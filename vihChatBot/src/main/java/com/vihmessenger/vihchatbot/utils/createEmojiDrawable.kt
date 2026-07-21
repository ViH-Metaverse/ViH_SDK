package com.vihmessenger.vihchatbot.utils
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.emoji2.text.EmojiCompat

fun createEmojiDrawable(context: Context, emoji: String, sizeInDp: Float): BitmapDrawable {
    val sizeInPx = (sizeInDp * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(sizeInPx, sizeInPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        textSize = sizeInPx * 0.6f // Adjust text size relative to the drawable size
        typeface = Typeface.DEFAULT
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.BLACK // Set emoji text color
    }

    // Process the emoji with EmojiCompat for consistent rendering
    val processedEmoji = EmojiCompat.get().process(emoji)?.toString() ?: emoji // Convert to String

    // Center the emoji text in the canvas
    val xPos = canvas.width / 2f
    val yPos = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2)
    canvas.drawText(processedEmoji, xPos, yPos, paint)

    return BitmapDrawable(context.resources, bitmap)
}


