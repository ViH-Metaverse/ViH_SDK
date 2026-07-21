package com.vihmessenger.vihchatbot.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.ui.activity.home.ChatActivity
import com.vihmessenger.vihchatbot.utils.CorrelationLogger
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val NOTIFICATION_GROUP_KEY = "com.vihmessenger.vihchatbot.NOTIFICATION_GROUP"
        private const val SUMMARY_NOTIFICATION_ID = 0 // Fixed ID for the summary notification
        private var unreadMessagesCount = 0 // Counter for active notifications
        private val messageLines = mutableListOf<String>() // To show lines in summary
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // SECURITY: Only log tokens in debug builds
        if (BuildConfig.DEBUG) {
            Log.d("FIREBASE_TOKEN", "New token: $token")
        }
        sendRegistrationTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val shootId = data[AppConstants.EXTRA_SHOOT_ID]
        val messageId = data[AppConstants.EXTRA_MESSAGE_ID]
        val traceId = data[AppConstants.EXTRA_TRACE_ID]

        CorrelationLogger.debug(
            tag = "FCM_DIAGNOSTIC",
            message = "onMessageReceived on ${Thread.currentThread().name}, keys=${data.keys}",
            shootId = shootId,
            messageId = messageId,
            traceId = traceId
        )

        unreadMessagesCount++

        handleDataPayloadAndShowNotification(data)
    }

    private fun handleDataPayloadAndShowNotification(data: Map<String, String>) {
        val messageForDisplay = parseHtmlMessage(data["msg"])
        val titleForDisplay = data["display_name"] ?: "New Message"
        val enterpriseIconUrl = data["display_img"]

        messageLines.add(0, "**${titleForDisplay}**: $messageForDisplay")
        if (messageLines.size > 5) {
            messageLines.removeAt(messageLines.size - 1)
        }

        var headerImageUrl: String? = null
        var isVideoHeader = false

        if (data["is_header_img"] == "1") {
            headerImageUrl = data["image_url"]
        }
        if (data["is_header_vid"] == "1") {
            isVideoHeader = true
            if (headerImageUrl.isNullOrBlank()) {
                headerImageUrl = data["thumbnail"]
            }
        }

        // Only surface the "Copy OTP" action for real OTP pushes (template_type/templ_typ == "1"),
        // per the OTP API-key contract (OTP_MOBILE_SPEC §2). Previously the regex fired on any
        // message containing a 4–8 digit run, so non-OTP messages got a spurious Copy action.
        val isOtpPush = data["template_type"] == "1" || data["templ_typ"] == "1"
        val extractedOtp = if (isOtpPush) extractOtpFromMessage(data["msg"]) else null

        val enterpriseLogoBitmap = getBlockingBitmapFromUrl(enterpriseIconUrl)
        val mainImageBitmap = getBlockingBitmapFromUrl(headerImageUrl)

        showCustomNotification(
            messageBody = messageForDisplay,
            data = data,
            displayedTitle = titleForDisplay,
            isVideoHeader = isVideoHeader,
            enterpriseLogoBitmap = enterpriseLogoBitmap,
            mainImageBitmap = mainImageBitmap,
            otp = extractedOtp
        )

        if (AppController.isAppInForeground()) {
            handleDataPayload(data)
        }
    }

    private fun showCustomNotification(
        messageBody: String,
        data: Map<String, String>,
        displayedTitle: String,
        isVideoHeader: Boolean,
        enterpriseLogoBitmap: Bitmap?,
        mainImageBitmap: Bitmap?,
        otp: String?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("FCM_PERMISSION", "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
                return
            }
        }
        val channelId = "custom_channel_v4"
        val channelNameString = "App Custom Notifications"
        val notificationManager = NotificationManagerCompat.from(this)
        val notificationId = System.currentTimeMillis().toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelNameString, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val contentPendingIntent = createContentIntent(notificationId, data)
        val deletePendingIntent = createDeleteIntent(notificationId)

        val customNotificationIcon = Prefs.getInstance(this).notificationIcon
        val notificationIconRes = if (customNotificationIcon != 0) customNotificationIcon else R.drawable.ic_notification

        val collapsedView = RemoteViews(packageName, R.layout.custom_notification_layout).apply {
            setTextViewText(R.id.notification_title, displayedTitle)
            setTextViewText(R.id.notification_message, messageBody)
            if (enterpriseLogoBitmap != null) setImageViewBitmap(R.id.notification_icon, enterpriseLogoBitmap)
            else setImageViewResource(R.id.notification_icon, notificationIconRes)
        }

        val expandedView = RemoteViews(packageName, R.layout.custom_notification_layout_expanded).apply {
            setTextViewText(R.id.notification_title_expanded, displayedTitle)
            setTextViewText(R.id.notification_message_expanded, messageBody)
            if (enterpriseLogoBitmap != null) setImageViewBitmap(R.id.notification_icon_expanded, enterpriseLogoBitmap)
            else setImageViewResource(R.id.notification_icon_expanded, notificationIconRes)

            if (mainImageBitmap != null) {
                setViewVisibility(R.id.notification_image_container_expanded, View.VISIBLE)
                setImageViewBitmap(R.id.notification_image_content, mainImageBitmap)
                setViewVisibility(R.id.notification_center_icon, if (isVideoHeader) View.VISIBLE else View.GONE)
                if (isVideoHeader) setImageViewResource(R.id.notification_center_icon, R.drawable.ic_play)
            } else {
                setViewVisibility(R.id.notification_image_container_expanded, View.GONE)
            }
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(notificationIconRes)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setGroup(NOTIFICATION_GROUP_KEY)

        addNotificationActions(
            notificationBuilder,
            otp,
            notificationId,
            contentPendingIntent
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("$unreadMessagesCount unread messages")
            .setSummaryText("You have new messages")
        messageLines.forEach { inboxStyle.addLine(it) }

        val summaryNotificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("New Messages")
            .setContentText("$unreadMessagesCount unread messages")
            .setSmallIcon(notificationIconRes)
            .setStyle(inboxStyle)
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        notificationManager.notify(notificationId, notificationBuilder.build())
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotificationBuilder.build())
    }

    private fun addNotificationActions(
        builder: NotificationCompat.Builder,
        otp: String?,
        notificationId: Int,
        contentPI: PendingIntent
    ) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT

        if (!otp.isNullOrEmpty()) {
            val copyIntent = Intent(this, OtpCopyReceiver::class.java).apply {
                action = OtpCopyReceiver.ACTION_COPY_OTP
                putExtra(OtpCopyReceiver.EXTRA_OTP, otp)
                putExtra(OtpCopyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val copyPI = PendingIntent.getBroadcast(this, notificationId + 1, copyIntent, flags)
            // SECURITY: Mask OTP in notification action to prevent exposure to notification listeners
            builder.addAction(0, "Copy OTP", copyPI)
        }

        builder.addAction(0, "View More", contentPI)
    }

    private fun createContentIntent(notificationId: Int, data: Map<String, String>): PendingIntent {
        val intent = Intent(this, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppConstants.ID, data["enterprise_id"])
            putExtra(AppConstants.CHANNEL_NAME, data["display_name"])
            putExtra(AppConstants.CHANNEL_LOGO, data["display_img"])
            putExtra("notification_id", notificationId)
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(this, notificationId, intent, flags)
    }

    private fun createDeleteIntent(notificationId: Int): PendingIntent {
        val intent = Intent(this, NotificationDismissedReceiver::class.java).apply {
            action = NotificationDismissedReceiver.ACTION_NOTIFICATION_DISMISSED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_ONE_SHOT
        return PendingIntent.getBroadcast(this, notificationId, intent, flags)
    }

    private fun extractOtpFromMessage(htmlMessage: String?): String? {
        if (htmlMessage.isNullOrEmpty()) return null
        val textMessage = parseHtmlMessage(htmlMessage)
        val pattern = Pattern.compile("\\b(\\d{4,8})\\b")
        val matcher = pattern.matcher(textMessage)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun getBlockingBitmapFromUrl(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input).also { connection.disconnect() }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("FCM_IMG_DOWNLOAD", "Error downloading image", e)
            }
            null
        }
    }

    private fun parseHtmlMessage(htmlString: String?): String {
        val defaultMessage = "You have a new message"
        if (htmlString.isNullOrEmpty()) return defaultMessage
        return try {
            val parsedHtml = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlString)
            }
            parsedHtml.toString().trim()
        } catch (e: Exception) {
            htmlString.trim()
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val intent = Intent("com.vihmessenger.vihchatbot.FCM_MESSAGE").apply {
            for ((key, value) in data) {
                putExtra(key, value)
            }
        }
        // Surface the correlation IDs so downstream Activities/ViewModels can attach
        // them to their own logs without re-parsing the data map (architecture §6.2).
        CorrelationLogger.debug(
            tag = "FCM_DIAGNOSTIC",
            message = "Broadcasting FCM payload to in-app receivers",
            shootId = data[AppConstants.EXTRA_SHOOT_ID],
            messageId = data[AppConstants.EXTRA_MESSAGE_ID],
            traceId = data[AppConstants.EXTRA_TRACE_ID]
        )
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun sendRegistrationTokenToServer(token: String) {
        // Pushes (deviceId, fcmToken) to the session registry described in
        // architecture §3.3. The registrar persists the token via Prefs first, so a
        // missing-auth failure here is retried automatically on the next login.
        DeviceTokenRegistrar.onNewToken(applicationContext, token)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) {
            Log.d("FIREBASE_SERVICE", "MyFirebaseMessagingService destroyed.")
        }
    }

    class OtpCopyReceiver : BroadcastReceiver() {
        companion object {
            const val ACTION_COPY_OTP = "com.vihmessenger.vihchatbot.services.COPY_OTP"
            const val EXTRA_OTP = "extra_otp"
            const val EXTRA_NOTIFICATION_ID = "notification_id_for_otp_copy"
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null || intent.action != ACTION_COPY_OTP) return
            val otpToCopy = intent.getStringExtra(EXTRA_OTP)
            val notificationIdToCancel = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            if (!otpToCopy.isNullOrBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OTP", otpToCopy))
                // SECURITY: Don't expose OTP value in Toast
                Toast.makeText(context, "OTP copied to clipboard", Toast.LENGTH_SHORT).show()

                if (notificationIdToCancel != -1) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationIdToCancel)

                    val dismissIntent = Intent(context, NotificationDismissedReceiver::class.java).apply {
                        action = NotificationDismissedReceiver.ACTION_NOTIFICATION_DISMISSED
                    }
                    context.sendBroadcast(dismissIntent)
                }
            }
        }
    }

    class NotificationDismissedReceiver : BroadcastReceiver() {
        companion object {
            const val ACTION_NOTIFICATION_DISMISSED = "com.vihmessenger.vihchatbot.services.NOTIFICATION_DISMISSED"
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent?.action != ACTION_NOTIFICATION_DISMISSED) return

            unreadMessagesCount--
            if (BuildConfig.DEBUG) {
                Log.d("FCM_DISMISS", "Notification dismissed. Remaining count: $unreadMessagesCount")
            }

            val notificationManager = NotificationManagerCompat.from(context)
            if (unreadMessagesCount > 0) {
                val customNotificationIcon = Prefs.getInstance(context).notificationIcon
                val notificationIconRes = if (customNotificationIcon != 0) customNotificationIcon else R.drawable.ic_notification

                val summaryNotificationBuilder = NotificationCompat.Builder(context, "custom_channel_v4")
                    .setContentTitle("New Messages")
                    .setContentText("$unreadMessagesCount unread messages")
                    .setSmallIcon(notificationIconRes)
                    .setGroup(NOTIFICATION_GROUP_KEY)
                    .setGroupSummary(true)
                    .setAutoCancel(true)

                notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotificationBuilder.build())
            } else {
                notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
                messageLines.clear()
            }
        }
    }
}