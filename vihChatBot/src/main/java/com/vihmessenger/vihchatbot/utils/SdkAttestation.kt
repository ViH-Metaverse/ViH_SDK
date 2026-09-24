package com.vihmessenger.vihchatbot.utils

import android.content.Context
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.firebase.FirebaseApp
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.constants.BaseAPIConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

    /**
     * Budget for the per-login work only (challenge fetch + token request). Deliberately
     * short: sign-in must not hang behind a stalled integrity provider.
     *
     * This does NOT cover [prepareIntegrityToken]. That call warms a provider by talking to
     * Google and is slow — seconds, sometimes far more on a cold start — which is why Google
     * documents it as something to do ahead of time rather than inline. Counting it against
     * a per-login budget is what made every attestation on a real device fall out silently:
     * the timeout fired, acquire() returned null, and the login went out unattested and
     * indistinguishable from a client that never implemented attestation.
     */
    private const val OVERALL_TIMEOUT_MS = 12_000L

    /** Budget for warming the provider, which happens once and off the login path. */
    private const val PREPARE_TIMEOUT_MS = 60_000L

    /**
     * Cached token provider. [prepareIntegrityToken] is expensive and its result is reusable
     * across requests, so it is warmed once ([warmUp]) and reused for every login.
     */
    @Volatile
    private var tokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    private val prepareLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Last reason attestation could not be produced, for diagnostics. Release builds strip
     * [VihLog], so without this a silent skip is invisible on a shipped build — which cost a
     * full test cycle. Read it from a host app or the debug harness.
     */
    @Volatile
    var lastSkipReason: String? = null
        private set

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
                    lastSkipReason = null
                    Attestation(platform = "android", token = token, nonce = nonce)
                }.getOrElse {
                    skip("threw:${it.javaClass.simpleName}")
                    null
                }
            }
        } ?: skip("timeout_${OVERALL_TIMEOUT_MS}ms").let { null }

    /**
     * Warms the integrity token provider. Call once, early, off the login path — from
     * `AppController` init or when a host opens its Discover screen. Safe to call repeatedly;
     * the provider is cached and only prepared once.
     */
    @JvmStatic
    suspend fun warmUp(context: Context) {
        if (tokenProvider != null) return
        prepareLock.withLock {
            if (tokenProvider != null) return
            val projectNumber = cloudProjectNumber(context) ?: run {
                skip("no_cloud_project_number"); return
            }
            val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
            tokenProvider = withTimeoutOrNull(PREPARE_TIMEOUT_MS) {
                manager.prepareIntegrityToken(
                    PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(projectNumber)
                        .build()
                ).await()
            } ?: run { skip("prepare_failed_or_timed_out"); null }
        }
    }

    private fun skip(reason: String) {
        lastSkipReason = reason
        VihLog.w(TAG, "Attestation unavailable: $reason")
    }

    /**
     * A client with the SDK's certificate pinning but **no** [AuthInterceptor] and **no**
     * [com.vihmessenger.vihchatbot.api.services.VihTokenAuthenticator].
     *
     * This matters more than it looks. The challenge endpoint is pre-auth, but routing it
     * through the shared client attached the stored bearer token, and a stale token made the
     * endpoint answer 401. That 401 then reached the authenticator, which renewed the session
     * — rotating the refresh token — on *every* challenge fetch, i.e. on every dashboard load.
     * Since the backend blacklists a spent refresh token and treats a replay as a leak by
     * revoking the whole family, the visible symptom was users being logged out at random.
     */
    private val bareClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .certificatePinner(ApiClient.certificatePinner)
            .build()
    }

    /**
     * Step 1 — single-use, channel-bound nonce. Sent unauthenticated, over [bareClient].
     */
    private fun fetchChallenge(channelId: String): String? {
        val body = JSONObject().put("channel_id", channelId).toString()
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + BaseAPIConstants.SDK_LOGIN_ATTESTATION_CHALLENGE)
            .post(body.toRequestBody(JSON_TYPE.toMediaType()))
            // bareClient skips AuthInterceptor; the challenge is pre-auth but the device id is
            // an identifier, not a credential, so it is safe and consistent to include.
            .apply { AppController.prefs?.deviceId?.let { header("X-Device-Id", it) } }
            .build()

        bareClient.newCall(request).execute().use { res ->
            if (!res.isSuccessful) {
                // 404 means this backend predates attestation; 400 means the channel is unknown.
                skip("challenge_http_${res.code}")
                return null
            }
            val raw = res.body?.string().orEmpty()
            val nonce = JSONObject(raw).optJSONObject("data")?.optString("nonce").orEmpty()
            return nonce.ifBlank {
                skip("challenge_no_nonce")
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
        // Warm on demand if the host never called warmUp. First login then pays the cost and
        // may still miss its budget, but every login after it is fast.
        if (tokenProvider == null) warmUp(context)
        val provider = tokenProvider ?: return null

        val token = provider.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(nonce)
                .build()
        ).await()?.token()

        if (token.isNullOrBlank()) {
            skip("integrity_request_returned_no_token")
            // A provider can go stale; drop it so the next attempt re-warms.
            tokenProvider = null
            return null
        }
        return token
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
                skip("integrity_task:${it.javaClass.simpleName}")
                if (cont.isActive) cont.resume(null)
            }
        }
}
