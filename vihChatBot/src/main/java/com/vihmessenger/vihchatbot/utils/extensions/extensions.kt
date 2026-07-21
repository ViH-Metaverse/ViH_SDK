package com.vihmessenger.vihchatbot.utils.extensions

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.utils.getVihSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale


fun Context.resizeDrawable(drawableId: Int, width: Int, height: Int): Drawable? {
    val drawable = ContextCompat.getDrawable(this, drawableId) ?: return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return BitmapDrawable(this.resources, bitmap)
}


fun Activity.statusBarGradient() { // Or whatever parameters it takes
    val window: Window = this.window // 'this' refers to the Activity instance

    // --- YOUR LOGIC TO SET THE GRADIENT BACKGROUND TO THE STATUS BAR ---
    // For example, if you are setting a Drawable to window.statusBarDrawable (API 21+)
    // or faking it by placing a view behind a transparent status bar.
    // Let's assume you successfully make the status bar visually light (e.g., a light grey gradient).
    // window.statusBarColor = someBaseColorOfTheGradientIfNeededForOlderApisOrFallback
    // window.statusBarDrawable = yourGradientDrawable // If using this method

    // --- AFTER setting the visual background, determine if it's light or dark ---
    val isGradientEffectivelyLight = true // YOU MUST DETERMINE THIS based on your gradient

    // --- Now, set the icon theme ---
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // For API 30 and above
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isGradientEffectivelyLight
        Log.d(
            "StatusBarIconDebug",
            "statusBarGradient (Activity: ${this.javaClass.simpleName}), API R+: isAppearanceLightStatusBars set to $isGradientEffectivelyLight"
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // For API 23 to 29
        val decorView = window.decorView
        var flags = decorView.systemUiVisibility
        if (isGradientEffectivelyLight) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            Log.d(
                "StatusBarIconDebug",
                "statusBarGradient (Activity: ${this.javaClass.simpleName}), API M+: Adding SYSTEM_UI_FLAG_LIGHT_STATUS_BAR"
            )
        } else {
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            Log.d(
                "StatusBarIconDebug",
                "statusBarGradient (Activity: ${this.javaClass.simpleName}), API M+: Clearing SYSTEM_UI_FLAG_LIGHT_STATUS_BAR"
            )
        }
        decorView.systemUiVisibility = flags
    }
    // For APIs below M, you can't change icon colors, only the status bar background.
}

fun Activity.statusBarSecondaryColor() {
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)

    if (getVihSettings() != null && !getVihSettings()?.style_secondary_color.isNullOrEmpty()) {
        window.statusBarColor = Color.parseColor(getVihSettings()?.style_secondary_color)
    } else {
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorStatusBar)

    }
    window?.apply {
        decorView.systemUiVisibility = if (ColorUtils.calculateLuminance(statusBarColor) >= 0.5) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            0
        }
        statusBarColor = statusBarColor
    }
}

fun Drawable.setColorFilterCompat() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_primary_color), PorterDuff.Mode.SRC_OVER
        )
    }
}

fun Drawable.setColorFilterOpacityCompat() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        try {
            val alpha = 0.1f  // 10% transparency

            // Parse the base color
            val color = Color.parseColor(getVihSettings()?.style_primary_color)

            this.setColorFilter(
                Color.argb(
                    (alpha * 255).toInt(),
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                ),
                PorterDuff.Mode.SRC_OVER
            )
            this.alpha = 50
            Log.d("TAG", "setColorFilterOpacityCompat: ")

        } catch (e: Exception) {
            Log.d("TAG", "setColorFilterOpacityCompat: ${e.toString()}")
        }
    }
}

fun Drawable.setColorFilterCompatSRCIN() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_primary_color), PorterDuff.Mode.SRC_IN
        )
    }
}

fun Drawable.setSecondaryColorFilterCompat() {
    if (getVihSettings() != null && !getVihSettings()?.style_secondary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_secondary_color), PorterDuff.Mode.SRC_OVER
        )
    }
}

fun Drawable.setSecondaryColorFilterCompatSRCIN() {
    if (getVihSettings() != null && !getVihSettings()?.style_secondary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_secondary_color), PorterDuff.Mode.SRC_IN
        )
    }
}

fun Drawable.setAccentColorFilterCompat() {
    if (getVihSettings() != null && !getVihSettings()?.style_accent_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_accent_color), PorterDuff.Mode.SRC_OVER
        )
    }
}

fun Drawable.setGreyColorFilterCompatSRCIN(color: Int) {
    this.setColorFilter(color, PorterDuff.Mode.SRC_IN)
}


fun ImageView.setColorFilterCompat() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_primary_color), PorterDuff.Mode.SRC_OVER
        )
    }
}

fun ImageView.setSolidColorFilterCompat() {
    if (getVihSettings() != null && !getVihSettings()?.solid_color.isNullOrEmpty() && getVihSettings()?.solid_color != "undefined") {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.solid_color), PorterDuff.Mode.SRC_OVER
        )
    }
}

fun ImageButton.setColorImageButtonFilterCompat() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_primary_color), PorterDuff.Mode.SRC_OVER
        )
    }
}

fun AppCompatImageView.setColorAppCompactFilterCompatSRCIN() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_primary_color), PorterDuff.Mode.SRC_IN
        )
    }
}

fun ImageView.setColorFilterCompatSRCIN() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.setColorFilter(
            Color.parseColor(getVihSettings()!!.style_primary_color), PorterDuff.Mode.SRC_IN
        )
    }
}

fun TextView.setVihTextColor() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.setTextColor(
            Color.parseColor(getVihSettings()!!.style_primary_color)
        )
    }
}

fun Int.toHex(): String {
    return String.format("#%06X", 0xFFFFFF and this)
}

fun TextView.setVihTextFontColor() {
    if (getVihSettings() != null && !getVihSettings()?.style_font_color.isNullOrEmpty()) {
        this.setTextColor(
            Color.parseColor(getVihSettings()!!.style_font_color)
        )
    }
}

fun Switch.setVihThumbColor() {
    if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
        this.thumbDrawable.colorFilter = PorterDuffColorFilter(
            Color.parseColor(
                getVihSettings()?.style_primary_color
            ), PorterDuff.Mode.MULTIPLY
        )
    }
}

fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

fun Int.withAlpha(alpha: Int): Int {
    return ColorUtils.setAlphaComponent(this, alpha)
}

fun shareVideoWithThumbnail(filePath: String, context: Context, timeMs: Int = 0) {
    val videoFile = File(filePath)

    if (!videoFile.exists()) {
        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
        return
    }

    // Create a temporary thumbnail file
    val thumbnailFile = File(context.cacheDir, "video_thumbnail.jpg")

    try {
        // Generate thumbnail from the video
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)

        // Get a frame from the video
        val bitmap = if (timeMs > 0) {
            retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
        } else {
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
        }

        // Save the thumbnail
        if (bitmap != null) {
            val fos = FileOutputStream(thumbnailFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.close()
            bitmap.recycle()
        }

        retriever.release()

        // Get content URIs for both files
        val videoUri = FileProvider.getUriForFile(
            context,
            getAuthority(context),
            videoFile
        )

        val thumbUri = FileProvider.getUriForFile(
            context,
            getAuthority(context),
            thumbnailFile
        )

        // Create ClipData for multiple attachments (video + thumbnail)
        var clipData = ClipData.newRawUri("", videoUri)
        clipData.addItem(ClipData.Item(thumbUri))

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, videoUri)

            // Set the thumbnail as EXTRA_TITLE to improve preview on some platforms
            putExtra(Intent.EXTRA_TITLE, "Video")

            clipData = clipData
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share video via"))

    } catch (e: Exception) {
        e.printStackTrace()
        // Fallback to regular sharing without thumbnail
        val videoUri = FileProvider.getUriForFile(
            context,
            getAuthorityProvider(context),
            videoFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share video via"))
    }
}

fun Context.shareVideo(videoPath: String?, context: Context) {
    val FILE_PROVIDER_AUTHORITY = "${context.packageName}.provider"

    if (videoPath.isNullOrEmpty()) {
        Toast.makeText(this, "Video path is empty.", Toast.LENGTH_SHORT).show()
        return
    }
    val videoFile = File(videoPath)
    if (!videoFile.exists()) {
        Toast.makeText(this, "Video file not found for sharing.", Toast.LENGTH_SHORT).show()
        Log.e("ContextExtShareVideo", "File not found: $videoPath")
        return
    }

    try {
        val videoUri: Uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, videoFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Video"))
    } catch (e: IllegalArgumentException) {
        Log.e(
            "ContextExtShareVideo",
            "FileProvider setup issue for path: ${videoFile.absolutePath}. Check authority and file_paths.xml.",
            e
        )
        Toast.makeText(this, "Could not share video. Setup error.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("ContextExtShareVideo", "Error sharing video", e)
        Toast.makeText(this, "Error sharing video: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun Context.saveVideoToGallery(videoPath: String?) {
    if (videoPath.isNullOrEmpty()) {
        Toast.makeText(this, "Video path is empty.", Toast.LENGTH_SHORT).show()
        return
    }
    val videoFile = File(videoPath)
    if (!videoFile.exists()) {
        Toast.makeText(this, "Video file not found to save.", Toast.LENGTH_SHORT).show()
        Log.e("ContextExtSaveVideo", "File not found: $videoPath")
        return
    }

    try {
        val values = android.content.ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4") // Or detect dynamically
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YourAppName") // Scoped storage
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                // For older versions, you might need to handle path differently or request write permission
                val moviesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val appDir = File(moviesDir, "YourAppName")
                if (!appDir.exists()) appDir.mkdirs()
                val newFile = File(appDir, videoFile.name)
                // Copy file to public directory before scanning for older APIs
                // This example will try to insert directly, which might require WRITE_EXTERNAL_STORAGE below Q
                put(MediaStore.MediaColumns.DATA, newFile.absolutePath)
            }
        }

        val resolver = this.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let { targetUri ->
            resolver.openOutputStream(targetUri).use { outStream ->
                requireNotNull(outStream) { "Failed to open output stream for $targetUri" }
                videoFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)
            }
            Toast.makeText(this, "Video saved to Gallery", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "Failed to create MediaStore entry for video", Toast.LENGTH_SHORT)
                .show()
        }

    } catch (e: Exception) {
        Log.e("ContextExtSaveVideo", "Error saving video to gallery", e)
        Toast.makeText(this, "Error saving video: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}


fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}


fun Context.openUrlWithBrowserChoice(
    url: String,
    chooserTitle: String = "Open with"
) {
    try {
        // Ensure the URL has a proper protocol
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        val webpage = Uri.parse(formattedUrl)

        // Create an intent specifically for viewing web pages
        val intent = Intent(Intent.ACTION_VIEW, webpage).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        // List of common browser package names
        val browserPackages = listOf(
            "com.android.chrome",     // Chrome
            "org.mozilla.firefox",    // Firefox
            "com.brave.browser",      // Brave
            "com.opera.browser",      // Opera
            "com.microsoft.emmx"      // Edge
        )

        // Filter and find installed browsers
        val installedBrowsers = browserPackages.mapNotNull { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0)
                packageName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        Log.d("OpenUrlDebug", "Installed browsers: $installedBrowsers")

        when {
            // If multiple browsers are installed, show chooser
            installedBrowsers.size > 1 -> {
                val chooserIntent = Intent.createChooser(intent, chooserTitle)
                startActivity(chooserIntent)
            }

            // If exactly one browser is installed, open directly with that browser
            installedBrowsers.size == 1 -> {
                intent.setPackage(installedBrowsers.first())
                startActivity(intent)
            }

            // If no specific browsers are found, fall back to default intent
            else -> {
                val resolvedActivities =
                    packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

                if (resolvedActivities.isNotEmpty()) {
                    val chooserIntent = Intent.createChooser(intent, chooserTitle)
                    startActivity(chooserIntent)
                } else {
                    Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "Unable to open URL: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun getAuthority(context: Context): String {
    return "${context.packageName}.fileprovider"
}

private fun getAuthorityProvider(context: Context): String {
    return "${context.packageName}.provider"
}

fun Int.toColorStateList(): ColorStateList = ColorStateList.valueOf(this)

fun parseDateToTimestamp(dateString: String): Long {
    val possibleFormats = listOf(
        "MMM, dd yyyy HH:mm:ss",      // Mar, 17 2025 12:12:59
        "yyyy-MM-dd'T'HH:mm:ss'Z'",   // 2025-03-17T12:12:59Z
        "yyyy-MM-dd HH:mm:ss",        // 2025-03-17 12:12:59
        "MM/dd/yyyy HH:mm:ss",        // 03/17/2025 12:12:59
        "dd-MM-yyyy HH:mm:ss",        // 17-03-2025 12:12:59
        "EEE, dd MMM yyyy HH:mm:ss Z" // Wed, 17 Mar 2025 12:12:59 +0000
    )
    //May, 21 2025 11:13:56

    for (format in possibleFormats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.ENGLISH)
            sdf.isLenient = false
            return sdf.parse(dateString)?.time ?: 0L
        } catch (e: ParseException) {
            // Continue to the next format if parsing fails
        }
    }
    return 0L // Return 0 if none of the formats match
}

fun getBlockingBitmapFromUrl(imageUrl: String): Bitmap? {
    var urlConnection: HttpURLConnection? = null
    var inputStream: InputStream? = null
    try {
        val url = URL(imageUrl)
        urlConnection = url.openConnection() as HttpURLConnection
        urlConnection.connectTimeout = 5000 // 5 seconds
        urlConnection.readTimeout = 5000    // 5 seconds
        urlConnection.connect()
        if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
            inputStream = urlConnection.inputStream
            return BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        inputStream?.close()
        urlConnection?.disconnect()
    }
    return null
}