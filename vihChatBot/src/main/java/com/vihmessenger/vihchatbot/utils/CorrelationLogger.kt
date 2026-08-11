package com.vihmessenger.vihchatbot.utils

import android.util.Log
import com.bugfender.sdk.Bugfender
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.config.VihConfigStore

/**
 * Structured logger that prefixes every entry with correlation identifiers required
 * by architecture §6.2 (shoot_id, message_id, trace_id). When the AWS-native data path
 * lands, the same three IDs will flow through MQTT, KMS, and the WebSocket API — keeping
 * one logging surface means a single OpenSearch query can recover the full timeline.
 *
 * Routes to Logcat (debug builds only, per existing security policy) and to Bugfender
 * only when the host app has opted in via `VihConfig.diagnostics.remoteLoggingEnabled`.
 *
 * SECURITY (VAPT F-14): the Bugfender sink used to be unconditional. The boolean passed to
 * `Bugfender.init` controls logcat *mirroring*, not upload — explicit `Bugfender.d/i/w/e`
 * calls upload from release builds too, so every line this logger emitted was leaving
 * partner devices for a third-party processor by default. It is now opt-in and off by
 * default. Never pass a secret to this logger regardless: use `VihLog.redact`/`tail`.
 *
 * Null IDs are rendered as "-" so log lines remain greppable while the server rollout
 * catches up. Adding the fields here is forward-compatible: the call sites are stable
 * even before the backend starts populating them.
 */
object CorrelationLogger {

    private const val DEFAULT_TAG = "VihSDK"

    fun debug(
        tag: String = DEFAULT_TAG,
        message: String,
        shootId: String? = null,
        messageId: String? = null,
        traceId: String? = null
    ) {
        val line = format(message, shootId, messageId, traceId)
        if (BuildConfig.DEBUG) {
            Log.d(tag, line)
        }
        if (remoteLoggingEnabled()) Bugfender.d(tag, line)
    }

    fun info(
        tag: String = DEFAULT_TAG,
        message: String,
        shootId: String? = null,
        messageId: String? = null,
        traceId: String? = null
    ) {
        val line = format(message, shootId, messageId, traceId)
        if (BuildConfig.DEBUG) {
            Log.i(tag, line)
        }
        if (remoteLoggingEnabled()) Bugfender.i(tag, line)
    }

    fun warn(
        tag: String = DEFAULT_TAG,
        message: String,
        shootId: String? = null,
        messageId: String? = null,
        traceId: String? = null,
        throwable: Throwable? = null
    ) {
        val line = format(message, shootId, messageId, traceId)
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.w(tag, line, throwable) else Log.w(tag, line)
        }
        if (remoteLoggingEnabled()) {
            Bugfender.w(tag, if (throwable != null) "$line | ${throwable.javaClass.simpleName}: ${throwable.message}" else line)
        }
    }

    fun error(
        tag: String = DEFAULT_TAG,
        message: String,
        shootId: String? = null,
        messageId: String? = null,
        traceId: String? = null,
        throwable: Throwable? = null
    ) {
        val line = format(message, shootId, messageId, traceId)
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, line, throwable) else Log.e(tag, line)
        }
        if (remoteLoggingEnabled()) {
            Bugfender.e(tag, if (throwable != null) "$line | ${throwable.javaClass.simpleName}: ${throwable.message}" else line)
        }
    }

    private fun format(
        message: String,
        shootId: String?,
        messageId: String?,
        traceId: String?
    ): String {
        val s = shootId ?: "-"
        val m = messageId ?: "-"
        val t = traceId ?: "-"
        return "[shoot=$s msg=$m trace=$t] $message"
    }

    /**
     * True only when the host app explicitly enabled remote logging. Defaults to false when
     * no [com.vihmessenger.vihchatbot.config.VihConfig] has been supplied.
     */
    private fun remoteLoggingEnabled(): Boolean =
        VihConfigStore.config?.diagnostics?.remoteLoggingEnabled == true

}
