package com.vihmessenger.vihchatbot.services // Or your preferred package for receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vihmessenger.vihchatbot.utils.VihLog
import android.widget.Toast
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs

class ShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VihLog.d(
            "ShortcutPinnedReceiver",
            "onReceive CALLED. Action: ${intent.action}, Intent: $intent"
        )

        // Even with an explicit intent, checking the action is a good safeguard
        if (intent.action == AppConstants.ACTION_SHORTCUT_PINNED) {
            VihLog.i("ShortcutPinnedReceiver", "ACTION_SHORTCUT_PINNED broadcast received!")
            Toast.makeText(context.applicationContext, "Shortcut Pinned!", Toast.LENGTH_LONG).show()

            val prefs = Prefs.getInstance(context.applicationContext)
            val oldValue = prefs.shortcutAddedSuccessfully
            prefs.shortcutAddedSuccessfully = true
            // The following log shows the in-memory value immediately after setting.
            // Actual disk write by apply() is asynchronous.
            VihLog.i(
                "ShortcutPinnedReceiver",
                "prefs.shortcutAddedSuccessfully was: $oldValue, set to: ${prefs.shortcutAddedSuccessfully}"
            )

            prefs.shortcutPromptCount = 0
            prefs.shortcutDeniedCount = 0
            prefs.shortcutDeniedByUser = false
            VihLog.d("ShortcutPinnedReceiver", "Shortcut related counters reset.")

        } else {
            VihLog.w(
                "ShortcutPinnedReceiver",
                "Received unexpected action or intent: ${intent.action}"
            )
        }
    }
}