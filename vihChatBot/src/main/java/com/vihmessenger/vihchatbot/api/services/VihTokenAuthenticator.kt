package com.vihmessenger.vihchatbot.api.services

import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.constants.BaseAPIConstants
import com.vihmessenger.vihchatbot.utils.VihLog
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Silently re-establishes the session when the backend rejects a request with 401.
 *
 * The backend issues a 1-hour access token and a 7-day refresh token, but exposes no
 * refresh-exchange endpoint — the refresh token is only ever accepted by logout. So the
 * only way to renew is to re-run the passwordless sign-in (`account/signup-login/`) with
 * the phone + channel already held in [com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs].
 * That is what this authenticator does.
 *
 * Before this existed, a session older than an hour surfaced as a hard 401 to every caller:
 * hosts driving the SDK through [com.vihmessenger.vihchatbot.discover.VihDiscover] got an
 * error out of `listEnterprises`, and the built-in Discover tab tripped
 * `BaseRepository.handleSessionExpired()`, which relaunched the *host* application. Renewing
 * here means neither path is reached for an ordinary expiry.
 *
 * OkHttp calls [authenticate] only on a 401 response, off the main thread, and retries the
 * returned request. Returning `null` gives up and lets the 401 propagate.
 */
class VihTokenAuthenticator : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never try to re-authenticate the sign-in call itself — that would recurse.
        if (response.request.url.encodedPath.endsWith(BaseAPIConstants.USER_SIGNUP_LOGIN)) {
            return null
        }

        // One retry only. Without this a persistently-401ing endpoint (e.g. a hashcode the
        // account genuinely cannot access) would loop until OkHttp's own limit.
        if (priorResponseCount(response) >= 1) {
            VihLog.w(TAG, "Giving up: already retried this request once after 401")
            return null
        }

        val prefs = AppController.prefs ?: return null
        val phone = prefs.phoneNumber
        val hashcode = prefs.hashcode
        if (phone.isNullOrBlank() || hashcode.isNullOrBlank()) {
            // No stored credentials to re-auth with — this is a genuinely unauthenticated
            // caller, not an expired one.
            return null
        }

        synchronized(lock) {
            val storedToken = prefs.accessToken
            val sentToken = response.request.header(AUTH_HEADER)?.removePrefix(BEARER_PREFIX)

            // Another thread refreshed while this request was in flight — reuse its token
            // instead of issuing a second sign-in.
            if (!storedToken.isNullOrBlank() && storedToken != sentToken) {
                return response.request.retryWith(storedToken)
            }

            val session = signInAgain(phone, hashcode, prefs.fcmToken.orEmpty()) ?: return null
            prefs.accessToken = session.accessToken
            prefs.refreshToken = session.refreshToken
            VihLog.d(TAG, "Session renewed after 401")
            return response.request.retryWith(session.accessToken)
        }
    }

    private fun Request.retryWith(token: String): Request =
        newBuilder().header(AUTH_HEADER, BEARER_PREFIX + token).build()

    /**
     * Blocking re-run of the passwordless sign-in. Runs on OkHttp's own thread, which is
     * where [authenticate] is already invoked, so blocking here is expected and safe.
     *
     * Uses [bareClient] rather than the shared client so the request cannot re-enter this
     * authenticator or pick up a stale bearer token from [AuthInterceptor].
     */
    private fun signInAgain(phone: String, hashcode: String, fcmToken: String): Session? = try {
        val payload = JSONObject()
            .put("mobile", phone)
            .put("channel_id", hashcode)
            .put("fcm_token", fcmToken)
            .toString()

        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + BaseAPIConstants.USER_SIGNUP_LOGIN)
            .post(payload.toRequestBody(JSON))
            .build()

        bareClient.newCall(request).execute().use { res ->
            val raw = res.body?.string()
            if (!res.isSuccessful || raw.isNullOrBlank()) {
                VihLog.w(TAG, "Re-auth failed: HTTP ${res.code}")
                null
            } else {
                val data = JSONObject(raw).optJSONObject("data")
                val access = data?.optString("access_token").orEmpty()
                val refresh = data?.optString("refresh").orEmpty()
                if (access.isBlank()) {
                    VihLog.w(TAG, "Re-auth response carried no access_token")
                    null
                } else {
                    Session(access, refresh)
                }
            }
        }
    } catch (t: Throwable) {
        // Offline, timeout, pinning failure — all mean "cannot renew now". Returning null
        // lets the original 401 surface to the caller rather than throwing from OkHttp's
        // authenticator, which would present as an opaque IOException.
        VihLog.e(TAG, "Re-auth threw: ${t.javaClass.simpleName} - ${t.message}")
        null
    }

    private fun priorResponseCount(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private data class Session(val accessToken: String, val refreshToken: String)

    companion object {
        private const val TAG = "VihTokenAuthenticator"
        private const val AUTH_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // Serialises refreshes across concurrent 401s so a burst of failed requests
        // produces one sign-in, not one per request.
        private val lock = Any()

        /**
         * A minimal client for the re-auth call: same timeouts and certificate pinning as the
         * main stack, but deliberately without [AuthInterceptor] or this authenticator.
         */
        private val bareClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .certificatePinner(ApiClient.certificatePinner)
                .build()
        }
    }
}
