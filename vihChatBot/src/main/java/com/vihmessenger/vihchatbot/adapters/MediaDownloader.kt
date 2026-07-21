package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class MediaDownloader(private val context: Context) {

    private val TAG_DOWNLOADER = "MediaDownloader"

    suspend fun downloadFileWithProgress(
        fileUrl: String,
        destinationFile: File,
        onProgress: (Int) -> Unit,
        onComplete: (success: Boolean, file: File?, isCancelled: Boolean) -> Unit
    ) {
        try {
            if (destinationFile.exists() && destinationFile.length() > 0) {
                Log.i(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] File already exists and has size > 0 at ${destinationFile.absolutePath}. Skipping download."
                )
                withContext(Dispatchers.Main) {
                    onComplete(true, destinationFile, false)
                }
                return
            } else if (destinationFile.exists() && destinationFile.length() == 0L) {
                Log.w(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] File exists but is empty at ${destinationFile.absolutePath}. Attempting to delete and re-download."
                )
                destinationFile.delete()
            }
        } catch (e: Exception) {
            Log.e(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] Error checking existing destination file: ${e.message}",
                e
            )
        }

        var successful = false
        var isCancelled = false
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.temp")
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: OutputStream? = null

        Log.d(
            TAG_DOWNLOADER,
            "[File: ${destinationFile.name}] Initializing download. Temp file: ${tempFile.absolutePath}"
        )

        try {
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    Log.d(
                        TAG_DOWNLOADER,
                        "[File: ${destinationFile.name}] Deleted existing temp file: ${tempFile.absolutePath}"
                    )
                } else {
                    Log.w(
                        TAG_DOWNLOADER,
                        "[File: ${destinationFile.name}] Failed to delete existing temp file: ${tempFile.absolutePath}"
                    )
                }
            }

            val parentDir = destinationFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                Log.d(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] Destination directory ${parentDir.absolutePath} does not exist. Creating."
                )
                if (!parentDir.mkdirs()) {
                    throw IOException("Failed to create destination directory: ${parentDir.absolutePath}")
                }
            }
            val url = URL(fileUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            Log.d(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] Attempting to download from URL: $fileUrl"
            )
            Log.d(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] Attempting to save to temp file: ${tempFile.absolutePath}"
            )

            connection.connect()

            if (!currentCoroutineContext().isActive) {
                isCancelled = true
                throw CancellationException("Download cancelled before starting.")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            val fileLength = connection.contentLength
            var downloadedBytes = 0L

            input = connection.inputStream
            output = FileOutputStream(tempFile)

            val buffer = ByteArray(4096)
            var bytesRead: Int

            Log.d(TAG_DOWNLOADER, "[File: ${destinationFile.name}] Starting download from $fileUrl")

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (!currentCoroutineContext().isActive) {
                    isCancelled = true
                    Log.w(
                        TAG_DOWNLOADER,
                        "[File: ${destinationFile.name}] Cancellation detected in download loop."
                    )
                    throw CancellationException("Download cancelled during operation.")
                }
                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                if (fileLength > 0) {
                    val progress = ((downloadedBytes * 100) / fileLength).toInt()
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }
            output.flush()
            Log.d(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] Download loop completed. Downloaded $downloadedBytes bytes."
            )

            try {
                output.close()
                output = null
                Log.d(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] Temp file output stream closed."
                )
            } catch (e: IOException) {
                Log.e(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] Error closing temp file output stream: ${e.message}",
                    e
                )
                throw e
            }
            try {
                input.close()
                input = null
                Log.d(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] Network input stream closed."
                )
            } catch (e: IOException) {
                Log.e(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] Error closing network input stream: ${e.message}",
                    e
                )
            }

            if (fileLength <= 0 || downloadedBytes == fileLength.toLong()) {
                if (downloadedBytes > 0) {
                    Log.d(
                        TAG_DOWNLOADER,
                        "[File: ${destinationFile.name}] Download appears complete. Proceeding with file finalization."
                    )
                    if (destinationFile.exists()) {
                        Log.w(
                            TAG_DOWNLOADER,
                            "[File: ${destinationFile.name}] Destination file ${destinationFile.absolutePath} unexpectedly exists before rename. Attempting to delete."
                        )
                        if (!destinationFile.delete()) {
                            Log.e(
                                TAG_DOWNLOADER,
                                "[File: ${destinationFile.name}] Failed to delete unexpectedly existing destination file. Rename/copy will likely fail if it cannot overwrite."
                            )
                        } else {
                            Log.d(
                                TAG_DOWNLOADER,
                                "[File: ${destinationFile.name}] Successfully deleted unexpectedly existing destination file."
                            )
                        }
                    }

                    if (tempFile.renameTo(destinationFile)) {
                        successful = true
                        Log.i(
                            TAG_DOWNLOADER,
                            "[File: ${destinationFile.name}] Successfully renamed temp file to ${destinationFile.absolutePath}"
                        )
                    } else {
                        Log.w(
                            TAG_DOWNLOADER,
                            "[File: ${destinationFile.name}] Failed to rename temp file to ${destinationFile.absolutePath}. Attempting copy fallback."
                        )
                        try {
                            tempFile.copyTo(destinationFile, overwrite = true)
                            successful = true
                            Log.i(
                                TAG_DOWNLOADER,
                                "[File: ${destinationFile.name}] Successfully copied temp file to ${destinationFile.absolutePath} (fallback)."
                            )
                            if (!tempFile.delete()) {
                                Log.w(
                                    TAG_DOWNLOADER,
                                    "[File: ${destinationFile.name}] Failed to delete temp file after successful copy fallback."
                                )
                            }
                        } catch (copyEx: Exception) {
                            Log.e(
                                TAG_DOWNLOADER,
                                "[File: ${destinationFile.name}] Fallback copy also failed: ${copyEx.message}",
                                copyEx
                            )
                            throw IOException(
                                "Failed to rename temp file and fallback copy also failed. Temp: ${tempFile.absolutePath}, Dest: ${destinationFile.absolutePath}",
                                copyEx
                            )
                        }
                    }
                } else if (fileLength > 0) {
                    throw IOException("Download incomplete. Expected $fileLength bytes but got $downloadedBytes bytes.")
                } else {
                    throw IOException("Download failed. No data received and content length unknown.")
                }
            } else {
                throw IOException("Download incomplete. Expected $fileLength bytes but got $downloadedBytes bytes.")
            }

        } catch (e: CancellationException) {
            isCancelled = true
            successful = false
            Log.w(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] Download cancelled: ${e.message}",
                e
            )
        } catch (e: IOException) {
            successful = false
            Log.e(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] IOException during download: ${e.message}",
                e
            )
        } catch (e: Exception) {
            successful = false
            Log.e(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] Generic error during download. Type: ${e::class.java.simpleName}: ${e.message}",
                e
            )
        } finally {
            try {
                output?.close()
            } catch (e: IOException) {
                Log.e(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] Error closing output stream in finally: ${e.message}"
                )
            }
            try {
                input?.close()
            } catch (e: IOException) {
                Log.e(
                    TAG_DOWNLOADER,
                    "[File: ${destinationFile.name}] Error closing input stream in finally: ${e.message}"
                )
            }
            connection?.disconnect()
            Log.d(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] Connection disconnected in finally block."
            )

            if (isCancelled || !successful) {
                if (tempFile.exists()) {
                    Log.d(
                        TAG_DOWNLOADER,
                        "[File: ${destinationFile.name}] Download not successful or cancelled. Deleting temp file: ${tempFile.absolutePath}"
                    )
                    if (!tempFile.delete()) {
                        Log.w(
                            TAG_DOWNLOADER,
                            "[File: ${destinationFile.name}] Failed to delete temp file in finally block: ${tempFile.absolutePath}"
                        )
                    }
                }
            }

            Log.d(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] In finally block. isCancelled: $isCancelled, successful: $successful. Calling onComplete."
            )
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) {
                    Log.d(
                        TAG_DOWNLOADER,
                        "[File: ${destinationFile.name}] Switched to Main (NonCancellable) for onComplete. Success: $successful, Cancelled: $isCancelled, File: ${if (successful) destinationFile.name else null}"
                    )
                    onComplete(successful, if (successful) destinationFile else null, isCancelled)
                }
            }
            Log.d(
                TAG_DOWNLOADER,
                "[File: ${destinationFile.name}] After onComplete call from finally block."
            )
        }
    }
}