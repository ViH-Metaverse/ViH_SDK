package com.vihmessenger.vihchatbot.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vihmessenger.vihchatbot.utils.extensions.getAuthority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class for sharing images using Coroutines
 */
class ImageShareHelper {
    companion object {
        // The correct authority from your error log

        /**
         * Downloads and shares an image from a URL using Coroutines
         * @param context The current context
         * @param imageUrl URL of the image to download and share
         * @param showLoading Whether to show a loading indicator
         */
        fun shareImageFromUrl(
            context: Context,
            imageUrl: String,
            showLoading: Boolean = true
        ) {
            // Create a CoroutineScope with the Main dispatcher
            val scope = CoroutineScope(Dispatchers.Main)

            scope.launch {
                try {
                    // Show loading if requested
                    if (showLoading) {
                        // You can use your preferred loading indicator here
                        Toast.makeText(context, "Preparing image...", Toast.LENGTH_SHORT).show()
                    }

                    // Download image on IO dispatcher
                    val imageUri = withContext(Dispatchers.IO) {
                        downloadAndSaveImage(context, imageUrl)
                    }

                    // Share the image if download was successful
                    if (imageUri != null) {
                        shareImage(context, imageUri)
                    } else {
                        Toast.makeText(context, "Failed to download image", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /**
         * Downloads an image from URL and saves it to the cache directory
         * @return Uri of the saved image or null if download failed
         */
        private suspend fun downloadAndSaveImage(context: Context, imageUrl: String): Uri? {
            return try {
                // Download the image
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                // Ensure the cache directory exists
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()

                // Generate a unique filename
                val fileName = "shared_image_${System.currentTimeMillis()}.png"
                val outputFile = File(cachePath, fileName)

                // Save the bitmap to a file
                FileOutputStream(outputFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                }

                // Return the content URI using FileProvider with the CORRECT authority
                FileProvider.getUriForFile(
                    context,
                    getAuthority(context),  // Using the correct authority constant
                    outputFile
                )

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * Shares an image using the system share dialog
         */
        private fun shareImage(context: Context, imageUri: Uri) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Image")

            if (shareIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooserIntent)
            } else {
                Toast.makeText(context, "No app available to handle this action", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
