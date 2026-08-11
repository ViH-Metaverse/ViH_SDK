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
 * The SDK's single HTTP stack.
 *
 * SECURITY (VAPT F-03 / F-10): there used to be two clients — this one and
 * `BaseCloudAPIService` — and the security controls were only ever wired into this one
 * while the SDK actually issued its traffic through the other. Both now share
 * [okHttpClient], so there is exactly one place to configure pinning, auth and logging,
 * and no way to add a control to "the client" and miss half the traffic.
 *
 * Controls applied here:
 *  - certificate pinning ([certificatePinner]) — enforced, not commented out
 *  - centralised bearer-token injection ([AuthInterceptor])
 *  - HTTP body logging only in debug builds
 */
object ApiClient {

    /**
     * SHA-256 SubjectPublicKeyInfo pins for the VIH API hosts.
     *
     * **These are CA pins, not leaf pins, and that is deliberate.** Both hosts sit behind
     * AWS Certificate Manager, which auto-renews and generates a *new key pair* at each
     * renewal — pinning the leaf would hard-fail every shipped client the moment ACM
     * rotated (the production leaf expires 2026-11-28, roughly three months out). Pinning
     * the issuing intermediate plus `Amazon Root CA 1` survives rotation while still
     * defeating the threat this control exists for: a user- or MDM-installed CA performing
     * interception. A Burp/mitmproxy CA does not chain to Amazon Root CA 1, so it fails.
     *
     * `Amazon Root CA 1` is the backup pin — valid until 2037, and it keeps clients working
     * if AWS moves a host to a different `Amazon RSA 2048 Mxx` intermediate. OkHttp accepts
     * the connection when *any* certificate in the chain matches *any* pin for the host.
     *
     * Regenerate with:
     * ```
     * openssl s_client -connect <host>:443 -servername <host> -showcerts </dev/null \
     *   | openssl x509 -pubkey -noout \
     *   | openssl pkey -pubin -outform der \
     *   | openssl dgst -sha256 -binary | openssl enc -base64
     * ```
     */
    internal val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // Production — Amazon RSA 2048 M04 (issuing intermediate)
            .add(
                "api.platform.vihresearchlabs.ai",
                "sha256/G9LNNAql897egYsabashkzUCTEJkWBzgoEtk8X/678c="
            )
            // Production — Amazon Root CA 1 (backup, survives intermediate rotation)
            .add(
                "api.platform.vihresearchlabs.ai",
                "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
            )
            // Staging — Amazon RSA 2048 M01 (issuing intermediate)
            .add(
                "api.dev.platform.vihresearchlabs.ai",
                "sha256/DxH4tt40L+eduF6szpY6TONlxhZhBd+pJ9wbHlQ2fuw="
            )
            // Staging — Amazon Root CA 1 (backup)
            .add(
                "api.dev.platform.vihresearchlabs.ai",
                "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
            )
            .build()
    }

    /**
     * The one OkHttp client the SDK uses. Internal so [com.vihmessenger.vihchatbot.data
     * .services.BaseCloudAPIService] can share it rather than building its own unprotected
     * one.
     */
    internal val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // SECURITY: centralised auth token injection
            .addInterceptor(AuthInterceptor())
            // Renew the session in-place when the backend returns 401. The access token
            // lives one hour and there is no refresh-exchange endpoint, so without this an
            // hour-old session fails every call. See [VihTokenAuthenticator].
            .authenticator(VihTokenAuthenticator())
            // SECURITY: certificate pinning — enforced for the hosts above; hosts without a
            // pin entry fall back to normal platform trust validation.
            .certificatePinner(certificatePinner)
            .also { client ->
                // SECURITY: body logging only in debug builds — it prints bearer tokens.
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor()
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY)
                    client.addInterceptor(logging)
                }
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
