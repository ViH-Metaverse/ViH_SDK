package com.vihmessenger.vihchatbot.services

import android.content.Context
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.data.model.DeviceTokenRequest
import com.vihmessenger.vihchatbot.utils.CorrelationLogger
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Push the (deviceId, fcmToken) pair to the session registry described in
 * architecture §3.3. Replaces the pre-existing TODO stub in
 * [MyFirebaseMessagingService.sendRegistrationTokenToServer].
 *
 * Two entry points cover the rotation paths:
 *   - [onNewToken] fires whenever Firebase rotates the token. Auth may not be ready yet,
 *     so the call is best-effort and the cached token + `fcmTokenRegistered=false` flag
 *     ensure a retry happens after login.
 *   - [registerCachedTokenIfNeeded] is called after sign-in to flush any token that
 *     arrived before auth was available.
 *
 * Failures are not retried in-process — the persistent `fcmTokenRegistered` flag lets
 * future app launches or login events drive the retry. This keeps the FCM service light
 * and avoids exhausting backoff budgets inside a short-lived Firebase callback.
 */
object DeviceTokenRegistrar {

    private const val TAG = "DeviceTokenRegistrar"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onNewToken(context: Context, token: String) {
        val prefs = Prefs.getInstance(context)
        prefs.fcmToken = token
        prefs.fcmTokenRegistered = false
        attemptRegistration(prefs)
    }

    fun registerCachedTokenIfNeeded(context: Context) {
        val prefs = Prefs.getInstance(context)
        if (prefs.fcmToken.isNullOrBlank()) return
        // Re-register when the token hasn't been ack'd yet, OR when the channel hashkey
        // changed since the last successful registration. The push registry is keyed per
        // (channel, device), so after a channel switch (login with a different hashkey, or
        // the Settings "Change Channel Hashkey" option) the device must register again or
        // shoots on the new channel are rejected as "not registered on the channel".
        val registeredForCurrentChannel =
            prefs.fcmTokenRegistered && prefs.hashcode == prefs.fcmRegisteredHashcode
        if (registeredForCurrentChannel) return
        attemptRegistration(prefs)
    }

    private fun attemptRegistration(prefs: Prefs) {
        val token = prefs.fcmToken ?: return
        val deviceId = prefs.deviceId
        val hashcode = prefs.hashcode

        scope.launch {
            try {
                val response = ApiClient.apiService.registerFcmToken(
                    DeviceTokenRequest(
                        deviceId = deviceId,
                        fcmToken = token,
                        hashcode = hashcode,
                        sdkVersion = BuildConfig.SDK_VERSION
                    )
                )
                if (response.isSuccessful) {
                    prefs.fcmTokenRegistered = true
                    // Remember which channel this registration was for, so a later channel
                    // switch triggers a fresh registration on the new channel.
                    prefs.fcmRegisteredHashcode = hashcode
                    CorrelationLogger.info(
                        tag = TAG,
                        message = "FCM token registered with session registry"
                    )
                } else {
                    CorrelationLogger.warn(
                        tag = TAG,
                        message = "FCM token registration rejected: HTTP ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                // Network-level failure. Leave fcmTokenRegistered=false so the next
                // login attempt or token rotation retries the call.
                CorrelationLogger.warn(
                    tag = TAG,
                    message = "FCM token registration failed",
                    throwable = e
                )
            }
        }
    }
}
