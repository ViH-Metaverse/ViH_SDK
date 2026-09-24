package com.vihmessenger.vihchatbot.utils

import android.content.Context
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.firebase.FirebaseApp
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.constants.BaseAPIConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Proves to the backend that a sign-in request comes from a genuine, unmodified build of a
 * registered app on a genuine device (VIH-SA-2026-09 H-3, client checklist item 25).
 *
 * **What this closes.** `account/sdk-login/` issues a session for a `{mobile, channel_id}` pair.
 * The channel hashkey is compiled into every APK, so before attestation both inputs were
 * recoverable by decompiling any client app — and a session could then be minted for any
 * subscriber's number from `curl`. Attestation makes the hashkey insufficient on its own: the
 * request must also carry a Play Integrity verdict for a package registered against that channel.
 *
 * **Flow** (each token is good for exactly one login attempt):
 *  1. `POST account/sdk-login/attestation-challenge/ {channel_id}` → a 43-char nonce, TTL 120s.
 *  2. Request a Play Integrity token with that nonce as the **request hash**.
 *  3. Send `attestation_platform`/`attestation_token`/`attestation_nonce` on the sdk-login body.
 *
 * The nonce goes in two places and both are checked: the `attestation_nonce` field, and
 * `setRequestHash` inside the integrity request. The backend compares
 * `requestDetails.requestHash` against the challenge it issued, so a correct
 * `attestation_nonce` with a different request hash is rejected. That is also why this uses the
 * **Standard** Integrity API — the Classic API reports `nonce` rather than `requestHash`.
 *
 * **Best-effort by design, for now.** Every failure path returns null and the caller signs in
 * without attestation. That is deliberate while `SDK_LOGIN_REQUIRE_ATTESTATION` is off
 * server-side: a device with no Play Services, an outage, or a slow network must not lock a user
 * out of a backend that is not yet enforcing. Once enforcement is on, an unattested request is
 * refused by the server, which is the correct outcome — the client does not need to change.
 *
 * Nothing here logs the token or the nonce.
 */
object SdkAttestation {

    private const val TAG = "SdkAttestation"
    private const val JSON_TYPE = "application/json; charset=utf-8"

    /** Whole flow is bounded: sign-in must not hang behind a stalled integrity provider. */
    private const val OVERALL_TIMEOUT_MS = 12_000L

    /** Result of a successful attestation, ready to attach to the sdk-login body. */
    data class Attestation(
        val platform: String,
        val token: String,
        val nonce: String,
    )

    /**
     * Acquires an attestation for [channelId], or null if one cannot be produced.
     *
     * Safe to call from any dispatcher; the blocking work is moved to IO internally.
     */
    suspend fun acquire(context: Context, channelId: String): Attestation? =
        withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val nonce = fetchChallenge(channelId) ?: return@runCatching null
                    val token = requestIntegrityToken(context, nonce) ?: return@runCatching null
                    Attestation(platform = "android", token = token, nonce = nonce)
                }.getOrElse {
                    VihLog.w(TAG, "Attestation unavailable: ${it.javaClass.simpleName}")
                    null
                }
            }
        }

    /**
     * Step 1 — single-use, channel-bound nonce.
     *
     * Deliberately uses a bare request through [ApiClient.okHttpClient]'s pinning without the
     * auth interceptor's concerns: this call is pre-auth by definition, since its whole purpose
     * is to let the following call authenticate.
     */
    private fun fetchChallenge(channelId: String): String? {
        val body = JSONObject().put("channel_id", channelId).toString()
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + BaseAPIConstants.SDK_LOGIN_ATTESTATION_CHALLENGE)
            .post(body.toRequestBody(JSON_TYPE.toMediaType()))
            .build()

        ApiClient.okHttpClient.newCall(request).execute().use { res ->
            if (!res.isSuccessful) {
                // 404 means this backend predates attestation; 400 means the channel is unknown.
                VihLog.w(TAG, "Challenge request failed: HTTP ${res.code}")
                return null
            }
            val raw = res.body?.string().orEmpty()
            val nonce = JSONObject(raw).optJSONObject("data")?.optString("nonce").orEmpty()
            return nonce.ifBlank {
                VihLog.w(TAG, "Challenge response carried no nonce")
                null
            }
        }
    }

    /**
     * Step 2 — Play Integrity token bound to [nonce] via the request hash.
     *
     * The cloud project number comes from the host's own Firebase configuration
     * (`gcmSenderId` is the project number), so an integrator does not have to supply it
     * separately — if Firebase is wired up for push, this is already correct.
     */
    private suspend fun requestIntegrityToken(context: Context, nonce: String): String? {
        val projectNumber = cloudProjectNumber(context) ?: run {
            VihLog.w(TAG, "No cloud project number available — skipping attestation")
            return null
        }

        val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
        val provider = manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(projectNumber)
                .build()
        ).await() ?: return null

        return provider.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(nonce)
                .build()
        ).await()?.token()
    }

    private fun cloudProjectNumber(context: Context): Long? = runCatching {
        val app = FirebaseApp.getInstance()
        app.options.gcmSenderId?.toLongOrNull()
    }.getOrNull()

    /**
     * Bridges Play's `Task` to a coroutine. Resumes with null on failure rather than throwing —
     * every caller here treats "no attestation" as a normal outcome, not an error.
     */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener {
                VihLog.w(TAG, "Integrity task failed: ${it.javaClass.simpleName}")
                if (cont.isActive) cont.resume(null)
            }
        }
}
