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
 * The backend issues a 1-hour access token and a 7-day refresh token. Renewal goes through
 * `account/token/refresh/`, which exchanges the stored refresh token for a fresh access token.
 *
 * **Why this changed.** There used to be no refresh-exchange endpoint, so the only way to renew
 * was to re-run the passwordless sign-in (`account/signup-login/`) with the phone + channel held
 * in [com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs]. That made every shipped client
 * structurally dependent on `signup-login` issuing a session from `{mobile, channel_id}` alone —
 * inputs that are both recoverable from any APK — so the endpoint could not be restricted without
 * logging out every client in the field. Moving renewal onto the refresh token removes that
 * dependency. See `docs/backend-security-work.md`.
 *
 * [signInAgain] survives only as a transitional fallback for sessions stored before this change,
 * which have no usable refresh token. **Delete it once the backend enforces a real credential on
 * `signup-login`** — at that point the fallback can only fail, and failing is correct: the user
 * re-authenticates.
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
        // Never try to re-authenticate the renewal calls themselves — that would recurse.
        val path = response.request.url.encodedPath
        if (path.endsWith(BaseAPIConstants.USER_SIGNUP_LOGIN) ||
            path.endsWith(BaseAPIConstants.TOKEN_REFRESH) ||
            // Pre-auth by definition. It should never carry a token, and a 401 from it must
            // never drive a renewal: doing so rotated the refresh token on every dashboard
            // load, and a replayed spent token makes the backend revoke the whole family —
            // which surfaced as users being logged out at random.
            path.endsWith(BaseAPIConstants.SDK_LOGIN_ATTESTATION_CHALLENGE)
        ) {
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
        val hasFallbackCredentials = !phone.isNullOrBlank() && !hashcode.isNullOrBlank()
        if (prefs.refreshToken.isNullOrBlank() && !hasFallbackCredentials) {
            // Nothing to renew with — a genuinely unauthenticated caller, not an expired one.
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

            // Preferred path: exchange the refresh token. Does not touch signup-login.
            val refreshed = prefs.refreshToken?.takeIf { it.isNotBlank() }?.let { exchangeRefresh(it) }
            if (refreshed != null) {
                prefs.accessToken = refreshed.accessToken
                // Store the rotated refresh token when the backend sends one (F-12). Dropping
                // it would mean replaying a blacklisted token on the next 401, which the
                // backend reads as a leak and answers by revoking every token for the account.
                if (refreshed.refreshToken.isNotBlank()) {
                    prefs.refreshToken = refreshed.refreshToken
                }
                VihLog.d(TAG, "Session renewed after 401 via refresh exchange")
                return response.request.retryWith(refreshed.accessToken)
            }

            // Transitional fallback — see the class KDoc. Reached when the stored session
            // predates the refresh-exchange change, or the refresh token has itself expired.
            if (!hasFallbackCredentials) return null
            val session = signInAgain(phone!!, hashcode!!, prefs.fcmToken.orEmpty()) ?: return null
            prefs.accessToken = session.accessToken
            prefs.refreshToken = session.refreshToken
            VihLog.d(TAG, "Session renewed after 401 via sign-in fallback")
            return response.request.retryWith(session.accessToken)
        }
    }

    private fun Request.retryWith(token: String): Request =
        newBuilder().header(AUTH_HEADER, BEARER_PREFIX + token).build()

    /**
     * Exchanges the stored refresh token for a fresh access token via
     * [BaseAPIConstants.TOKEN_REFRESH].
     *
     * Returns null for any non-2xx — an expired or rejected refresh token is an ordinary
     * outcome here, not an error worth surfacing, and the caller falls through to its
     * fallback. Reads `access`, the field the endpoint actually returns, and tolerates
     * `access_token` in case the contract is aligned with the sign-in response later.
     */
    private fun exchangeRefresh(refreshToken: String): Session? = try {
        val payload = JSONObject().put("refresh", refreshToken).toString()
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + BaseAPIConstants.TOKEN_REFRESH)
            .post(payload.toRequestBody(JSON))
            // bareClient skips AuthInterceptor, so the device id has to be set here (F-13).
            .apply { AppController.prefs?.deviceId?.let { header("X-Device-Id", it) } }
            .build()

        bareClient.newCall(request).execute().use { res ->
            val raw = res.body?.string()
            if (!res.isSuccessful || raw.isNullOrBlank()) {
                VihLog.w(TAG, "Refresh exchange failed: HTTP ${res.code}")
                null
            } else {
                val json = JSONObject(raw)
                val access = json.optString("access").ifBlank { json.optString("access_token") }
                if (access.isBlank()) {
                    VihLog.w(TAG, "Refresh exchange carried no access token")
                    null
                } else {
                    // The backend rotates refresh tokens (F-12) and blacklists the spent one,
                    // so a rotated token MUST be stored. Re-presenting the old one is treated
                    // as a leak and revokes the whole family — which would log the user out
                    // completely on the next renewal. Empty when rotation is off; the caller
                    // then keeps the token it already had.
                    Session(access, json.optString("refresh").orEmpty())
                }
            }
        }
    } catch (t: Throwable) {
        VihLog.e(TAG, "Refresh exchange threw: ${t.javaClass.simpleName} - ${t.message}")
        null
    }

    /**
     * Blocking re-run of the passwordless sign-in. Runs on OkHttp's own thread, which is
     * where [authenticate] is already invoked, so blocking here is expected and safe.
     *
     * Uses [bareClient] rather than the shared client so the request cannot re-enter this
     * authenticator or pick up a stale bearer token from [AuthInterceptor].
     */
    private fun signInAgain(phone: String, hashcode: String, fcmToken: String): Session? = try {
        // device_id keeps the fallback's token bound the same way the primary path's is
        // (F-13). Without it a session renewed through this path would silently lose its
        // device binding, which is the opposite of what the control is for.
        val payload = JSONObject()
            .put("mobile", phone)
            .put("channel_id", hashcode)
            .put("fcm_token", fcmToken)
            .apply { AppController.prefs?.deviceId?.let { put("device_id", it) } }
            .toString()

        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + BaseAPIConstants.USER_SIGNUP_LOGIN)
            .post(payload.toRequestBody(JSON))
            // bareClient skips AuthInterceptor, so the device id has to be set here (F-13).
            .apply { AppController.prefs?.deviceId?.let { header("X-Device-Id", it) } }
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
