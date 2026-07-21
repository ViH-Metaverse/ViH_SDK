package com.vihmessenger.vihchatbot.adapters

import android.webkit.MimeTypeMap
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileUtils {
    fun getFileNameExtensionAndType(url: String): Triple<String, String, String> {
         if (url.isBlank()) {
            return Triple("Invalid", "", "Unknown")
        }
        return try {
            val fileNameWithExtension = URL(url).path.substringAfterLast("/")
            val fileName = fileNameWithExtension.substringBeforeLast(".")
            val fileExtension = fileNameWithExtension.substringAfterLast(".", "")
            val fileType = when (fileExtension.lowercase()) {
                "pdf" -> "PDF"
                "doc", "docx" -> "Word"
                "xls", "xlsx" -> "Spreadsheet"
                "mp3", "wav", "aac", "m4a", "ogg" -> "Audio" // Added Audio
                "mp4", "mov", "avi", "mkv" -> "Video" // Added Video
                else -> "File" // More generic default
            }
            Triple(fileName, fileExtension, fileType)
        } catch (e: MalformedURLException) {
             Triple("Invalid", "", "Unknown")
        }
    }

    fun getFileNameFromUrl(url: String): String {
         return url.substring(url.lastIndexOf('/') + 1).let {
            if (it.contains("?")) it.substring(0, it.indexOf("?")) else it
        }
    }

     fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }


    fun getMimeTypeForSharing(fileExtension: String): String {
         return when (fileExtension.toLowerCase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            // Add other types if sharing them
            else -> "application/octet-stream" // General binary type
        }
    }

    fun getMimeTypeForViewing(filePathOrUrl: String): String {
        // Derive the extension from the name directly. MimeTypeMap.getFileExtensionFromUrl()
        // returns "" for names containing spaces or parentheses (e.g. "file (2).pdf"), which
        // then falls through to "*/*" and lets the wrong app (e.g. Google Photos) try to open
        // a PDF. Stripping any query string and taking the text after the last '.' is robust.
        val extension = filePathOrUrl
            .substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase(Locale.getDefault())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: guessMimeTypeFromExtension(extension)
    }

     private fun guessMimeTypeFromExtension(extension: String) : String {
         return when (extension) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
    }

    fun isFileValid(file: File): Boolean {
        return file.exists() && file.canRead() && file.length() > 0
    }
}