package com.vihmessenger.vihchatbot.data.services


import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface BaseCloudAPIService {

    companion object {
        operator fun invoke(): Retrofit {
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)

            // SECURITY: Only enable HTTP logging in debug builds
            if (BuildConfig.DEBUG) {
                clientBuilder.addInterceptor(
                    HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
                )
            }

            // SECURITY: Use BuildConfig.API_BASE_URL instead of hardcoded URL
            return Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(clientBuilder.build())
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
