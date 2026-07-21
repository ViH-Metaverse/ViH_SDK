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

        // Skip auth header if one is already present (e.g., for login/signup endpoints)
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val token = AppController.prefs?.accessToken
        if (token.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
