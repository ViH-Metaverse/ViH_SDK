package com.vihmessenger.vihchatbot.adapters

import com.vihmessenger.vihchatbot.utils.parseWireTimestamp
import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {

    // The display formats below stay on the device locale - the user reads those. The
    // incoming timestamp is a wire format and is parsed by parseWireTimestamp, which is
    // locale-independent; parsing it with the device locale silently failed for every
    // September date on en-IN/en-GB devices and stamped the message with "Today".
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val dateWithYearFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun parseDate(timestamp: String): Date {
        return parseWireTimestamp(timestamp) ?: Date() // Fall back to now if unreadable
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