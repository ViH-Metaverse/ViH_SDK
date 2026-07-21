package com.vihmessenger.vihchatbot.adapters

import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.data.model.CpassJsonModel
import com.vihmessenger.vihchatbot.data.model.MessageModel
import com.vihmessenger.vihchatbot.databinding.ItemChatTemplateBinding
import com.vihmessenger.vihchatbot.listener.onItemChatClickListener
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager
import com.vihmessenger.vihchatbot.utils.getProfileData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.linc.amplituda.Amplituda
import com.masoudss.lib.WaveformSeekBar
import java.io.File

class TemplateMessageViewHolder(
    val binding: ItemChatTemplateBinding,
    private val context: Context,
    private val listener: onItemChatClickListener,
    private val channelName: String,
    private val channelLogo: String?,
    private val mediaDownloader: MediaDownloader,
    val audioPlayerManager: AudioPlayerManager,
    private val fileHandler: FileHandler
) : RecyclerView.ViewHolder(binding.root), DynamicThemeManager.ThemeChangeListener {

    private var currentDownloadJob: Job? = null
    private var currentVideoDownloadJob: Job? = null
    private var currentAudioDownloadJob: Job? = null
    private val TAG = "TemplateViewHolder"

    @RequiresApi(Build.VERSION_CODES.O)
    fun bind(message: MessageModel) {
        if (binding.rlTemplateDoc.tag == "doc_download_active" &&
            (message.cpaas_json?.is_header_doc != "1" || message.cpaas_json?.doc_url.isNullOrEmpty() ||
                    FileUtils.getFileNameFromUrl(message.cpaas_json?.doc_url ?: "") != binding.tvFileName.tag?.toString())
        ) {
            currentDownloadJob?.cancel("Document view rebound, cancelling old download.")
            currentDownloadJob = null
        }
        if (binding.rlVideoLayout.tag == "video_download_active" &&
            (message.cpaas_json?.is_header_vid != "1" || message.cpaas_json?.video_url.isNullOrEmpty() ||
                    FileUtils.getFileNameFromUrl(message.cpaas_json?.video_url ?: "") != binding.rlVideoLayout.getTag(
                R.id.tag_video_filename
            )?.toString())
        ) {
            currentVideoDownloadJob?.cancel("Video view rebound, cancelling old video download.")
            currentVideoDownloadJob = null
        }
        if (binding.lyAudioTemplate.audioDownloadTemplate.tag == "audio_download_active" &&
            (message.cpaas_json?.is_header_aud != "1" || message.cpaas_json?.audio_url.isNullOrEmpty() ||
                    FileUtils.getFileNameFromUrl(message.cpaas_json?.audio_url ?: "") != binding.lyAudioTemplate.tvAudioFileName.tag?.toString())
        ) {
            currentAudioDownloadJob?.cancel("Audio view rebound, cancelling old download.")
            currentAudioDownloadJob = null
        }


        binding.apply {
            rlImageLayout.visibility = View.GONE
            rlTemplateDoc.visibility = View.GONE
            rlVideoLayout.visibility = View.GONE
            lyAudioTemplate.clAudioTemplate.visibility = View.GONE
            vpProducts.visibility = View.GONE
            viewPagerIndicator.visibility = View.GONE
            rvTemplateButton.visibility = View.GONE
            tvFooter.visibility = View.GONE

            flProgressCancel.visibility = View.GONE // For document
            flVideoProgressCancel.visibility = View.GONE // For video
            lyAudioTemplate.flAudioProgressCancel.visibility = View.GONE // For audio


            val cpaasData = message.cpaas_json ?: return
            tvHeader.text =
                HtmlCompat.fromHtml(cpaasData.msg ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY)
            tvChatTemplateTime.text = DateTimeUtils.parseTimestampToTime(message.created_at)

            if (cpaasData.is_header_img == "1" && !cpaasData.image_url.isNullOrEmpty()) {
                rlImageLayout.visibility = View.VISIBLE
                CustomImageLoader.loadImageView(
                    imageView = ivTemplateHeaderImage,
                    url = cpaasData.image_url,
                    name = getProfileData()?.full_name
                        ?: "NA",
                    onError = { ivTemplateHeaderImage.setImageResource(R.drawable.placeholder) }
                )
                rlImageLayout.setOnClickListener { listener.onImageClick(cpaasData.image_url ?: "") }
            }

            if (cpaasData.is_header_doc == "1" && !cpaasData.doc_url.isNullOrEmpty()) {
                binding.rlTemplateDoc.visibility = View.VISIBLE
                binding.tvFileName.tag = FileUtils.getFileNameFromUrl(cpaasData.doc_url ?: "")
                setupDocumentView(cpaasData)
            } else {
                binding.rlTemplateDoc.visibility = View.GONE
                if (binding.rlTemplateDoc.tag == "doc_download_active") {
                    currentDownloadJob?.cancel("Document view hidden or changed.")
                    currentDownloadJob = null
                    binding.flProgressCancel.visibility = View.GONE
                    binding.ivDownload.visibility = View.VISIBLE
                    binding.rlTemplateDoc.tag = null
                }
            }

            if (cpaasData.is_header_vid == "1" && !cpaasData.video_url.isNullOrEmpty()) {
                rlVideoLayout.visibility = View.VISIBLE
                binding.rlVideoLayout.setTag(
                    R.id.tag_video_filename,
                    FileUtils.getFileNameFromUrl(cpaasData.video_url ?: "")
                )
                setupVideoView(cpaasData)
            } else {
                binding.rlVideoLayout.visibility = View.GONE
                if (binding.rlVideoLayout.tag == "video_download_active") {
                    currentVideoDownloadJob?.cancel("Video view hidden or changed.")
                    currentVideoDownloadJob = null
                    binding.flVideoProgressCancel.visibility = View.GONE
                    binding.buttonDownload.visibility = View.VISIBLE // Reset to download state
                    binding.imgPlay.visibility = View.GONE
                    binding.rlVideoLayout.tag = null
                }
            }

            if (cpaasData.is_header_aud == "1" && !cpaasData.audio_url.isNullOrEmpty()) {
                lyAudioTemplate.clAudioTemplate.visibility = View.VISIBLE
                applyThemeColors(DynamicThemeManager.getPrimaryColor())
                setupAudioView(cpaasData)
            } else {
                lyAudioTemplate.clAudioTemplate.visibility = View.GONE
                audioPlayerManager.cleanup()
            }

            cpaasData.product?.takeIf { it.isNotEmpty() }?.let { productList ->
                vpProducts.visibility = View.VISIBLE
                viewPagerIndicator.visibility = View.VISIBLE
                val adapter = ProductViewPagerAdapter(context, productList)
                vpProducts.adapter = adapter
                viewPagerIndicator.initWithViewPager(vpProducts)
            }

            cpaasData.button?.takeIf { it.isNotEmpty() }?.let { buttonList ->
                rvTemplateButton.visibility = View.VISIBLE
                rvTemplateButton.layoutManager = LinearLayoutManager(context)
                rvTemplateButton.setHasFixedSize(false)
                val buttonAdapter = ChatTemplateButtonAdapter(context, listener)
                rvTemplateButton.adapter = buttonAdapter
                buttonAdapter.insertButtonList(buttonList)
            }

            cpaasData.footer?.takeIf { it.isNotEmpty() }?.let { footerText ->
                tvFooter.visibility = View.VISIBLE
                tvFooter.text = footerText
            }
        }
    }

    private fun updateThumbnailAppearance(imageView: ImageView, isDownloaded: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!isDownloaded) {
                val blurEffect = RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.MIRROR)
                imageView.setRenderEffect(blurEffect)
            } else {
                imageView.setRenderEffect(null)
            }
        } else {
            imageView.alpha = if (!isDownloaded) 0.5f else 1.0f
        }
    }

    private fun setupDocumentView(cpaasData: CpassJsonModel) {
        binding.apply {
            val docUrl = cpaasData.doc_url ?: ""
            val localDocFileNameForLogging = FileUtils.getFileNameFromUrl(docUrl)

            // MODIFICATION: Use channel-specific directory for documents
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val channelSpecificDir = File(baseDir, channelName)
            channelSpecificDir.mkdirs() // This ensures the "channelName" directory exists
            val localFile = File(channelSpecificDir, FileUtils.getFileNameFromUrl(docUrl))


            val (fileName, fileExtension, fileType) = FileUtils.getFileNameExtensionAndType(docUrl)
            CustomImageLoader.loadImageView(
                imageView = ivTemplateDocument,
                url = cpaasData.thumbnail,
                onError = { ivTemplateDocument.setImageResource(R.drawable.placeholder) }
            )
            val displayedFileName = if (fileName.length > 12) fileName.take(12) + "…" else fileName
            tvFileName.text = "$displayedFileName.$fileExtension"
            val pageInfo = " "
            val sizeInfo =
                cpaasData.media_size?.toLongOrNull()?.let { FileUtils.formatFileSize(it) } ?: ""
            val fileTypeStr = fileType.takeIf { it != "Unknown" } ?: fileExtension.uppercase()
            tvFileDetails.text = listOf(pageInfo, sizeInfo, fileTypeStr).filter { it.isNotBlank() }
                .joinToString(" · ")

            ivDocType.setImageResource(
                when (fileExtension.lowercase()) {
                    "pdf" -> R.drawable.ic_pdf; "doc", "docx" -> R.drawable.ic_doc
                    "xls", "xlsx" -> R.drawable.ic_sheet; else -> R.drawable.ic_doc
                }
            )

            val isFileConsideredValid =
                localFile.exists() && localFile.isFile && localFile.length() > 0

            if (isFileConsideredValid) {
                ivDownload.visibility = View.GONE
                flProgressCancel.visibility = View.GONE
                rlTemplateDoc.tag = null
            } else {
                ivDownload.visibility = View.VISIBLE
                flProgressCancel.visibility = View.GONE
            }

            rlTemplateDoc.setOnClickListener {
                if (localFile.exists() && localFile.isFile && localFile.length() > 0) {
                    fileHandler.openDocument(localFile)
                } else {
                    Toast.makeText(
                        context,
                        "Document not downloaded. Please use the download icon.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            ivDownload.setOnClickListener {
                handleDocumentDownload(docUrl, localFile)
            }

            ivCancelDownload.setOnClickListener {
                Log.d(
                    TAG,
                    "[DOC: $localDocFileNameForLogging] ivCancelDownload clicked. Job: $currentDownloadJob"
                )
                currentDownloadJob?.cancel(kotlinx.coroutines.CancellationException("User cancelled document download for $localDocFileNameForLogging"))
            }
        }
    }

    private fun handleDocumentDownload(docUrl: String, destinationFile: File) {
        val localFileNameForLogging = FileUtils.getFileNameFromUrl(docUrl)
        currentDownloadJob?.cancel("New document download requested, cancelling previous.")

        binding.ivDownload.visibility = View.GONE
        binding.flProgressCancel.visibility = View.VISIBLE
        binding.circularProgressBar.progress = 0f
        binding.circularProgressBar.progressMax = 100f
        binding.circularProgressBar.progressBarColor = DynamicThemeManager.getPrimaryColor()
        binding.rlTemplateDoc.tag = "doc_download_active"

        currentDownloadJob = CoroutineScope(Dispatchers.IO).launch {
            mediaDownloader.downloadFileWithProgress(docUrl, destinationFile,
                onProgress = { progress ->
                    if (isActive) binding.circularProgressBar.progress = progress.toFloat()
                },
                onComplete = { success, file, isCancelled ->
                    Log.i(
                        TAG,
                        "[DOC: $localFileNameForLogging] onComplete. Cancelled: $isCancelled, Success: $success"
                    )
                    // This block is already on Main thread due to MediaDownloader's implementation
                    binding.flProgressCancel.visibility = View.GONE
                    binding.rlTemplateDoc.tag = null // Clear download active tag

                    if (isCancelled) {
                        binding.ivDownload.visibility = View.VISIBLE
                        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                    } else if (success && file != null) {
                        binding.ivDownload.visibility = View.GONE
                        Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show()
                        fileHandler.openDocument(file)
                    } else {
                        binding.ivDownload.visibility = View.VISIBLE
                        Toast.makeText(context, "Download failed", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
        currentDownloadJob?.invokeOnCompletion { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) {
                Log.d(
                    TAG,
                    "[DOC: $localFileNameForLogging] Doc Job Cancelled: ${throwable.message}"
                )
            }
        }
    }

    fun cleanupDocumentDownload() {
        val docName = binding.tvFileName.tag?.toString() ?: "UnknownDoc"
        Log.d(TAG, "cleanupDocumentDownload called for $docName. Job: $currentDownloadJob")
        currentDownloadJob?.cancel("ViewHolder document download cleanup.")
        currentDownloadJob = null
        if (binding.root.isAttachedToWindow) {
            binding.flProgressCancel.visibility = View.GONE
        }
        binding.rlTemplateDoc.tag = null
    }

    private fun setupVideoView(cpaasData: CpassJsonModel) {
        binding.apply {
            val videoUrl = cpaasData.video_url ?: ""
            val videoFileNameForLogging = FileUtils.getFileNameFromUrl(videoUrl)

            // MODIFICATION: Use channel-specific directory for videos
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val channelSpecificDir = File(baseDir, channelName)
            channelSpecificDir.mkdirs() // Ensures the directory exists
            val localVideoFile = File(channelSpecificDir, videoFileNameForLogging)

            val isVideoDownloaded = FileUtils.isFileValid(localVideoFile)
            updateThumbnailAppearance(ivTemplateVideo, isVideoDownloaded)

            CustomImageLoader.loadImageView(
                imageView = ivTemplateVideo,
                url = cpaasData.thumbnail,
                onError = { ivTemplateVideo.setImageResource(R.drawable.video_placeholder) }
            )

            val sizeInfo =
                cpaasData.media_size?.toLongOrNull()?.let { FileUtils.formatFileSize(it) }
                    ?: "Video"
            buttonDownload.text = sizeInfo

            if (isVideoDownloaded) {
                buttonDownload.visibility = View.GONE
                flVideoProgressCancel.visibility = View.GONE
                imgPlay.visibility = View.VISIBLE
                rlVideoLayout.tag = null
            } else {
                buttonDownload.visibility = View.VISIBLE
                flVideoProgressCancel.visibility = View.GONE
                imgPlay.visibility = View.GONE
            }

            ivTemplateVideo.setOnClickListener {
                if (FileUtils.isFileValid(localVideoFile)) {
                    fileHandler.triggerVideoPlayback(localVideoFile, listener)
                } else {
                    Toast.makeText(context, "Video not downloaded yet.", Toast.LENGTH_SHORT).show()
                }
            }


            buttonDownload.setOnClickListener {
                currentVideoDownloadJob?.cancel("New video download requested, cancelling previous.")

                buttonDownload.visibility = View.GONE
                imgPlay.visibility = View.GONE
                flVideoProgressCancel.visibility = View.VISIBLE
                videoCircularProgressBar.progress = 0f
                videoCircularProgressBar.progressMax = 100f
                rlVideoLayout.tag = "video_download_active" // Mark that a download is active
                val primaryColor = DynamicThemeManager.getPrimaryColor() // Get the color
                videoCircularProgressBar.progressBarColor = primaryColor

                ImageViewCompat.setImageTintList(
                    ivCancelVideoDownload,
                    ColorStateList.valueOf(primaryColor)
                )
                currentVideoDownloadJob = CoroutineScope(Dispatchers.IO).launch {
                    mediaDownloader.downloadFileWithProgress(videoUrl, localVideoFile,
                        onProgress = { progress ->
                            if (isActive) videoCircularProgressBar.progress = progress.toFloat()
                        },
                        onComplete = { success, file, isCancelled ->
                            Log.i(
                                TAG,
                                "[VID: $videoFileNameForLogging] onComplete. Cancelled: $isCancelled, Success: $success"
                            )
                            flVideoProgressCancel.visibility = View.GONE
                            rlVideoLayout.tag = null // Clear download active tag

                            updateThumbnailAppearance(
                                ivTemplateVideo,
                                success && file != null && FileUtils.isFileValid(file)
                            )

                            if (isCancelled) {
                                buttonDownload.visibility = View.VISIBLE
                                imgPlay.visibility = View.GONE
                            } else if (success && file != null) {
                                imgPlay.visibility = View.VISIBLE
                                buttonDownload.visibility = View.GONE
                            } else {
                                buttonDownload.visibility = View.VISIBLE
                                imgPlay.visibility = View.GONE
                                Toast.makeText(context, "Video download failed", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    )
                }
                currentVideoDownloadJob?.invokeOnCompletion { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) {
                        Log.d(
                            TAG,
                            "[VID: $videoFileNameForLogging] Video Job Cancelled: ${throwable.message}"
                        )
                    }
                }
            }

            ivCancelVideoDownload.setOnClickListener {
                Log.d(
                    TAG,
                    "[VID: $videoFileNameForLogging] ivCancelVideoDownload clicked. Job: $currentVideoDownloadJob"
                )
                currentVideoDownloadJob?.cancel(kotlinx.coroutines.CancellationException("User cancelled video download for $videoFileNameForLogging"))
            }

            imgPlay.setOnClickListener {
                if (FileUtils.isFileValid(localVideoFile)) {
                    fileHandler.triggerVideoPlayback(localVideoFile, listener)
                } else {
                    Toast.makeText(
                        context,
                        "Video file not available. Please download again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    imgPlay.visibility = View.GONE
                    buttonDownload.visibility = View.VISIBLE // Allow re-download
                    flVideoProgressCancel.visibility = View.GONE
                }
            }
        }
    }

    fun cleanupVideoDownload() {
        val videoName =
            binding.rlVideoLayout.getTag(R.id.tag_video_filename)?.toString() ?: "UnknownVideo"
        Log.d(TAG, "cleanupVideoDownload called for $videoName. Job: $currentVideoDownloadJob")
        currentVideoDownloadJob?.cancel("ViewHolder video download cleanup.")
        currentVideoDownloadJob = null
        if (binding.root.isAttachedToWindow) {
            binding.flVideoProgressCancel.visibility = View.GONE
        }
        binding.rlVideoLayout.tag = null
        binding.rlVideoLayout.setTag(R.id.tag_video_filename, null)
    }


    private fun setupAudioView(cpaasData: CpassJsonModel) {
        applyThemeColors(DynamicThemeManager.getPrimaryColor())
        binding.lyAudioTemplate.apply {
            val audioUrl = cpaasData.audio_url ?: ""
            val (fileName, fileExtension, fileType) = FileUtils.getFileNameExtensionAndType(audioUrl)

            // MODIFICATION: Use channel-specific directory for audio
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val channelSpecificDir = File(baseDir, channelName)
            channelSpecificDir.mkdirs() // Ensures the directory exists

            if (baseDir == null) {
                Log.e(TAG, "External music directory is null. Cannot proceed with audio setup.")
                Toast.makeText(context, "Cannot access storage for audio", Toast.LENGTH_SHORT)
                    .show()
                audioDownloadTemplate.visibility = View.GONE
                clAudioPlayTemplate.visibility = View.GONE
                return@apply
            }

            val localAudioFile = File(channelSpecificDir, "$fileName.$fileExtension")
            tvAudioFileName.tag = fileName

            CustomImageLoader.loadImageView(
                imageView = ivEndIcon, url = channelLogo ?: "", name = channelName ?: "NA",
                onError = { ivEndIcon.setImageResource(R.drawable.placeholder) }
            )

            CustomImageLoader.loadImageView(
                imageView = ivEndImg, url = channelLogo ?: "", name = channelName ?: "NA",
                onError = { ivEndIcon.setImageResource(R.drawable.placeholder) }
            )

            val fullFileName = "$fileName.$fileExtension"
            val maxLength = 20
            val shortenedFileName = if (fullFileName.length > maxLength) {
                val extensionLength = fileExtension.length + 1
                val maxNameLength = maxLength - extensionLength - 1
                if (maxNameLength > 3) {
                    fullFileName.take(maxNameLength) + "…" + fullFileName.takeLast(extensionLength)
                } else {
                    "…" + fullFileName.takeLast(maxLength - 1)
                }
            } else fullFileName
            tvAudioFileName.text = shortenedFileName

            val sizeInfo =
                cpaasData.media_size?.toLongOrNull()?.let { FileUtils.formatFileSize(it) } ?: "0 B"
            val fileTypeStr = fileType.takeIf { it != "Unknown" } ?: fileExtension.uppercase()
            tvAudioFileDetails.text = "$sizeInfo · $fileTypeStr"

            if (FileUtils.isFileValid(localAudioFile)) {
                audioDownloadTemplate.visibility = View.GONE
                clAudioPlayTemplate.visibility = View.VISIBLE
                try {
                    Log.d(TAG, "Setting samples from: ${localAudioFile.absolutePath}")
                    applyWaveformSamples(waveformSeekBar, localAudioFile)
                    waveformSeekBar.waveProgressColor = DynamicThemeManager.getPrimaryColor()
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting samples for WaveformSeekBar", e)
                }
                audioPlayerManager.initialize(
                    localAudioFile.absolutePath, waveformSeekBar, ivPlay, tvDuration,
                    R.drawable.ic_play_audio, R.drawable.ic_playing_audio
                )
            } else {
                audioDownloadTemplate.visibility = View.VISIBLE
                clAudioPlayTemplate.visibility = View.GONE
                waveformSeekBar.setSampleFrom(intArrayOf())
                waveformSeekBar.progress = 0f
                applyThemeColors(DynamicThemeManager.getPrimaryColor())
                audioPlayerManager.cleanup()
            }

            ivAudioDownload.setOnClickListener {
                handleAudioDownload(audioUrl, localAudioFile)
            }

            ivCancelAudioDownload.setOnClickListener {
                val audioFileNameForLogging = FileUtils.getFileNameFromUrl(audioUrl)
                Log.d(
                    TAG,
                    "[AUD: $audioFileNameForLogging] ivCancelAudioDownload clicked. Job: $currentAudioDownloadJob"
                )
                currentAudioDownloadJob?.cancel(kotlinx.coroutines.CancellationException("User cancelled audio download for $audioFileNameForLogging"))
            }
        }
    }

    /**
     * Decode [file] into waveform samples with Amplituda (2.3.1 — 16 KB-aligned native
     * libs) off the main thread, then apply them via setSampleFrom(int[]). We call
     * Amplituda directly instead of waveformSeekBar.setSampleFrom(File): the bundled
     * decoder in waveformSeekBar 5.0.2 targets the old linc.com.amplituda package that
     * 2.3.1 renamed to com.linc.amplituda.
     */
    private fun applyWaveformSamples(seekBar: WaveformSeekBar, file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            val samples = try {
                Amplituda(context).processAudio(file).get().amplitudesAsList().toIntArray()
            } catch (e: Exception) {
                Log.e(TAG, "Amplituda decode failed for ${file.name}", e)
                intArrayOf()
            }
            withContext(Dispatchers.Main) {
                try {
                    seekBar.setSampleFrom(samples)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply waveform samples", e)
                }
            }
        }
    }

    private fun handleAudioDownload(audioUrl: String, destinationFile: File) {
        val localFileNameForLogging = FileUtils.getFileNameFromUrl(audioUrl)
        currentAudioDownloadJob?.cancel("New audio download requested, cancelling previous.")

        binding.lyAudioTemplate.ivAudioDownload.visibility = View.GONE
        binding.lyAudioTemplate.flAudioProgressCancel.visibility = View.VISIBLE
        binding.lyAudioTemplate.audioCircularProgressBar.progress = 0f
        binding.lyAudioTemplate.audioCircularProgressBar.progressMax = 100f
        binding.lyAudioTemplate.audioCircularProgressBar.progressBarColor =
            DynamicThemeManager.getPrimaryColor()
        binding.lyAudioTemplate.audioDownloadTemplate.tag = "audio_download_active"

        currentAudioDownloadJob = CoroutineScope(Dispatchers.IO).launch {
            mediaDownloader.downloadFileWithProgress(audioUrl, destinationFile,
                onProgress = { progress ->
                    if (isActive) binding.lyAudioTemplate.audioCircularProgressBar.progress =
                        progress.toFloat()
                },
                onComplete = { success, file, isCancelled ->
                    Log.i(
                        TAG,
                        "[AUD: $localFileNameForLogging] onComplete. Cancelled: $isCancelled, Success: $success"
                    )
                    binding.lyAudioTemplate.flAudioProgressCancel.visibility = View.GONE
                    binding.lyAudioTemplate.audioDownloadTemplate.tag = null

                    if (isCancelled) {
                        binding.lyAudioTemplate.ivAudioDownload.visibility = View.VISIBLE
                        Toast.makeText(context, "Audio download cancelled", Toast.LENGTH_SHORT)
                            .show()
                    } else if (success && file != null) {
                        binding.lyAudioTemplate.ivAudioDownload.visibility = View.GONE
                        binding.lyAudioTemplate.audioDownloadTemplate.visibility = View.GONE
                        binding.lyAudioTemplate.clAudioPlayTemplate.visibility = View.VISIBLE
                        try {
                            Log.d(TAG, "Setting samples from: ${file.absolutePath}")
                            applyWaveformSamples(binding.lyAudioTemplate.waveformSeekBar, file)
                            binding.lyAudioTemplate.waveformSeekBar.waveProgressColor =
                                DynamicThemeManager.getPrimaryColor()
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Error setting samples for WaveformSeekBar after download",
                                e
                            )
                        }
                        audioPlayerManager.initialize(
                            file.absolutePath,
                            binding.lyAudioTemplate.waveformSeekBar,
                            binding.lyAudioTemplate.ivPlay,
                            binding.lyAudioTemplate.tvDuration,
                            R.drawable.ic_play_audio,
                            R.drawable.ic_playing_audio
                        )
                        Toast.makeText(context, "Audio download complete", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        binding.lyAudioTemplate.ivAudioDownload.visibility = View.VISIBLE
                        Toast.makeText(context, "Audio download failed", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
        currentAudioDownloadJob?.invokeOnCompletion { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) {
                Log.d(
                    TAG,
                    "[AUD: $localFileNameForLogging] Audio Job Cancelled: ${throwable.message}"
                )
            }
        }
    }

    fun cleanupAudioDownload() {
        val audioName = binding.lyAudioTemplate.tvAudioFileName.tag?.toString() ?: "UnknownAudio"
        Log.d(TAG, "cleanupAudioDownload called for $audioName. Job: $currentAudioDownloadJob")
        currentAudioDownloadJob?.cancel("ViewHolder audio download cleanup.")
        currentAudioDownloadJob = null
        if (binding.root.isAttachedToWindow) {
            binding.lyAudioTemplate.flAudioProgressCancel.visibility = View.GONE
            binding.lyAudioTemplate.ivAudioDownload.visibility = View.VISIBLE
            binding.lyAudioTemplate.audioDownloadTemplate.tag = null
        }
    }

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        applyThemeColors(primaryColor)
    }

    private fun applyThemeColors(primaryColor: Int) {
        binding.lyAudioTemplate.waveformSeekBar?.let {
            it.waveProgressColor = primaryColor
        }
        binding.circularProgressBar?.progressBarColor = primaryColor // For document
        binding.videoCircularProgressBar?.progressBarColor = primaryColor // For video
        binding.lyAudioTemplate.audioCircularProgressBar?.progressBarColor =
            primaryColor // For audio
    }

    fun cleanup() {
        Log.d(TAG, "cleanup() called for ViewHolder: $this")
//        audioPlayerManager.cleanup()
//        cleanupDocumentDownload()
//        cleanupVideoDownload()
//        cleanupAudioDownload()
    }

    fun onViewAttached() {
        Log.d(TAG, "onViewAttached() for ViewHolder: $this")
        DynamicThemeManager.registerListener(this)
        applyThemeColors(DynamicThemeManager.getPrimaryColor())
    }

    fun onViewDetached() {
        Log.d(TAG, "onViewDetached() for ViewHolder: $this, Cancelling jobs.")
        DynamicThemeManager.unregisterListener(this)
    }
}