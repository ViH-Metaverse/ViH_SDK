package com.vihmessenger.vihchatbot.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * The single place the SDK turns a server-supplied string into a launched intent
 * (VAPT F-16, CWE-939).
 *
 * Every URL the SDK opens — chat message links, template buttons, product buy/cart links,
 * enterprise website — originates from the server or from message content, i.e. from
 * outside the trust boundary. Handing such a string straight to
 * `Intent(ACTION_VIEW, Uri.parse(it))` is an arbitrary-intent launch primitive: `intent:`
 * URIs can target unexported components in the host app, `file:`/`content:` can coax a
 * viewer into reading private files, and custom schemes can hand data to whatever app
 * claims them.
 *
 * The old call sites each rolled their own prefix check. They were *mostly* equivalent, but
 * not quite: `CompanyProfileActivity` and one branch of `ChatActivity` **downgraded** a
 * scheme-less URL to `http://`, silently sending the user over cleartext. Centralising also
 * means the next control added here applies everywhere instead of to three of five sites.
 *
 * Policy: only `http`/`https` survive, `http` is upgraded to `https`, and anything else is
 * refused. A scheme-less string is treated as a bare host and gets `https://`.
 */
object ExternalUrl {

    private const val TAG = "ExternalUrl"

    /**
     * Normalises [rawUrl] to an `https` URI, or returns null when it is not a web URL.
     *
     * Note this deliberately parses before deciding — a `startsWith("http")` test passes
     * for `httpsomething:` and other strings that are not what they look like.
     */
    fun sanitize(rawUrl: String?): Uri? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        // A bare host ("example.com/path") has no scheme — default it to https rather than
        // http, which is what several call sites used to do.
        val candidate = if (!trimmed.contains("://")) "https://$trimmed" else trimmed

        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            VihLog.w(TAG, "Refusing non-web scheme: $scheme")
            return null
        }
        if (uri.host.isNullOrBlank()) {
            VihLog.w(TAG, "Refusing URL with no host.")
            return null
        }

        // Upgrade cleartext. Never downgrade.
        return if (scheme == "http") uri.buildUpon().scheme("https").build() else uri
    }

    /**
     * Opens [rawUrl] in a browser after [sanitize]. Shows [invalidMessage] and returns false
     * when the URL is refused or no handler exists.
     *
     * [useChooser] presents the system chooser rather than going straight to the default
     * handler — matching the behaviour of the sites that used `Intent.createChooser`.
     */
    fun open(
        context: Context,
        rawUrl: String?,
        useChooser: Boolean = false,
        chooserTitle: String = "Open with",
        invalidMessage: String = "Invalid link"
    ): Boolean {
        val uri = sanitize(rawUrl)
        if (uri == null) {
            Toast.makeText(context, invalidMessage, Toast.LENGTH_SHORT).show()
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // CATEGORY_BROWSABLE keeps the resolution to apps that advertise themselves as
            // web handlers, which narrows it further than ACTION_VIEW alone.
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(
                if (useChooser) Intent.createChooser(intent, chooserTitle) else intent
            )
            true
        } catch (e: ActivityNotFoundException) {
            VihLog.w(TAG, "No activity can handle the web intent.", e)
            Toast.makeText(context, "Cannot open link.", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
