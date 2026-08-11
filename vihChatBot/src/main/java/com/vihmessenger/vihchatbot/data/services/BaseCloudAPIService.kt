package com.vihmessenger.vihchatbot.data.services


import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.api.services.ApiClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit instance used by the repositories.
 *
 * SECURITY (VAPT F-03 / F-10): this used to build its own bare `OkHttpClient` with neither
 * certificate pinning nor the auth interceptor — and because this is the client the SDK
 * actually routes its traffic through, the controls configured on [ApiClient] protected
 * almost nothing. It now reuses [ApiClient.okHttpClient], so pinning, auth injection and
 * debug-only logging apply to every request the SDK makes, and connection pooling and
 * thread pools are shared rather than duplicated.
 */
interface BaseCloudAPIService {

    companion object {
        operator fun invoke(): Retrofit {
            return Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(ApiClient.okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        fun <T> getApiService(service: Class<T>): T {
            if (AppController.cloudApiService != null) {
                return AppController.cloudApiService!!.create(service)
            } else {
                throw Throwable("CloudApiService cannot be null")
            }
        }
    }
}
