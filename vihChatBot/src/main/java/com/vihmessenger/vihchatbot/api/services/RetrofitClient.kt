package com.vihmessenger.vihchatbot.api.services

import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.data.services.ApiService
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A modern, singleton Retrofit API client.
 *
 * SECURITY improvements:
 * - Certificate pinning for API domains
 * - Auth interceptor for centralized token management
 * - HTTP logging only in debug builds
 */
object ApiClient {

    // SECURITY: Certificate pinning to prevent MITM attacks
    // NOTE: Replace the placeholder pin hashes with your actual server certificate SHA-256 pins.
    // You can obtain them by running: openssl s_client -connect vihapi.plugseal.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
    private val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // TODO: Replace these placeholder pins with actual certificate SHA-256 pins for your domains
            // .add("vihapi.plugseal.com", "sha256/yVsNAcVu753A+RkSN87Tw1NVDV7moS77Wp0KIMnBUBk=")
            // .add("stagingnlp.vihmessenger.com", "sha256/vbjMRUxLfavtd+3+GPgx+mtIcTtK1wgv4HS3yHrsCac=")
            .build()
    }

    // Private OkHttpClient with security configurations
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // SECURITY: Centralized auth token injection
            .addInterceptor(AuthInterceptor())
            // SECURITY: Certificate pinning (uncomment when pins are configured)
            // .certificatePinner(certificatePinner)
            .also { client ->
                // SECURITY: Enable logging only in DEBUG mode to prevent leaking sensitive data
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor()
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY)
                    client.addInterceptor(logging)
                }
            }
            .build()
    }

    // Private Retrofit instance
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Public service instance that the rest of the app will use
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
