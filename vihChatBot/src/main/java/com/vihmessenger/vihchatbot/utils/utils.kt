package com.vihmessenger.vihchatbot.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vihmessenger.vihchatbot.AppController.Companion.prefs
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.data.model.SdkFeatureModel
import com.vihmessenger.vihchatbot.data.model.UserProfileModel
import com.vihmessenger.vihchatbot.utils.extensions.getAuthority
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun getVihSettings(): SdkFeatureModel? {
    val type: Type = object : TypeToken<SdkFeatureModel>() {}.type

    return Gson().fromJson(prefs!!.vihSettings, type)
}

fun getProfileData(): UserProfileModel? {
    val type: Type = object : TypeToken<UserProfileModel>() {}.type

    return Gson().fromJson(prefs!!.userProfile, type)
}


fun parseTimestamp(timestamp: String): String? {
    val dateFormat = SimpleDateFormat("MMM, dd yyyy HH:mm:ss", Locale.getDefault())
    val date = dateFormat.parse(timestamp) ?: return null

    val currentCalendar = Calendar.getInstance()
    val today = currentCalendar.time

    // Yesterday
    val yesterdayCalendar = Calendar.getInstance()
    yesterdayCalendar.add(Calendar.DATE, -1)
    val yesterday = yesterdayCalendar.time

    // Current year
    val currentYear = currentCalendar.get(Calendar.YEAR)

    // Date's year
    val dateCalendar = Calendar.getInstance().apply { time = date }
    val dateYear = dateCalendar.get(Calendar.YEAR)

    return when {
        // If it's today, show time (03:17 PM)
        isSameDay(date, today) -> {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        }

        // If it's yesterday, just show "Yesterday"
        isSameDay(date, yesterday) -> {
            "Yesterday"
        }

        // If it's in the current year but not today or yesterday, show date and month (04 Mar)
        dateYear == currentYear -> {
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
        }

        // If it's from a previous year, show date, month, and year (04 Mar 2024)
        else -> {
            SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(date)
        }
    }
}

fun currentDateTime(): String {
    val dateFormat = SimpleDateFormat("MMM, dd yyyy HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date())
}

fun parseDateString(dateString: String): Long {
    val dateFormat = SimpleDateFormat("MMM, dd yyyy HH:mm:ss", Locale.getDefault())
    val date: Date =
        dateFormat.parse(dateString) ?: throw IllegalArgumentException("Invalid date format")
    return date.time
}

@RequiresApi(Build.VERSION_CODES.O)
fun getRelativeTime(timeString: String, pattern: String = "MMM, dd yyyy HH:mm:ss"): String {

    val time = parseDateString(timeString)
    val now = System.currentTimeMillis()
    val diff = now - time

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
            "$seconds seconds ago"
        }

        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            "$minutes minutes ago"
        }

        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "$hours hours ago"
        }

        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "$days days ago"
        }

        else -> {
            val dateFormat = SimpleDateFormat("MMM, dd yyyy HH:mm:ss", Locale.getDefault())
            dateFormat.format(Date(time))
        }
    }
}

fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.MONTH) == cal2.get(
        Calendar.MONTH
    ) && cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
}

 fun isSameDayTimeStamp(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun shareImageFromImageView(imageView: ImageView, context: Context) {
    // Get the drawable from the ImageView
    val drawable = imageView.drawable ?: run {
        Toast.makeText(context, "No image available to share", Toast.LENGTH_SHORT).show()
        return
    }

    // Convert drawable to Bitmap
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
        Toast.makeText(context, "Failed to convert image", Toast.LENGTH_SHORT).show()
        return
    }

    // Save the Bitmap locally and get its URI
    val imageUri = saveBitmapToCache(bitmap, context)

    if (imageUri != null) {
        // Share the image using Intent
        shareImage(context, imageUri)
    } else {
        Toast.makeText(context, "Failed to share image", Toast.LENGTH_SHORT).show()
    }
}

// Function to save the Bitmap locally and return the URI
fun saveBitmapToCache(bitmap: Bitmap, context: Context): Uri? {
    return try {
        // Create a file in the cache directory
        val file = File(context.cacheDir, "shared_image.png")
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        // Get the URI using FileProvider
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", // Match the authority in AndroidManifest.xml
            file
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun shareImage(context: Context, imageUri: Uri) {
    val imageFile = uriToFile(context, imageUri) ?: return

    val uri = FileProvider.getUriForFile(
        context,
        getAuthority(context),
        imageFile
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Grant permission to receiving apps
    val chooser = Intent.createChooser(shareIntent, "Share Image")
    val resInfoList =
        context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
    for (resolveInfo in resInfoList) {
        val packageName = resolveInfo.activityInfo.packageName
        context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(chooser)
}

fun uriToFile(context: Context, uri: Uri): File? {
    val contentResolver = context.contentResolver
    val tempFile =
        File.createTempFile("temp_", ".jpg", context.cacheDir) // Change extension as needed

    try {
        val inputStream = contentResolver.openInputStream(uri)
        val outputStream = FileOutputStream(tempFile)

        inputStream?.copyTo(outputStream)

        inputStream?.close()
        outputStream.close()

        return tempFile
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun saveImageToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String = "image_${System.currentTimeMillis()}.jpg"
) {
    val resolver = context.contentResolver
    val imageCollection =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    // Create ContentValues to insert metadata
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + "/Vih Messenger"
        ) // Fixed folder name
        put(MediaStore.Images.Media.IS_PENDING, 1) // Pending state before saving
    }

    // Insert image details into MediaStore
    val imageUri: Uri? = resolver.insert(imageCollection, contentValues)
    imageUri?.let { uri ->
        try {
            // Open output stream and write the bitmap
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
            }

            // Mark the image as complete
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    } ?: run {
        Toast.makeText(context, "Failed to create new MediaStore record", Toast.LENGTH_SHORT).show()
    }
}

fun saveImageViewToGallery(imageView: ImageView, context: Context) {
    val drawable = imageView.drawable as? BitmapDrawable
    val bitmap = drawable?.bitmap

    if (bitmap != null) {
        saveImageToGallery(context, bitmap) // Folder name is hardcoded in saveImageToGallery
    } else {
        Toast.makeText(context, "Failed to retrieve image from ImageView", Toast.LENGTH_SHORT)
            .show()
    }
}

fun formatFileSize(bytes: Long): String {
    return if (bytes >= 1024 * 1024) { // If the size is greater than or equal to 1MB
        val mb = bytes / (1024 * 1024)
        String.format("%.2f MB", mb.toDouble())
    } else { // If the size is less than 1MB
        val kb = bytes / 1024
        String.format("%d KB", kb)
    }
}

fun setRoundedCorners(imageView: ImageView, context: Context) {
    val drawable = ContextCompat.getDrawable(context, R.drawable.placeholder) as BitmapDrawable
    val bitmap = drawable.bitmap
    val cornerRadiusPx = dpToPx(context, 16) // Convert 16dp to pixels
    val roundedBitmap = getRoundedCornerBitmap(bitmap, cornerRadiusPx) // Apply rounded corners

    imageView.setImageBitmap(roundedBitmap)
}

fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Int): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint()
    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    paint.isAntiAlias = true
    canvas.drawARGB(0, 0, 0, 0)
    paint.color = Color.parseColor("#FFFFFF")
    canvas.drawRoundRect(RectF(rect), cornerRadius.toFloat(), cornerRadius.toFloat(), paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)

    return output
}

// Converts dp to pixels based on screen density
fun dpToPx(context: Context, dp: Int): Int {
    val density = context.resources.displayMetrics.density
    return (dp * density).toInt()
}
