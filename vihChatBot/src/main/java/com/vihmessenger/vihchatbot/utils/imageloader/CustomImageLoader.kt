package com.vihmessenger.vihchatbot.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object CustomImageLoader {

    /**
     * Loads an image into an ImageView from a URL, URI, or local file path.
     *
     * @param imageView The ImageView to load the image into.
     * @param url The image source (can be a network URL, content/file URI, or absolute file path).
     * @param name A fallback name to generate initials from if the image fails to load.
     * @param onError A callback function to execute if the image fails to load.
     * @param onSuccess A callback function to execute if the image loads successfully.
     * @param applyCircleCrop If true, the loaded image will be cropped into a circle.
     * @param skipCache If true, skips reading from and writing to the memory cache for network images.
     * @param progressBar An optional ProgressBar to show while the image is loading.
     */
    fun loadImageView(
        imageView: ImageView,
        url: String?,
        name: String? = null,
        onError: (() -> Unit)? = null,
        onSuccess: (() -> Unit)? = null,
        applyCircleCrop: Boolean = false,
        skipCache: Boolean = false,
        progressBar: ProgressBar? = null
    ) {
        progressBar?.visibility = View.VISIBLE

        // 1. Handle null or empty URL
        if (url.isNullOrEmpty()) {
            progressBar?.visibility = View.GONE
            if (name.isNullOrEmpty()) {
                onError?.invoke()
            } else {
                setInitials(imageView, name)
            }
            return
        }

        // 2. Handle local file path
        val file = File(url)
        if (file.exists() && file.isFile) {
            val bitmap = loadImageFromFile(file, applyCircleCrop)
            progressBar?.visibility = View.GONE
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                onSuccess?.invoke()
            } else {
                onError?.invoke()
            }
            return
        }


        // 3. Handle local content/file URI
        if (url.startsWith("content://") || url.startsWith("file://")) {
            val bitmap = loadLocalImageFromUri(imageView.context, Uri.parse(url), applyCircleCrop)
            progressBar?.visibility = View.GONE
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                onSuccess?.invoke()
            } else {
                onError?.invoke()
            }
            return
        }

        // 4. Handle network URL
        // Load from cache if enabled
        if (!skipCache) {
            val cachedBitmap = BitmapCache.get(url)
            if (cachedBitmap != null) {
                progressBar?.visibility = View.GONE
                imageView.setImageBitmap(cachedBitmap)
                onSuccess?.invoke()
                return
            }
        }

        // Add a timestamp to the URL to bypass server-side caching if skipCache is true
        val finalUrl = if (skipCache) {
            if (url.contains("?")) "$url&_t=${System.currentTimeMillis()}"
            else "$url?_t=${System.currentTimeMillis()}"
        } else url

        GlobalScope.launch(Dispatchers.Main) {
            val bitmap = actualLoadImageFromUrl(finalUrl, applyCircleCrop)
            progressBar?.visibility = View.GONE
            if (bitmap != null) {
                if (!skipCache) {
                    BitmapCache.put(url, bitmap) // Cache using the original URL
                } else {
                    BitmapCache.remove(url) // Ensure it's not in cache
                }
                imageView.setImageBitmap(bitmap)
                onSuccess?.invoke()
            } else {
                if (name.isNullOrEmpty()) {
                    onError?.invoke()
                } else {
                    setInitials(imageView, name)
                }
            }
        }
    }

    /**
     * Decodes a bitmap from a local file.
     */
    private fun loadImageFromFile(file: File, applyCircleCrop: Boolean): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                if (applyCircleCrop) cropToCircle(bitmap) else bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("CustomImageLoader", "Error loading image from file: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Decodes a bitmap from a content or file URI.
     */
    private fun loadLocalImageFromUri(context: Context, uri: Uri, applyCircleCrop: Boolean): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                if (applyCircleCrop) cropToCircle(bitmap) else bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("CustomImageLoader", "Error loading image from URI: $uri", e)
            null
        }
    }

    /**
     * A simple in-memory cache for bitmaps.
     */
    object BitmapCache {
        private val cache = mutableMapOf<String, Bitmap>()

        fun get(url: String): Bitmap? = cache[url]

        fun put(url: String, bitmap: Bitmap) {
            cache[url] = bitmap
        }

        fun remove(url: String) {
            cache.remove(url)
        }

        fun clear() {
            cache.clear()
        }
    }

    /**
     * Crops a bitmap into a circle.
     */
    private fun cropToCircle(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true

        val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rectF, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(squareBitmap, 0f, 0f, paint)

        // It's good practice to recycle the intermediate bitmap if it's not the original
        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }

        return output
    }

    private fun getInitials(name: String?): String {
        if (name.isNullOrEmpty()) return "N/A"
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return parts.firstOrNull()?.take(1)?.uppercase() + (parts.getOrNull(1)?.take(1)?.uppercase() ?: "")
    }

    private fun setInitials(imageView: ImageView, name: String?) {
        val initials = getInitials(name)
        imageView.setImageBitmap(createInitialsBitmap(initials))
    }

    private fun createInitialsBitmap(initials: String): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 100f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER

        val width = 200
        val height = 200

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.GRAY) // Placeholder background color for initials

        val xPos = (canvas.width / 2).toFloat()
        val yPos = ((canvas.height / 2) - ((paint.descent() + paint.ascent()) / 2))

        canvas.drawText(initials, xPos, yPos, paint)

        return bitmap
    }

    /**
     * Public suspend function to fetch a bitmap from a URL, useful for non-ImageView targets.
     */
    suspend fun getBitmapFromUrl(url: String, applyCircleCrop: Boolean = false): Bitmap? {
        return actualLoadImageFromUrl(url, applyCircleCrop)
    }

    /**
     * The core logic for downloading and decoding a bitmap from a network URL.
     */
    private suspend fun actualLoadImageFromUrl(url: String, applyCircleCrop: Boolean): Bitmap? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000 // 15 seconds
                connection.readTimeout = 15000    // 15 seconds
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                connection.setRequestProperty("Pragma", "no-cache")
                connection.setRequestProperty("Expires", "0")
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        if (applyCircleCrop) cropToCircle(bitmap) else bitmap
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("CustomImageLoader", "Error loading image from URL: $url", e)
                null
            } finally {
                // Ensure resources are always closed
                inputStream?.close()
                connection?.disconnect()
            }
        }
    }
}
