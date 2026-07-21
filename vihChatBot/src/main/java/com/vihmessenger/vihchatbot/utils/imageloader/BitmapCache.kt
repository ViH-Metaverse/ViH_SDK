package com.vihmessenger.vihchatbot.utils.imageloader

import android.graphics.Bitmap
import android.util.LruCache

object BitmapCache {
    private val cache: LruCache<String, Bitmap> = LruCache(50) // You can adjust the cache size as needed

    // Get a bitmap from the cache
    fun get(url: String): Bitmap? {
        return cache.get(url)
    }

    // Put a bitmap in the cache
    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}