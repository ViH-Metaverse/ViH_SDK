package com.vihmessenger.vihchatbot.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.vihmessenger.vihchatbot.utils.VihLog
import android.widget.Toast
import com.vihmessenger.vihchatbot.utils.SecureClipboard

class OtpCopyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY_OTP = "com.vihmessenger.vihchatbot.services.COPY_OTP"
        const val EXTRA_OTP = "extra_otp"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        // Preliminary checks
        if (context == null) {
            VihLog.e("OtpCopyReceiver", "Context is null in onReceive.")
            return
        }
        if (intent == null) {
            VihLog.e("OtpCopyReceiver", "Intent is null in onReceive.")
            return
        }

        VihLog.d("OtpCopyReceiver", "onReceive triggered. Action: ${intent.action}")

        if (intent.action == ACTION_COPY_OTP) {
            val otpToCopy = intent.getStringExtra(EXTRA_OTP)
            val notificationIdToCancel = intent.getIntExtra("notification_id_for_otp_copy", -1)

            VihLog.d("OtpCopyReceiver", "Processing ACTION_COPY_OTP. OTP=${VihLog.redact(otpToCopy)}, Notification ID: $notificationIdToCancel")

            if (!otpToCopy.isNullOrBlank()) {
                try {
                    // SECURITY (VAPT F-08): sensitive, self-expiring clip — not a bare setPrimaryClip.
                    if (SecureClipboard.copySensitive(context, "OTP", otpToCopy)) {
                        VihLog.d("OtpCopyReceiver", "OTP ${VihLog.redact(otpToCopy)} set to primary clip.")
                        Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
                    } else {
                        VihLog.e("OtpCopyReceiver", "Failed to set OTP on the clipboard.")
                        Toast.makeText(context, "Failed to copy OTP.", Toast.LENGTH_SHORT).show()
                    }

                    if (notificationIdToCancel != -1) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationIdToCancel)
                        VihLog.d("OtpCopyReceiver", "Cancelled notification ID: $notificationIdToCancel after OTP copy attempt.")
                    }
                } catch (e: Exception) {
                    VihLog.e("OtpCopyReceiver", "Error during clipboard operation or notification cancellation", e)
                    Toast.makeText(context, "Error copying OTP.", Toast.LENGTH_SHORT).show()
                }
            } else {
                VihLog.w("OtpCopyReceiver", "OTP to copy was null or blank in intent. Cannot copy.")
                Toast.makeText(context, "No OTP found to copy.", Toast.LENGTH_SHORT).show()
            }
        } else {
            VihLog.w("OtpCopyReceiver", "Received intent with incorrect action: ${intent.action}")
        }
    }
}