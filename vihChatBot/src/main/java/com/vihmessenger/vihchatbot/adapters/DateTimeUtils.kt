package com.vihmessenger.vihchatbot.adapters

import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {

    // Assume "MMM, dd yyyy HH:mm:ss" is the consistent input format
    private val inputFormat = SimpleDateFormat("MMM, dd yyyy HH:mm:ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val dateWithYearFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun parseDate(timestamp: String): Date {
        return try {
            inputFormat.parse(timestamp) ?: Date()
        } catch (e: Exception) {
            // Log error
            e.printStackTrace()
            Date() // Return current date as fallback
        }
    }

    fun parseTimestampToTime(timestamp: String): String {
        return try {
            val date = parseDate(timestamp)
            timeFormat.format(date)
        } catch (e: Exception) {
            e.printStackTrace()
            "" // Return empty on error
        }
    }

     fun getFormattedDateHeader(timestamp: String): String {
        return try {
            val date = parseDate(timestamp)
            val calendar = Calendar.getInstance().apply { time = date }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

            when {
                isSameDay(calendar, today) -> "Today"
                isSameDay(calendar, yesterday) -> "Yesterday"
                isSameYear(calendar, today) -> dateOnlyFormat.format(date)
                else -> dateWithYearFormat.format(date)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Unknown Date"
        }
    }

     fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

     fun isSameYear(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }
}