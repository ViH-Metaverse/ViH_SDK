package com.vihmessenger.vihchatbot.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.services.ShortcutPinnedReceiver
import com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pins a home-screen shortcut that opens the SDK directly (skips the host app), so a user can
 * jump straight into their messages. Shared by the auto-prompt in `DashboardFragment` and the
 * "Add to Home Screen" action in `SettingFragment`.
 *
 * Note: Android lets an app *request* a pinned shortcut but not remove one it created, so this is
 * an add-only action (a launcher/OS confirmation gates the actual pin).
 */
object HomeScreenShortcut {

    const val SHORTCUT_ID = "vih_chatbot_shortcut"

    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Request a pinned shortcut labeled [label] with [icon] that launches [DashBoardActivity] for
     * [phone] / [hashcode]. Returns true if the request was accepted by the launcher.
     */
    fun pin(
        context: Context,
        phone: String,
        hashcode: String,
        label: String,
        icon: IconCompat
    ): Boolean {
        if (!isSupported(context)) {
            Toast.makeText(context, "Unable to add a shortcut on this launcher.", Toast.LENGTH_LONG).show()
            return false
        }
        val launchIntent = Intent(context, DashBoardActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AppConstants.PHONENUMBER, phone)
            putExtra(AppConstants.HASHCODE_EXTRA, hashcode)
            putExtra("launched_from_shortcut", true)
        }
        val shortcutInfo = ShortcutInfoCompat.Builder(
            context, SHORTCUT_ID + "_" + System.currentTimeMillis()
        )
            .setShortLabel(label.ifBlank { "Chat" })
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        val callbackIntent = Intent(context, ShortcutPinnedReceiver::class.java).apply {
            action = AppConstants.ACTION_SHORTCUT_PINNED
        }
        val successCallback = PendingIntent.getBroadcast(
            context, 0, callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, successCallback.intentSender)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not add shortcut.", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Load [logoUrl] (as a circular icon) then [pin]; falls back to a default icon if the logo is
     * missing/unloadable. Call from a coroutine — it does IO for the logo and switches to Main to
     * pin. [phone] / [hashcode] typically come from `Prefs`.
     */
    suspend fun pinWithLogo(
        context: Context,
        phone: String,
        hashcode: String,
        label: String,
        logoUrl: String?
    ) {
        val bitmap = if (!logoUrl.isNullOrBlank()) {
            CustomImageLoader.getBitmapFromUrl(logoUrl, applyCircleCrop = true)
        } else null
        val icon = if (bitmap != null) {
            IconCompat.createWithBitmap(bitmap)
        } else {
            IconCompat.createWithResource(context, R.drawable.placeholder)
        }
        withContext(Dispatchers.Main) {
            pin(context, phone, hashcode, label, icon)
        }
    }
}
