package com.vihmessenger.vihchatbot.api.services

import com.vihmessenger.vihchatbot.AppController
import okhttp3.Interceptor
import okhttp3.Response

/**
 * SECURITY: Centralized auth interceptor that injects the Bearer token into all API requests.
 * This replaces the previous pattern of using default parameter values in ApiService,
 * which was vulnerable to race conditions and "Bearer null" being sent.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val prefs = AppController.prefs

        // SECURITY (F-13): every authenticated request must carry the device id the session
        // was bound to.
        //
        // sdk-login sends `device_id`, which opts the issued token into device binding —
        // DeviceBoundJWTAuthentication then refuses that token on any request that does not
        // present the same id. Sending the id at login but not afterwards therefore produced a
        // session that authenticated once and 401'd on everything after it, including its own
        // renewal, which surfaced as the app logging itself out moments after signing in.
        //
        // Sent on every request, not just authenticated ones: it is a device identifier rather
        // than a credential, and the renewal endpoints need it too.
        val builder = originalRequest.newBuilder()
        if (originalRequest.header(DEVICE_ID_HEADER) == null) {
            prefs?.deviceId?.takeIf { it.isNotBlank() }?.let {
                builder.header(DEVICE_ID_HEADER, it)
            }
        }

        // Skip the auth header if one is already present (e.g. for login/signup endpoints)
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(builder.build())
        }

        val token = prefs?.accessToken
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }

    private companion object {
        const val DEVICE_ID_HEADER = "X-Device-Id"
    }
}
