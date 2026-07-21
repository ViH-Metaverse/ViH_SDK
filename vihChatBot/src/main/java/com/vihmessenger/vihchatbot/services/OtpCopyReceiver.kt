package com.vihmessenger.vihchatbot.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class OtpCopyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY_OTP = "com.vihmessenger.vihchatbot.services.COPY_OTP"
        const val EXTRA_OTP = "extra_otp"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        // Preliminary checks
        if (context == null) {
            Log.e("OtpCopyReceiver", "Context is null in onReceive.")
            return
        }
        if (intent == null) {
            Log.e("OtpCopyReceiver", "Intent is null in onReceive.")
            return
        }

        Log.d("OtpCopyReceiver", "onReceive triggered. Action: ${intent.action}")

        if (intent.action == ACTION_COPY_OTP) {
            val otpToCopy = intent.getStringExtra(EXTRA_OTP)
            val notificationIdToCancel = intent.getIntExtra("notification_id_for_otp_copy", -1)

            Log.d("OtpCopyReceiver", "Processing ACTION_COPY_OTP. OTP from intent: '$otpToCopy', Notification ID: $notificationIdToCancel")

            if (!otpToCopy.isNullOrBlank()) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP", otpToCopy) // Label "OTP", text is otpToCopy
                    clipboard.setPrimaryClip(clip)

                    // Verify if clipboard has the text (optional, for debugging)
                    if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.getItemAt(0)?.text == otpToCopy) {
                        Log.d("OtpCopyReceiver", "OTP '$otpToCopy' successfully set to primary clip.")
                        Toast.makeText(context, "OTP '$otpToCopy' copied!", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("OtpCopyReceiver", "Failed to verify OTP in clipboard after setPrimaryClip. OTP was: '$otpToCopy'")
                        Toast.makeText(context, "Failed to copy OTP.", Toast.LENGTH_SHORT).show()
                    }

                    if (notificationIdToCancel != -1) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationIdToCancel)
                        Log.d("OtpCopyReceiver", "Cancelled notification ID: $notificationIdToCancel after OTP copy attempt.")
                    }
                } catch (e: Exception) {
                    Log.e("OtpCopyReceiver", "Error during clipboard operation or notification cancellation", e)
                    Toast.makeText(context, "Error copying OTP.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w("OtpCopyReceiver", "OTP to copy was null or blank in intent. Cannot copy.")
                Toast.makeText(context, "No OTP found to copy.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("OtpCopyReceiver", "Received intent with incorrect action: ${intent.action}")
        }
    }
}