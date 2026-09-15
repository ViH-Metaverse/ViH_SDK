package com.vihmessenger.vihchatbot.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Temporary files for camera capture and image upload (HISPL 12.4, CWE-377).
 *
 * Capture files used to be created in `getExternalFilesDir(DIRECTORY_PICTURES)`, which lives
 * under `/sdcard/Android/data/<host-package>/files/Pictures/`. On API 24–28 — and `minSdk` is
 * 24 — scoped storage is not enforced there, so any app holding `READ_EXTERNAL_STORAGE` can
 * read those files. They were also never deleted, so a user's camera captures accumulated on
 * the device indefinitely.
 *
 * Both problems are fixed here rather than at each call site: files are created in the app's
 * **internal** cache, which the platform sandboxes to the owning app on every API level, and
 * [sweepStale] clears anything an interrupted upload left behind.
 *
 * The directory is exposed to `FileProvider` through the `captures` `<cache-path>` entry in
 * `res/xml/file_paths.xml`, so handing the URI to the camera app still works.
 */
object CaptureFiles {

    /** Sub-directory of the internal cache that holds in-flight capture/upload files. */
    private const val DIR = "captures"

    /** Files older than this are assumed orphaned by an interrupted flow. */
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /**
     * Creates an empty capture file in internal cache storage.
     *
     * `Locale.US` is deliberate: the timestamp is a filename, not display text, and
     * `Locale.getDefault()` here produces non-ASCII digits under locales such as `ar-EG` and
     * a non-Gregorian year under `th-TH`. The same pattern caused the chat-list crash on
     * non-`en-US` English locales fixed in 0.7.7.
     */
    @JvmStatic
    fun create(context: Context, prefix: String = "JPEG_", suffix: String = ".jpg"): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File.createTempFile("${prefix}${timeStamp}_", suffix, dir)
    }

    /**
     * Best-effort delete of a single capture file once it has been uploaded (or the upload
     * failed). Never throws: a failed cleanup must not surface as a failed profile update.
     */
    @JvmStatic
    fun delete(file: File?) {
        if (file == null) return
        runCatching { if (file.exists()) file.delete() }
            .onFailure { VihLog.w(TAG, "Could not delete capture file: ${it.javaClass.simpleName}") }
    }

    /**
     * Deletes capture files older than [MAX_AGE_MS]. Call on a cheap, frequent entry point
     * (dashboard launch) so that a crash or a cancelled upload cannot leak files forever.
     * Cheap enough to run on the main thread: the directory holds at most a handful of files.
     */
    @JvmStatic
    fun sweepStale(context: Context) {
        runCatching {
            val dir = File(context.cacheDir, DIR)
            if (!dir.isDirectory) return
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) f.delete()
            }
        }.onFailure { VihLog.w(TAG, "Capture sweep failed: ${it.javaClass.simpleName}") }
    }

    private const val TAG = "CaptureFiles"
}
