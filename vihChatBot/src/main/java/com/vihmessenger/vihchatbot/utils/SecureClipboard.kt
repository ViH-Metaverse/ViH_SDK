package com.vihmessenger.vihchatbot.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Clipboard writes for one-time codes (VAPT F-08, CWE-200).
 *
 * A plain `setPrimaryClip(ClipData.newPlainText(...))` leaves an OTP on a process-shared
 * surface indefinitely: on Android 12 and below any foreground app can read the primary
 * clip without prompting, and on every version the clipboard preview renders the code in a
 * system toast/overlay that screen-recording malware captures. An OTP that outlives its use
 * on a shared surface is precisely the artefact credential-stealing malware scrapes.
 *
 * This helper does two things the bare call does not:
 *  - marks the clip sensitive so the platform (API 33+) and several OEM skins below that
 *    mask the preview rather than rendering the code in plaintext;
 *  - clears the clip after [RETENTION_MS], but only if it still holds the same value, so a
 *    user who copied something else in the meantime keeps their clipboard.
 */
object SecureClipboard {

    /** How long a one-time code may sit on the clipboard before it is cleared. */
    const val RETENTION_MS = 60_000L

    private const val TAG = "SecureClipboard"
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Copies [value] as a sensitive, self-expiring clip. Returns true when the clipboard
     * accepted it.
     */
    fun copySensitive(context: Context, label: String, value: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false

        val clip = ClipData.newPlainText(label, value).apply {
            // API 33+ honours this by masking the clipboard preview UI; older OEM skins that
            // backported the flag honour it too, and it is inert everywhere else.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            } else {
                description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }

        return runCatching {
            clipboard.setPrimaryClip(clip)
            scheduleClear(clipboard, value)
            true
        }.getOrElse {
            VihLog.e(TAG, "Failed to set primary clip: ${it.javaClass.simpleName}")
            false
        }
    }

    /**
     * Clears the clip after [RETENTION_MS] — but only when it still contains [value].
     * Without that check we would wipe whatever the user copied in the meantime.
     */
    private fun scheduleClear(clipboard: ClipboardManager, value: String) {
        handler.postDelayed({
            runCatching {
                val current = clipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
                if (current == value) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        // No clearPrimaryClip before API 28 — overwrite with an empty clip.
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    VihLog.d(TAG, "Expired one-time code from clipboard.")
                }
            }
        }, RETENTION_MS)
    }
}
