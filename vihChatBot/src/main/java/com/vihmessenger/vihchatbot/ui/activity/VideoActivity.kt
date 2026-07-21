package com.vihmessenger.vihchatbot.ui.activity

import BaseActivity
import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.databinding.ActivityVideoBinding
import com.vihmessenger.vihchatbot.utils.extensions.saveVideoToGallery
import com.vihmessenger.vihchatbot.utils.extensions.shareVideo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class VideoActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var videoControlsVisible = true // For video player controls
    private var imageControlsAreVisible =
        true // For image viewer controls (top bar + new bottom bar)
    private var videoFile: File? = null
    private var channel: EnterPriseModel? = null

    private lateinit var binding: ActivityVideoBinding
    private var isOptionsMenuVisible = false // For video's popup menu
    private var currentOrientationIsLandscape = false

    private var mediaUrl: String? = null
    private var mediaType: String? = null

    private val hideVideoControlsRunnable = Runnable { // Renamed for clarity
        if (mediaType == MEDIA_TYPE_VIDEO && videoControlsVisible) {
            toggleVideoControlsVisibility()
        }
    }

    private val hideImageControlsRunnable = Runnable {
        if (mediaType == MEDIA_TYPE_IMAGE && imageControlsAreVisible) {
            toggleImageControlsVisibility()
        }
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (mediaType == MEDIA_TYPE_VIDEO && binding.videoView.isPlaying) {
                val currentPosition = binding.videoView.currentPosition
                binding.seekBar.progress = currentPosition
                binding.tvCurrentTime.text = formatTime(currentPosition.toLong())
                handler.postDelayed(this, 500)
            }
        }
    }

    companion object {
        private const val TAG = "VideoActivity"
        const val EXTRA_MEDIA_URL = "EXTRA_MEDIA_URL"
        const val EXTRA_MEDIA_TYPE = "EXTRA_MEDIA_TYPE"
        const val MEDIA_TYPE_VIDEO = "video"
        const val MEDIA_TYPE_IMAGE = "image"

        fun startIntent(
            context: Context,
            mediaUrl: String?,
            mediaType: String?,
            channel: EnterPriseModel?
        ) {
            if (mediaUrl.isNullOrEmpty() || mediaType.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    "Media URL or type is invalid.",
                    Toast.LENGTH_SHORT
                ).show()
                Log.w(TAG, "startIntent: mediaUrl or mediaType is null or empty.")
                return
            }
            if (mediaType != MEDIA_TYPE_VIDEO && mediaType != MEDIA_TYPE_IMAGE) {
                Toast.makeText(context, "Unsupported media type.", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "startIntent: Unsupported mediaType: $mediaType")
                return
            }

            val intent = Intent(context, VideoActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_URL, mediaUrl)
                putExtra(EXTRA_MEDIA_TYPE, mediaType)
                putExtra(AppConstants.CHANNEL_EXTRA, channel)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFullscreenMode()

        mediaUrl = intent.getStringExtra(EXTRA_MEDIA_URL)
        mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        channel = getIntentChannel()

        if (mediaUrl.isNullOrEmpty() || mediaType.isNullOrEmpty()) {
            Log.e(TAG, "Media URL or Type is missing in onCreate.")
            Toast.makeText(this, "Error: Media information is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()
        setupCommonStatusBar()

        if (mediaType == MEDIA_TYPE_IMAGE) {
            setupImageViewer()
        } else if (mediaType == MEDIA_TYPE_VIDEO) {
            // setupPopUpMenuActions is relevant for video's "more options" button
            setupPopUpMenuActions()
            setupVideoPlayerAndControls()
        } else {
            Log.e(TAG, "Invalid media type: $mediaType")
            Toast.makeText(this, "Unsupported media type.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun getIntentChannel(): EnterPriseModel? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA, EnterPriseModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA) as? EnterPriseModel
        }
    }

    private fun setupCommonStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = getColor(android.R.color.black)
        }
    }

    private fun setupImageViewer() {
        // Hide all video-specific controls
        binding.videoView.visibility = View.GONE
        binding.videoControls.visibility =
            View.GONE // Hides seekbar, video play/pause, orientation, expandCancel (video's more options)
        binding.ivCenterPlayPause.visibility = View.GONE
        binding.videoOverlay.visibility = View.GONE
        binding.popupIconsContainer.visibility = View.GONE // Hide video's share/download popup

        // Show image view and its specific controls
        binding.imageViewDisplay.visibility = View.VISIBLE
        binding.videoTopBarLayout.visibility = View.VISIBLE
        binding.imageControlsBottomBar.visibility = View.VISIBLE
        imageControlsAreVisible = true // Set initial state

        mediaUrl?.let {
            CustomImageLoader.loadImageView(
                binding.imageViewDisplay,
                it,
                channel?.displayNameModel?.display_name ?: "Image"
            )
        } ?: run {
            Toast.makeText(this, "Image URL is missing.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "setupImageViewer: mediaUrl is null")
            binding.imageViewDisplay.setImageResource(R.drawable.placeholder)
        }

        if (mediaUrl.isNullOrEmpty()) {
            // Disable new image bottom bar buttons if URL is null
            binding.btnShareImageBottom.isEnabled = false
            binding.btnDownloadImageBottom.isEnabled = false
            binding.btnShareImageBottom.alpha = 0.5f
            binding.btnDownloadImageBottom.alpha = 0.5f
        } else {
            binding.btnShareImageBottom.isEnabled = true
            binding.btnDownloadImageBottom.isEnabled = true
            binding.btnShareImageBottom.alpha = 1.0f
            binding.btnDownloadImageBottom.alpha = 1.0f
        }
        binding.btnOrientationToggle.visibility =
            View.GONE // Ensure video orientation toggle is hidden

        // Listeners for image controls
        binding.btnShareImageBottom.setOnClickListener {
            shareCurrentImage()
        }
        binding.btnDownloadImageBottom.setOnClickListener {
            downloadCurrentImage()
        }

        // Listener for toggling controls when image or container is tapped
        binding.imageViewDisplay.setOnClickListener { toggleImageControlsVisibility() }
        binding.videoPlayerContainer.setOnClickListener { // Catches taps on the container around the image
            if (mediaType == MEDIA_TYPE_IMAGE) {
                toggleImageControlsVisibility()
            }
        }
        resetHideImageControlsTimer() // Start timer to auto-hide image controls
    }

    private fun setupVideoPlayerAndControls() {
        binding.imageViewDisplay.visibility = View.GONE
        binding.imageControlsBottomBar.visibility = View.GONE // Hide image specific bottom bar

        binding.videoView.visibility = View.VISIBLE
        binding.btnOrientationToggle.visibility = View.VISIBLE

        setupVideoPlayerInternal()
        setupVideoBottomControlsActions()
        setupCenterControls()

        videoControlsVisible = false // Video controls start hidden
        binding.videoTopBarLayout.visibility = View.GONE
        binding.videoControls.visibility = View.GONE
        binding.videoOverlay.visibility = View.GONE
        binding.ivCenterPlayPause.visibility = View.GONE
        updateCenterPlayPauseIcon()
        updateOrientationButtonIcon()
    }


    private fun updateCenterPlayPauseIcon() {
        if (binding.videoView.isPlaying) {
            binding.ivCenterPlayPause.setImageResource(R.drawable.ic_pause)
            binding.ivCenterPlayPause.contentDescription = getString(R.string.pause)
        } else {
            binding.ivCenterPlayPause.setImageResource(R.drawable.ic_play)
            binding.ivCenterPlayPause.contentDescription = getString(R.string.play)
        }
    }

    private fun setupCenterControls() {
        binding.ivCenterPlayPause.setOnClickListener {
            binding.btnPlayPause.performClick()
        }
        binding.videoOverlay.setOnClickListener {
            toggleVideoControlsVisibility()
        }
    }

    private fun updateOrientationButtonIcon() {
        currentOrientationIsLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (currentOrientationIsLandscape) {
            binding.btnOrientationToggle.setImageResource(R.drawable.ic_shrink_screen)
        } else {
            binding.btnOrientationToggle.setImageResource(R.drawable.ic_expand_screen)
        }
    }

    override fun initView() {}
    private fun setupFullscreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }


    private fun setupVideoPlayerInternal() {
        binding.videoView.setOnClickListener {
            toggleVideoControlsVisibility()
        }

        val videoUriString = mediaUrl
        if (videoUriString.isNullOrEmpty()) {
            Log.e(TAG, "Video URI string is null or empty.")
            Toast.makeText(this, "Error: Video link is missing.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val videoUri = Uri.parse(videoUriString)

        if (videoUri.scheme?.equals("file", ignoreCase = true) == true) {
            videoUri.path?.let { path ->
                videoFile = File(path)
                if (videoFile?.exists() != true) {
                    Log.e(TAG, "File does not exist at path: $path for URI $videoUriString")
                    Toast.makeText(this, "Error: Video file not found.", Toast.LENGTH_LONG).show()
                    disableShareDownloadPopup() // Video popup
                } else {
                    enableShareDownloadPopup() // Video popup
                }
            } ?: run {
                Log.e(TAG, "Path from file URI is null: $videoUriString")
                Toast.makeText(this, "Error: Invalid video file path.", Toast.LENGTH_LONG).show()
                disableShareDownloadPopup()
            }
        } else {
            Log.w(TAG, "Video URI is not a local file URI: $videoUriString.")
            disableShareDownloadPopup()
        }

        binding.videoView.apply {
            setVideoURI(videoUri)
            setOnPreparedListener { mp ->
                binding.tvTotalDuration.text = formatTime(mp.duration.toLong())
                binding.seekBar.max = mp.duration
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                start()
                startUpdatingSeekBarProgress()
                updateCenterPlayPauseIcon()
                if (!videoControlsVisible) {
                    toggleVideoControlsVisibility()
                } else {
                    resetHideVideoControlsTimer()
                }
            }
            setOnCompletionListener {
                binding.seekBar.progress = binding.seekBar.max
                binding.tvCurrentTime.text = formatTime(binding.seekBar.max.toLong())
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                updateCenterPlayPauseIcon()
                stopUpdatingSeekBarProgress()
                handler.removeCallbacks(hideVideoControlsRunnable)
                seekTo(0)
                binding.seekBar.progress = 0
                binding.tvCurrentTime.text = formatTime(0L)
                if (!videoControlsVisible) toggleVideoControlsVisibility()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "VideoView Error: What: $what, Extra: $extra for URI: $videoUriString")
                Toast.makeText(this@VideoActivity, "Cannot play this video.", Toast.LENGTH_SHORT)
                    .show()
                finish()
                true
            }
        }

        binding.btnPlayPause.setOnClickListener {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                stopUpdatingSeekBarProgress()
                handler.removeCallbacks(hideVideoControlsRunnable)
            } else {
                binding.videoView.start()
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                startUpdatingSeekBarProgress()
                resetHideVideoControlsTimer()
            }
            updateCenterPlayPauseIcon()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.videoView.seekTo(progress)
                    binding.tvCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopUpdatingSeekBarProgress()
                handler.removeCallbacks(hideVideoControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (binding.videoView.isPlaying) {
                    startUpdatingSeekBarProgress()
                    resetHideVideoControlsTimer()
                } else {
                    binding.tvCurrentTime.text = formatTime(binding.seekBar.progress.toLong())
                }
            }
        })
    }

    // This setup is for the video's "more options" popup (expandCancel button in video_controls)
    private fun setupPopUpMenuActions() {
        binding.expandCancel.setImageResource(R.drawable.ic_expand_popup)
        isOptionsMenuVisible = false
        binding.popupIconsContainer.visibility = View.GONE

        binding.expandCancel.setOnClickListener { // This is the expandCancel within video_controls
            isOptionsMenuVisible = !isOptionsMenuVisible
            if (isOptionsMenuVisible) {
                binding.popupIconsContainer.visibility = View.VISIBLE
                binding.expandCancel.setImageResource(R.drawable.ic_close_popup)
            } else {
                binding.popupIconsContainer.visibility = View.GONE
                binding.expandCancel.setImageResource(R.drawable.ic_expand_popup)
            }
            // This check is important: only reset video controls timer if in video mode and they are visible
            if (mediaType == MEDIA_TYPE_VIDEO && videoControlsVisible) {
                resetHideVideoControlsTimer()
            }
        }

        binding.btnSharePopup.setOnClickListener { // Video share
            videoFile?.let { file -> shareVideo(file.absolutePath, this) }
                ?: Toast.makeText(this, "Video not available for sharing.", Toast.LENGTH_SHORT)
                    .show()
            hideOptionsMenu() // Hides video's popup
        }

        binding.btnDownloadPopup.setOnClickListener { // Video download
            videoFile?.let { file -> saveVideoToGallery(file.absolutePath) }
                ?: Toast.makeText(this, "Video not available for download.", Toast.LENGTH_SHORT)
                    .show()
            hideOptionsMenu() // Hides video's popup
        }
    }

    private fun setupVideoBottomControlsActions() {
        binding.btnOrientationToggle.setOnClickListener {
            requestedOrientation = if (currentOrientationIsLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            }
            if (videoControlsVisible) resetHideVideoControlsTimer()
        }
    }

    // Hides the video's "more options" popup menu
    private fun hideOptionsMenu() {
        binding.popupIconsContainer.visibility = View.GONE
        binding.expandCancel.setImageResource(R.drawable.ic_expand_popup)
        isOptionsMenuVisible = false
        if (mediaType == MEDIA_TYPE_VIDEO && videoControlsVisible) {
            resetHideVideoControlsTimer()
        }
    }

    private fun shareCurrentImage() {
        mediaUrl?.let { url ->
            val fileProviderAuthority = "${applicationContext.packageName}.fileprovider"
            shareActualImageFromUrl(this, url, "Share this Image", fileProviderAuthority)
            Log.i(TAG, "Sharing image from URL: $url")
        } ?: Toast.makeText(this, "Image URL not available.", Toast.LENGTH_SHORT).show()
        if (imageControlsAreVisible) toggleImageControlsVisibility()
    }

    private fun downloadCurrentImage() {
        mediaUrl?.let { url ->
            val titleForFile = channel?.displayNameModel?.display_name ?: binding.tvToolBarChatName.text.toString()
            downloadImageToGallery(this, url, titleForFile)
        } ?: Toast.makeText(this, "Image URL not available.", Toast.LENGTH_SHORT).show()
        if (imageControlsAreVisible) toggleImageControlsVisibility()
    }

    private fun disableShareDownloadPopup() {
        binding.btnSharePopup.isEnabled = false
        binding.btnDownloadPopup.isEnabled = false
        binding.btnSharePopup.alpha = 0.5f
        binding.btnDownloadPopup.alpha = 0.5f
    }

    private fun enableShareDownloadPopup() {
        binding.btnSharePopup.isEnabled = true
        binding.btnDownloadPopup.isEnabled = true
        binding.btnSharePopup.alpha = 1.0f
        binding.btnDownloadPopup.alpha = 1.0f
    }

    private fun startUpdatingSeekBarProgress() {
        handler.removeCallbacks(updateProgressRunnable)
        handler.post(updateProgressRunnable)
    }

    private fun stopUpdatingSeekBarProgress() {
        handler.removeCallbacks(updateProgressRunnable)
    }

    private fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.tbMain)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        binding.ivBackArrow.setOnClickListener { onSupportNavigateUp() }

        binding.tvToolBarMain.visibility = View.GONE
        binding.llToolChat.visibility = View.VISIBLE
        val channelName = channel?.displayNameModel?.display_name
        binding.tvToolBarChatName.text = channelName ?: "Media Viewer"
        val logoUrl = channel?.display_img?.takeIf { it.isNotBlank() } ?: channel?.profile_picture

        if (!logoUrl.isNullOrBlank()) {
            binding.ivToolbar.visibility = View.VISIBLE
            CustomImageLoader.loadImageView(
                binding.ivToolbar,
                logoUrl,
                channelName?.ifEmpty { " " }
            )
        } else {
            binding.ivToolbar.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onPause() {
        super.onPause()
        if (mediaType == MEDIA_TYPE_VIDEO) {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                updateCenterPlayPauseIcon()
            }
            stopUpdatingSeekBarProgress()
            handler.removeCallbacks(hideVideoControlsRunnable)
        } else if (mediaType == MEDIA_TYPE_IMAGE) {
            handler.removeCallbacks(hideImageControlsRunnable)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mediaType == MEDIA_TYPE_VIDEO) {
            updateOrientationButtonIcon()
            val currentPosition = binding.videoView.currentPosition
            binding.seekBar.progress = currentPosition
            binding.tvCurrentTime.text = formatTime(currentPosition.toLong())

            if (binding.videoView.isPlaying) {
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                startUpdatingSeekBarProgress()
                resetHideVideoControlsTimer()
            } else {
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                if (videoControlsVisible) {
                    handler.removeCallbacks(hideVideoControlsRunnable)
                }
            }
            updateCenterPlayPauseIcon()

            if (videoControlsVisible) {
                binding.videoControls.visibility = View.VISIBLE
                binding.videoTopBarLayout.visibility = View.VISIBLE
                binding.videoOverlay.visibility = View.VISIBLE
                binding.ivCenterPlayPause.visibility = View.VISIBLE
                binding.popupIconsContainer.visibility =
                    if (isOptionsMenuVisible) View.VISIBLE else View.GONE
            } else {
                binding.videoControls.visibility = View.GONE
                binding.videoTopBarLayout.visibility = View.GONE
                binding.videoOverlay.visibility = View.GONE
                binding.ivCenterPlayPause.visibility = View.GONE
                binding.popupIconsContainer.visibility = View.GONE
            }
        } else if (mediaType == MEDIA_TYPE_IMAGE) {
            // Restore image controls visibility state
            val visibility = if (imageControlsAreVisible) View.VISIBLE else View.GONE
            binding.videoTopBarLayout.visibility = visibility
            binding.imageControlsBottomBar.visibility = visibility
            binding.popupIconsContainer.visibility = View.GONE // Ensure video popup is hidden

            if (imageControlsAreVisible) {
                resetHideImageControlsTimer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaType == MEDIA_TYPE_VIDEO) {
            binding.videoView.stopPlayback()
            stopUpdatingSeekBarProgress()
        }
        handler.removeCallbacksAndMessages(null)
    }

    private fun toggleVideoControlsVisibility() {
        videoControlsVisible = !videoControlsVisible
        val newVisibility = if (videoControlsVisible) View.VISIBLE else View.GONE

        binding.videoControls.visibility = newVisibility
        binding.videoTopBarLayout.visibility = newVisibility
        binding.videoOverlay.visibility = newVisibility
        binding.ivCenterPlayPause.visibility = newVisibility

        if (videoControlsVisible) {
            updateCenterPlayPauseIcon()
            resetHideVideoControlsTimer()
            binding.popupIconsContainer.visibility =
                if (isOptionsMenuVisible) View.VISIBLE else View.GONE
        } else {
            handler.removeCallbacks(hideVideoControlsRunnable)
            binding.popupIconsContainer.visibility = View.GONE
        }
    }

    private fun toggleImageControlsVisibility() {
        imageControlsAreVisible = !imageControlsAreVisible
        val newVisibility = if (imageControlsAreVisible) View.VISIBLE else View.GONE

        binding.videoTopBarLayout.visibility = newVisibility
        binding.imageControlsBottomBar.visibility = newVisibility

        if (imageControlsAreVisible) {
            resetHideImageControlsTimer()
        } else {
            handler.removeCallbacks(hideImageControlsRunnable)
        }
    }


    private fun resetHideVideoControlsTimer() { // Renamed for clarity
        handler.removeCallbacks(hideVideoControlsRunnable)
        if (binding.videoView.isPlaying || binding.btnPlayPause.drawable.constantState == getDrawable(
                R.drawable.ic_pause
            )?.constantState
        ) {
            handler.postDelayed(hideVideoControlsRunnable, 3000)
        }
    }

    private fun resetHideImageControlsTimer() {
        handler.removeCallbacks(hideImageControlsRunnable)
        handler.postDelayed(hideImageControlsRunnable, 3000) // 3 seconds
    }

    override fun onBackPressed() {
        if (mediaType == MEDIA_TYPE_VIDEO && currentOrientationIsLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mediaType == MEDIA_TYPE_VIDEO) {
            updateOrientationButtonIcon()
            val currentVisibility = if (videoControlsVisible) View.VISIBLE else View.GONE
            binding.videoControls.visibility = currentVisibility
            binding.videoTopBarLayout.visibility = currentVisibility
            binding.videoOverlay.visibility = currentVisibility
            binding.ivCenterPlayPause.visibility = currentVisibility

            if (videoControlsVisible) {
                updateCenterPlayPauseIcon()
                resetHideVideoControlsTimer()
            }
            binding.popupIconsContainer.visibility =
                if (isOptionsMenuVisible && videoControlsVisible) View.VISIBLE else View.GONE
        } else if (mediaType == MEDIA_TYPE_IMAGE) {
            val currentVisibility = if (imageControlsAreVisible) View.VISIBLE else View.GONE
            binding.videoTopBarLayout.visibility = currentVisibility
            binding.imageControlsBottomBar.visibility = currentVisibility
            binding.popupIconsContainer.visibility = View.GONE // Ensure video popup is hidden

            if (imageControlsAreVisible) {
                resetHideImageControlsTimer()
            }
        }
    }

    fun shareActualImageFromUrl(
        context: Context,
        imageUrl: String?,
        shareTitle: String = "Share Image",
        authority: String // e.g., "com.yourapp.fileprovider"
    ) {
        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "Image URL is not available to share.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Show a toast that we are preparing the image
        Toast.makeText(context, "Preparing image for sharing...", Toast.LENGTH_SHORT).show()

        // Perform network and file operations on a background thread
        Thread {
            var tempImageFile: File? = null
            try {
                // 1. Create a temporary file in the cache subdirectory defined in file_paths.xml
                val cachePath = File(context.cacheDir, "shared_images")
                cachePath.mkdirs() // Create the subdirectory if it doesn't exist

                // Extract a filename or generate one
                val fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1)
                    .takeIf { it.isNotEmpty() } ?: "shared_image_${System.currentTimeMillis()}.jpg"

                tempImageFile = File(cachePath, fileName)

                // 2. Download the image
                val url = URL(imageUrl)
                val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val output = FileOutputStream(tempImageFile)
                input.use { _input ->
                    output.use { _output ->
                        _input.copyTo(_output)
                    }
                }
                connection.disconnect()

                // 3. Get a content URI using FileProvider
                val imageUri: Uri = FileProvider.getUriForFile(
                    context,
                    authority, // Use the authority you defined in AndroidManifest.xml
                    tempImageFile
                )

                // 4. Create the share intent (on the main thread)
                (context as? android.app.Activity)?.runOnUiThread {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        type =
                            context.contentResolver.getType(imageUri) ?: "image/*" // Get MIME type
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooserIntent = Intent.createChooser(shareIntent, shareTitle)

                    if (chooserIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(chooserIntent)
                    } else {
                        Toast.makeText(
                            context,
                            "No app found to handle sharing.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: IOException) {
                Log.e("ShareImage", "Error downloading or sharing image: ${e.message}", e)
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Error preparing image for sharing.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ShareImage", "Generic error sharing image: ${e.message}", e)
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Could not share image.", Toast.LENGTH_SHORT).show()
                }
            }
            // Note: tempImageFile in cache will be cleaned up by the system eventually,
            // or you could implement a more aggressive cleanup strategy if needed.
        }.start()
    }


    fun downloadImageToGallery(
        context: Context,
        imageUrl: String?,
        imageFileNamePrefix: String = "Image" // A prefix for the downloaded file name
    ) {
        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "Image URL is invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val uri = Uri.parse(imageUrl)
            val fileNameBuilder = StringBuilder()
            val sanitizedPrefix = imageFileNamePrefix.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            fileNameBuilder.append(sanitizedPrefix)
            fileNameBuilder.append("_${System.currentTimeMillis()}")

            // Try to get a file extension from the URL or MIME type
            var fileExtension = MimeTypeMap.getFileExtensionFromUrl(imageUrl)
            if (fileExtension.isNullOrEmpty()) {
                // Fallback if extension is not in URL, try to get from ContentResolver if it's a content URI (less likely for remote URL)
                // For remote URLs, this might not give a type. Default to common image types.
                val mimeType = context.contentResolver.getType(uri)
                fileExtension =
                    mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                        ?: "png" // Default to .png
            }
            fileNameBuilder.append(".$fileExtension")

            val finalFileName = fileNameBuilder.toString()

            val request = DownloadManager.Request(uri).apply {
                setTitle(finalFileName) // Title for the notification
                setDescription("Downloading image...") // Description for the notification
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES,
                    File.separator + "YourAppName" + File.separator + finalFileName // Save in a subfolder, e.g., Pictures/YourAppName/image.png
                )
                setMimeType(
                    context.contentResolver.getType(uri) ?: "image/$fileExtension"
                ) // Set MIME type
                allowScanningByMediaScanner() // Important to make it appear in Gallery
            }

            downloadManager.enqueue(request)
            Toast.makeText(context, "Download started. Check notifications.", Toast.LENGTH_LONG)
                .show()

        } catch (e: Exception) {
            Toast.makeText(context, "Error starting download: ${e.message}", Toast.LENGTH_LONG)
                .show()
            e.printStackTrace()
        }
    }


    override fun initViewModels() {}
    override fun setObservers() {}
    override fun setListeners() {}
    override fun onViewClick(view: View?) {}
    override fun onThemeChanged(
        primaryColor: Int, secondaryColor: Int, primaryTextColor: Int,
        secondaryTextColor: Int, headerColor: Int, defaultTextColor: Int
    ) {
    }
}