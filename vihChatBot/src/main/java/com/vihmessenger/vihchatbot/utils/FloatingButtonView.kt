package com.vihmessenger.vihchatbot.utils

/**
 * A custom floating button view that can be included in XML layouts.
 * Features:
 * - Rounded shape
 * - Customizable center image
 * - Customizable background color
 * - Click action to start an activity with a passed value
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Patterns
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vihmessenger.vihchatbot.BuildConfig
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.config.VihConfig
import com.vihmessenger.vihchatbot.config.VihConfigStore
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class FloatingButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_BACKGROUND_COLOR = Color.BLUE
        private const val DEFAULT_SIZE_DP = 56 // Material design FAB standard size
        private const val DEFAULT_TARGET_ACTIVITY =
            "com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity" // <-- CHANGE THIS LINE

        // In-app broadcast the SDK fires (via LocalBroadcastManager) on every received push —
        // see MyFirebaseMessagingService.handleDataPayload(). The widget listens for it to pop.
        private const val FCM_MESSAGE_ACTION = "com.vihmessenger.vihchatbot.FCM_MESSAGE"

        /**
         * Start the SDK with the provided context and hashcode.
         *
         * @param context The context to start the activity from.
         * @param phone The user's phone number with country code (e.g., "919876543210"). This is mandatory.
         * @param hashcode The hashcode to pass to the DashboardActivity. This is mandatory.
         * @param name Optional: The user's name.
         * @param userProfileUrl Optional: A URL for the user's profile picture.
         * @param userProfileBitmap Optional: A Bitmap for the user's profile picture.
         * @param email Optional: The user's email address.
         * @param notificationIcon Optional: A drawable resource for the notification icon.
         * @throws IllegalArgumentException if mandatory fields (phone, hashcode) are missing or invalid, or if both userProfileUrl and userProfileBitmap are provided.
         */
        fun startSdk(
            context: Context,
            phone: String,
            hashcode: String,
            name: String? = null,
            userProfileUrl: String? = null,
            userProfileBitmap: Bitmap? = null,
            email: String? = null,
            notificationIcon: Int? = null
        ) {
            // 1. Validate mandatory fields
            if (hashcode.isBlank()) {
                throw IllegalArgumentException("Hashcode cannot be empty.")
            }
            if (phone.isBlank()) {
                throw IllegalArgumentException("Phone number cannot be empty.")
            }
            if (!isValidPhoneNumber(phone)) {
                throw IllegalArgumentException("Invalid phone number format. It must be a valid number with country code and without the '+' sign.")
            }

            // 2. Validate optional fields
            if (userProfileUrl != null && userProfileBitmap != null) {
                throw IllegalArgumentException("Provide either userProfileUrl or userProfileBitmap, not both.")
            }
            if (userProfileUrl != null && !Patterns.WEB_URL.matcher(userProfileUrl).matches()) {
                throw IllegalArgumentException("Invalid user profile URL.")
            }
            if (email != null && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                throw IllegalArgumentException("Invalid email address format.")
            }

            // 3. Save necessary data to preferences
            val prefs = Prefs.getInstance(context)
            if (hashcode != BuildConfig.DEFAULT_HASHCODE) {
                // Reset stale session state if this is a switch to a different channel.
                prefs.switchChannel(hashcode)
                prefs.isSDK = true
            }

            name?.let { prefs.name = it }
            email?.let { prefs.email = it }
            notificationIcon?.let { prefs.notificationIcon = it }

            userProfileUrl?.let { prefs.userProfileUrl = it }
            userProfileBitmap?.let {
                val imagePath = saveBitmapToInternalStorage(context, it)
                prefs.userProfileUrl = imagePath
            }


            // 4. Create and launch the intent
            val intent = Intent().apply {
                setClassName(context, DEFAULT_TARGET_ACTIVITY)
                putExtra(AppConstants.HASHCODE_EXTRA, hashcode)
                putExtra(AppConstants.PHONENUMBER, phone)

                // Add optional data to the intent
                name?.let { putExtra("USER_NAME", it) }
                userProfileUrl?.let { putExtra("USER_PROFILE_URL", it) }
                email?.let { putExtra("USER_EMAIL", it) }
                notificationIcon?.let { putExtra("NOTIFICATION_ICON", it) }
            }
            context.startActivity(intent)
        }

        /**
         * White-label entry point. Same as [startSdk] above, plus a [VihConfig] that reshapes
         * the SDK UI — brand colors and the tab set (split / rename / re-icon). The config is
         * stored process-wide ([VihConfigStore]); the brand theme is applied immediately so the
         * first frame is on-brand (host colors win — re-applied after server features load).
         *
         * @param config Customization for tabs + theme. See [VihConfig].
         */
        fun startSdk(
            context: Context,
            phone: String,
            hashcode: String,
            config: VihConfig,
            name: String? = null,
            userProfileUrl: String? = null,
            userProfileBitmap: Bitmap? = null,
            email: String? = null,
            notificationIcon: Int? = null
        ) {
            VihConfigStore.set(config)
            // Seed the brand colors up front so the dashboard opens on-brand. The dashboard
            // re-applies this override after the server SDK-features response so host wins.
            config.theme?.let { theme ->
                DynamicThemeManager.applyHostOverride(
                    context, theme.primary, theme.onPrimary, theme.secondary, theme.accent
                )
            }
            startSdk(
                context, phone, hashcode, name,
                userProfileUrl, userProfileBitmap, email, notificationIcon
            )
        }

        private fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
            return try {
                val file = File(context.cacheDir, "profile_image.png")
                FileOutputStream(file).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * Validates a phone number.
         *
         * @param phone The phone number to validate.
         * @return True if the phone number is valid, false otherwise.
         */
        private fun isValidPhoneNumber(phone: String): Boolean {
            // This pattern checks for a country code followed by the rest of the number.
            // It ensures the number does not start with '+'.
            val phonePattern = "^[1-9][0-9]{9,14}$"
            return Pattern.matches(phonePattern, phone)
        }
    }

    // Paint for drawing the background
    private val backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = DEFAULT_BACKGROUND_COLOR
    }

    // The drawable to be displayed in the center
    private var centerImage: Drawable? = null

    // Resource ID for the center image
    private var centerImageResId: Int = 0

    // The key for the value to pass to the target activity
    private var extraKey: String = "hashcode"

    // The value to pass to the target activity
    private var extraValue: String? = null

    // Button state
    private var isPressed = false

    // --- Custom-integration widget behavior ---

    /** When false, tapping does not launch the SDK itself — the host's OnClickListener handles it
     *  (e.g. to call [startSdk] with the phone). Default true keeps the built-in launch. */
    var launchesSdkOnClick: Boolean = true

    /** When true (default), the widget pops (up then back) on each in-app "message received"
     *  broadcast the SDK fires on an incoming push. */
    var popOnMessageEnabled: Boolean = true

    /** When true, draw only the center image (no background circle) — for a full-bleed logo/PNG
     *  such as the client's floating-widget artwork. */
    var imageOnly: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Unread-message count shown in a small red badge at the top-right. 0 hides the badge.
     *  Auto-incremented on each incoming-message broadcast (see [countUnreadOnMessage]) and
     *  cleared on tap; a host can also set it directly from a real source (e.g. the summed
     *  chat-list unseen counts). */
    var unreadCount: Int = 0
        set(value) {
            val v = value.coerceAtLeast(0)
            if (field != v) {
                field = v
                invalidate()
            }
        }

    /** When true (default), each incoming-message broadcast bumps [unreadCount] by one. Turn off
     *  if the host drives [unreadCount] itself. */
    var countUnreadOnMessage: Boolean = true

    /** Show/hide the unread badge without losing the count. */
    var unreadBadgeEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Increase the unread badge by one. */
    fun incrementUnread() {
        unreadCount += 1
    }

    /** Reset the unread badge to zero. */
    fun clearUnread() {
        unreadCount = 0
    }

    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E53935") // red
    }
    private val badgeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Pops the widget when the SDK broadcasts an incoming message (registered while attached).
    private val fcmMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != FCM_MESSAGE_ACTION) return
            if (countUnreadOnMessage) incrementUnread()
            if (popOnMessageEnabled) popUp()
        }
    }

    // Preferences instance
    private val prefs: Prefs = Prefs.getInstance(context)

    init {
        // Parse custom attributes if available
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(
                attrs, R.styleable.FloatingButtonView
            )

            try {
                // Get background color attribute
                val backgroundColor = typedArray.getColor(
                    R.styleable.FloatingButtonView_backgroundColor, DEFAULT_BACKGROUND_COLOR
                )
                backgroundPaint.color = backgroundColor

                // Get center image attribute
                if (typedArray.hasValue(R.styleable.FloatingButtonView_centerImage)) {
                    centerImageResId = typedArray.getResourceId(
                        R.styleable.FloatingButtonView_centerImage, 0
                    )

                    if (centerImageResId != 0) {
                        try {
                            centerImage = ContextCompat.getDrawable(context, centerImageResId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Get extra value
                extraValue = typedArray.getString(R.styleable.FloatingButtonView_extraValue)
            } finally {
                typedArray.recycle()
            }
        }

        // Set up click detection
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Convert default size to pixels
        val density = resources.displayMetrics.density
        val defaultSize = (DEFAULT_SIZE_DP * density + 0.5f).toInt()

        // Handle wrap_content
        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)

        // Make sure the button is a perfect circle by using the smaller dimension
        val size = minOf(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(fcmMessageReceiver, IntentFilter(FCM_MESSAGE_ACTION))
    }

    override fun onDetachedFromWindow() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(fcmMessageReceiver)
        super.onDetachedFromWindow()
    }

    /**
     * Play the "message received" cue: the widget rises up, then settles back with a slight
     * overshoot. Triggered automatically on the SDK's incoming-message broadcast (see
     * [popOnMessageEnabled]); also callable directly.
     */
    fun popUp() {
        animate().cancel()
        val riseBy = -resources.displayMetrics.density * 18f
        animate()
            .translationY(riseBy)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                animate()
                    .translationY(0f)
                    .setDuration(280)
                    .setInterpolator(OvershootInterpolator(3f))
                    .start()
            }
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val radius = minOf(w, h) / 2f

        // Background circle — skipped in image-only mode (full-bleed logo/PNG).
        if (!imageOnly) {
            canvas.drawCircle(w / 2f, h / 2f, radius, backgroundPaint)
        }

        // Draw center image if available. In image-only mode it's center-inside (aspect
        // preserved, for non-square artwork); otherwise it's inset ~20% inside the circle.
        centerImage?.let { drawable ->
            if (imageOnly) {
                val iw = drawable.intrinsicWidth.toFloat()
                val ih = drawable.intrinsicHeight.toFloat()
                if (iw > 0f && ih > 0f) {
                    val scale = minOf(width / iw, height / ih)
                    val dw = (iw * scale).toInt()
                    val dh = (ih * scale).toInt()
                    val left = (width - dw) / 2
                    val top = (height - dh) / 2
                    drawable.setBounds(left, top, left + dw, top + dh)
                } else {
                    drawable.setBounds(0, 0, width, height)
                }
            } else {
                val padding = (radius * 0.2f).toInt()
                drawable.setBounds(padding, padding, width - padding, height - padding)
            }
            drawable.alpha = 255
            canvas.save()
            drawable.draw(canvas)
            canvas.restore()
        }

        // Unread-message badge — a small red circle at the top-right with the count.
        if (unreadBadgeEnabled && unreadCount > 0) {
            val density = resources.displayMetrics.density
            val label = if (unreadCount > 99) "99+" else unreadCount.toString()
            val badgeRadius = (if (label.length >= 3) 11f else 9f) * density
            val cx = w - badgeRadius - 1f * density
            val cy = badgeRadius + 1f * density

            canvas.drawCircle(cx, cy, badgeRadius + 1.5f * density, badgeRingPaint) // white ring
            canvas.drawCircle(cx, cy, badgeRadius, badgeFillPaint)                  // red fill

            badgeTextPaint.textSize = badgeRadius * (if (label.length >= 3) 0.9f else 1.15f)
            val textY = cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            canvas.drawText(label, cx, textY, badgeTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                // Darken color when pressed
                val pressedColor = adjustColorBrightness(backgroundPaint.color, 0.8f)
                backgroundPaint.color = pressedColor
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isPressed) {
                    // Restore original color
                    val originalColor = adjustColorBrightness(backgroundPaint.color, 1.25f)
                    backgroundPaint.color = originalColor

                    // Handle click - start the target activity
                    performClick()
                    isPressed = false
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                // Restore original color
                val originalColor = adjustColorBrightness(backgroundPaint.color, 1.25f)
                backgroundPaint.color = originalColor
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()

        // Opening the SDK = the user is reading their messages, so clear the unread badge.
        clearUnread()

        // Host opted to handle the tap itself (e.g. call startSdk with the phone) — don't also
        // fire the built-in launch.
        if (!launchesSdkOnClick) return true

        try {
            // Create an intent for the DashboardActivity
            val intent = Intent().apply {
                setClassName(context, DEFAULT_TARGET_ACTIVITY)

                // Add hashcode data if value is specified
                if (extraValue != null) {
                    // It's good practice to validate the hashcode here as well
                    if (extraValue!!.isBlank()) {
                        // Handle the error, maybe log it or show a toast
                        return false
                    }
                    putExtra(extraKey, extraValue)

                    // Check if hashcode is not equal to BuildConfig.HASHCODE
                    // and save it in preferences if it's different
                    if (extraValue != BuildConfig.DEFAULT_HASHCODE) {
                        // Reset stale session state if this is a switch to a different channel.
                        prefs.switchChannel(extraValue!!)
                        prefs.isSDK = true
                    }
                }
            }

            // Start the activity
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    // Helper method to adjust color brightness
    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val red = minOf(255, maxOf(0, (Color.red(color) * factor).toInt()))
        val green = minOf(255, maxOf(0, (Color.green(color) * factor).toInt()))
        val blue = minOf(255, maxOf(0, (Color.blue(color) * factor).toInt()))
        return Color.rgb(red, green, blue)
    }

    // Setter methods for programmatic customization

    /**
     * Set the background color of the button
     * @param color the color to set
     */
    fun setButtonBackgroundColor(color: Int) {
        backgroundPaint.color = color
        invalidate()
    }

    /**
     * Set the center image drawable
     * @param drawable the drawable to display in the center
     */
    fun setCenterImage(drawable: Drawable?) {
        centerImage = drawable
        // Reset bounds to force recalculation in onDraw
        centerImage?.setBounds(0, 0, 0, 0)
        invalidate()
    }

    /**
     * Set the center image resource ID
     * @param resId the resource ID of the drawable to display
     */
    fun setCenterImageResource(resId: Int) {
        if (resId != 0) {
            try {
                centerImageResId = resId
                centerImage = ContextCompat.getDrawable(context, resId)
                // Reset bounds to force recalculation in onDraw
                centerImage?.setBounds(0, 0, 0, 0)
                invalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Set the hashcode value to pass to the DashboardActivity
     * @param hashcode the hashcode value to pass
     */
    fun setHashcode(hashcode: String) {
        extraValue = hashcode

        // Check if hashcode is not equal to BuildConfig.HASHCODE
        // and save it in preferences if it's different
        if (hashcode != BuildConfig.DEFAULT_HASHCODE) {
            // Reset stale session state if this is a switch to a different channel.
            prefs.switchChannel(hashcode)
            prefs.isSDK = true
        }
    }
}