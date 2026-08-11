package com.vihmessenger.vihchatbot.utils

import android.util.Log
import com.vihmessenger.vihchatbot.BuildConfig

/**
 * Debug-gated replacement for [android.util.Log] (VAPT F-06, CWE-532).
 *
 * Logcat is a process-shared surface: it is readable over ADB, by any app holding
 * READ_LOGS (pre-installed OEM/carrier apps frequently do), by analytics SDKs the *host*
 * app installs, on rooted devices, and in bug-report captures. Because this SDK is
 * embedded in third-party apps and its payload is OTPs and transactional messages,
 * nothing may reach logcat in a release build.
 *
 * Every method here no-ops unless [BuildConfig.DEBUG]. In a published AAR that constant
 * is `false`, so release consumers get silence. R8 additionally strips the underlying
 * `android.util.Log` calls via the `-assumenosideeffects` rule in `consumer-rules.pro`,
 * which catches anything that bypasses this wrapper.
 *
 * Even in debug builds, never pass a secret as the message. Use [redact] for tokens and
 * codes, and [tail] for phone numbers, so a shared debug session or a screen recording of
 * a developer's logcat cannot leak a live credential.
 */
object VihLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun d(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.d(tag, message, throwable)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun i(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.i(tag, message, throwable)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.e(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }

    /**
     * Renders a secret as a non-reversible placeholder that still tells you whether the
     * value was present and roughly how long it was — enough to debug a "token is null"
     * or "token got truncated" bug without ever writing the value down.
     */
    fun redact(value: String?): String = when {
        value == null -> "<null>"
        value.isEmpty() -> "<empty>"
        else -> "<redacted:${value.length}>"
    }

    /**
     * Masks all but the last [keep] characters. For phone numbers and channel hashkeys,
     * where the trailing digits are enough to correlate log lines during debugging but
     * the whole value is PII (or, for a hashkey, a channel identifier).
     */
    fun tail(value: String?, keep: Int = 4): String = when {
        value == null -> "<null>"
        value.isEmpty() -> "<empty>"
        value.length <= keep -> "*".repeat(value.length)
        else -> "*".repeat(value.length - keep) + value.takeLast(keep)
    }
}
