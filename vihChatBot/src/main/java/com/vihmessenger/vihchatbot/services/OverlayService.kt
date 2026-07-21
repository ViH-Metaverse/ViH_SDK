package com.vihmessenger.vihchatbot.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.ui.activity.home.ChatActivity
import org.json.JSONObject

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    companion object {
        const val OVERLAY_CHANNEL_ID = "OverlayServiceChannel"
        const val OVERLAY_NOTIFICATION_ID = 101 // A unique ID for the foreground notification
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("OverlayService", "Service starting.")
        if (intent == null) {
            Log.e("OverlayService", "Service started with a null intent. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Get all the data from the intent
        val title = intent.getStringExtra("title") ?: "New Message"
        val message = intent.getStringExtra("message") ?: "You have a new message"
        val dataBundle = intent.getBundleExtra("data_bundle")

        // CRITICAL: Promote this service to a foreground service.
        // This is required on Android 8+ to do work from the background and is essential for showing an overlay.
        startForeground(OVERLAY_NOTIFICATION_ID, createForegroundNotification(title))

        // Show the actual overlay window on the screen
        showOverlay(title, message, dataBundle)

        // Don't restart the service automatically if it's killed, as the intent data would be lost.
        return START_NOT_STICKY
    }

    private fun createForegroundNotification(title: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "On-Screen Alerts", // User-visible name in Android settings
                NotificationManager.IMPORTANCE_LOW // Low importance so it's not intrusive to the user
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // This persistent notification is required by the OS to keep the service alive.
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Displaying important alert on screen.")
            .setSmallIcon(R.drawable.ic_notification) // Use your app's monochrome icon
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay(title: String, message: String, data: Bundle?) {
        // Avoid adding multiple windows if the service is somehow started twice
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.custom_notification_layout_expanded, null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Define the layout parameters for the overlay window
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // This type is crucial for being able to draw over other apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // This makes the window non-focusable and allows events to pass through to apps below.
            // You might want to change this to 0 if you want the overlay to be interactive (e.g., with buttons).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
            // Add some margin from the top
            y = 50
        }

        try {
            // Populate the view with the correct data
            overlayView?.findViewById<TextView>(R.id.notification_title_expanded)?.text = title
            overlayView?.findViewById<TextView>(R.id.notification_message_expanded)?.text = message

            // Add an OnClickListener to the entire view to open the app and close the overlay
            overlayView?.setOnClickListener {
                // When the overlay is clicked, open the main app
                val chatIntent = Intent(applicationContext, ChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    if (data != null) {
                        putExtras(data)
                    }
                }
                startActivity(chatIntent)

                // And then stop the service, which will remove the overlay
                stopSelf()
            }


            windowManager.addView(overlayView, params)
            Log.d("OverlayService", "Overlay view added successfully.")

        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding overlay view to window manager", e)
            stopSelf() // Stop service if we fail to show the view
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("OverlayService", "Service is being destroyed.")
        // Clean up the view when the service is stopped
        overlayView?.let {
            // Check if the view is still attached to the window before trying to remove it to prevent crashes.
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
    }
}