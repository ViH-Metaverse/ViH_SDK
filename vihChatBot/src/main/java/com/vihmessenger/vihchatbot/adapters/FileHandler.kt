package com.vihmessenger.vihchatbot.adapters

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vihmessenger.vihchatbot.listener.onItemChatClickListener
import java.io.File


class FileHandler(private val context: Context) {

    private val TAG = "FileHandler"

    private val FILE_PROVIDER_AUTHORITY = "${context.packageName}.provider"

    fun openDocument(file: File) {
        Log.d(TAG, "FILE_PROVIDER_AUTHORITY: ${FILE_PROVIDER_AUTHORITY}")
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "openDocument: File is invalid or does not exist at ${file.absolutePath}")
            Toast.makeText(context, "File is invalid or empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val fileUri: Uri? = try {
            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        } catch (e: IllegalArgumentException) {
            Log.e(
                TAG,
                "FileProvider Uri error. Check your file_paths.xml and authority string. Path: ${file.absolutePath}",
                e
            )
            Toast.makeText(
                context,
                "Configuration error: Could not generate URI for file.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val mimeType = FileUtils.getMimeTypeForViewing(file.absolutePath)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            Log.d(TAG, "Attempting to open document with URI: $fileUri, MIME: $mimeType")
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(
                TAG,
                "No application found to open file: ${file.name} with MIME type $mimeType",
                e
            )
            Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_LONG)
                .show()
        }
    }

    fun triggerVideoPlayback(file: File, listener: onItemChatClickListener) {
        if (!FileUtils.isFileValid(file)) {
            Toast.makeText(context, "Video file is not ready or invalid", Toast.LENGTH_SHORT).show()
            return
        }
        listener.onVideoClick(file) // Delegate to the main click listener
    }

//    fun triggerVideoPlayback(videoFile: File, listener: onItemChatClickListener) {
//        if (!videoFile.exists() || videoFile.length() == 0L) {
//            Log.e(
//                TAG,
//                "triggerVideoPlayback: Video file is invalid or empty at ${videoFile.absolutePath}"
//            )
//            Toast.makeText(context, "Video file is invalid or missing.", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val videoUri: Uri? = try {
//            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, videoFile)
//        } catch (e: IllegalArgumentException) {
//            Log.e(
//                TAG,
//                "FileProvider Uri error for video. Check file_paths.xml. Path: ${videoFile.absolutePath}",
//                e
//            )
//            Toast.makeText(
//                context,
//                "Configuration error: Could not generate URI for video.",
//                Toast.LENGTH_LONG
//            ).show()
//            return
//        }
//
//        val mimeType = FileUtils.getMimeTypeForViewing(videoFile.absolutePath)
//        val intent = Intent(Intent.ACTION_VIEW).apply {
//            setDataAndType(videoUri, mimeType)
//            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        }
//        try {
//            Log.d(TAG, "Attempting to play video with URI: $videoUri, MIME: $mimeType")
//            context.startActivity(intent)
//        } catch (e: ActivityNotFoundException) {
//            Log.e(
//                TAG,
//                "No application found to play video: ${videoFile.name} with MIME type $mimeType",
//                e
//            )
//            Toast.makeText(context, "No app found to play this video.", Toast.LENGTH_LONG).show()
//        }
//    }
}