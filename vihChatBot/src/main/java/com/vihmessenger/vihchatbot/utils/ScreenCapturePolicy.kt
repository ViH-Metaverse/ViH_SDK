package com.vihmessenger.vihchatbot.utils

import android.app.Activity
import android.os.Build
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
        hideOverlayWindows(activity)
    }

    /**
     * Hides overlay windows drawn by *other* apps while an SDK window is in the foreground
     * (HISPL 12.6, CWE-1021).
     *
     * `filterTouchesWhenObscured` on the sensitive layouts discards taps that arrive through
     * an overlay; this goes further and stops the overlay being drawn over us at all, which
     * also defeats the purely visual half of an overlay-phishing attack — a replica login
     * prompt rendered on top of the real chat surface.
     *
     * API 31+ only, and it affects only `TYPE_APPLICATION_OVERLAY` windows from other apps.
     * System windows and the host app's own in-app views are unaffected, so a host that
     * draws its own UI inside the SDK activity keeps working.
     */
    private fun hideOverlayWindows(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching { activity.window.setHideOverlayWindows(true) }
            .onFailure { VihLog.w(TAG, "setHideOverlayWindows failed: ${it.javaClass.simpleName}") }
    }
}
