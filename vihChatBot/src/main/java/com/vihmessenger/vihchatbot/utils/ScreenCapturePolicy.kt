package com.vihmessenger.vihchatbot.utils

import android.app.Activity
import android.view.WindowManager
import com.vihmessenger.vihchatbot.config.VihConfigStore

/**
 * Applies the SDK's screen-capture policy to a window (VAPT F-09, MASVS-RESILIENCE-3).
 *
 * `FLAG_SECURE` blocks screenshots, screen recording and mirroring to non-secure displays,
 * and keeps the window out of the recents-screen thumbnail the system caches on task switch.
 *
 * This SDK renders OTPs and transactional messages, and screen-capture malware plus
 * accessibility-abuse trojans are the dominant OTP-theft technique on Android — so the
 * policy defaults to on and the host app must opt out deliberately via
 * [com.vihmessenger.vihchatbot.config.VihSecurity.blockScreenCapture].
 *
 * Call from `onCreate` **before** the window is first drawn; the flag is not applied
 * retroactively to frames already rendered.
 */
object ScreenCapturePolicy {

    private const val TAG = "ScreenCapturePolicy"

    fun apply(activity: Activity) {
        val blockCapture = VihConfigStore.config?.security?.blockScreenCapture ?: true
        if (blockCapture) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            VihLog.w(TAG, "Screen-capture protection disabled by host VihConfig.")
        }
    }
}
