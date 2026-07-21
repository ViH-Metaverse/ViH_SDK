package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class AudioPlayerManager(private val context: Context) {
    private var managerScope: CoroutineScope? = null

    private var mediaPlayer: MediaPlayer? = null
    private var seekBarUpdateJob: Job? = null
    private var waveformSeekBar: WaveformSeekBar? = null
    private var playPauseButton: ImageView? = null
    private var durationTextView: TextView? = null
    private var isPrepared = false
    private var currentAudioPath: String? = null

    private fun ensureActiveScope(): CoroutineScope {
        val currentScope = managerScope
        return if (currentScope == null || !currentScope.isActive) {
            CoroutineScope(Dispatchers.Main + SupervisorJob()).also { managerScope = it }
        } else {
            currentScope
        }
    }

    fun initialize(
        audioPath: String,
        seekBar: WaveformSeekBar,
        button: ImageView,
        textView: TextView,
        playIconRes: Int,
        pauseIconRes: Int
    ) {
        if (currentAudioPath == audioPath && mediaPlayer != null /* && isPrepared removed check here, rely on player state */) {
            managerScope = ensureActiveScope() // Make sure scope is valid
            this.waveformSeekBar = seekBar
            this.playPauseButton = button
            this.durationTextView = textView

            updateUiToCurrentState(playIconRes, pauseIconRes)
            if (mediaPlayer?.isPlaying == true) {
                startSeekBarUpdates()
            } else {
                seekBarUpdateJob?.cancel()
            }
            setupSeekBarListener(seekBar)
            setupPlayButtonListener(button, playIconRes, pauseIconRes)
            return
        }
        releasePlayerAndResetJob()
        managerScope = ensureActiveScope()
        currentAudioPath = audioPath
        this.waveformSeekBar = seekBar
        this.playPauseButton = button
        this.durationTextView = textView
        isPrepared = false
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(audioPath)
                setOnPreparedListener { mp ->
                    isPrepared = true
                    val duration = mp.duration
                    waveformSeekBar?.maxProgress = duration.toFloat()
                    waveformSeekBar?.progress = 0f
                    textView.text = formatDuration(0)
                    button.setImageResource(playIconRes)
                    button.isEnabled = true
                }
                setOnCompletionListener {
                    seekBarUpdateJob?.cancel()
                    if (managerScope?.isActive == true) {
                        button.setImageResource(playIconRes)
                        waveformSeekBar?.progress = 0f
                        textView.text = formatDuration(0)
                    } else {
                        Log.w(
                            "AudioPlayerManager",
                            "Playback Completed but scope was inactive for $currentAudioPath"
                        )
                    }
                }
                setOnErrorListener { _, what, extra ->
                    cleanup()
                    button.isEnabled = false
                    true
                }
                prepareAsync()
                button.isEnabled = false

            } catch (e: IOException) {
                cleanup()
                button?.isEnabled = false
            } catch (e: IllegalStateException) {
                cleanup() // Full cleanup on error
                button?.isEnabled = false
            } catch (e: Exception) {
                cleanup()
                button?.isEnabled = false
            }
        }
        setupSeekBarListener(seekBar)
        setupPlayButtonListener(button, playIconRes, pauseIconRes)
    }

    private fun setupSeekBarListener(seekBar: WaveformSeekBar) {
        seekBar.onProgressChanged = object : SeekBarOnProgressChanged {
            override fun onProgressChanged(
                seekBar: WaveformSeekBar,
                progress: Float,
                fromUser: Boolean
            ) {
                if (fromUser && isPrepared && mediaPlayer != null) {
                    try {
                        mediaPlayer?.seekTo(progress.toInt())
                        durationTextView?.text = formatDuration(progress.toInt())
                    } catch (e: IllegalStateException) {
                        Log.e("AudioPlayerManager", "IllegalStateException during seekTo", e)
                    }
                }
            }
        }
    }

    private fun setupPlayButtonListener(button: ImageView, playIconRes: Int, pauseIconRes: Int) {
        button.setOnClickListener {
            togglePlayPause(playIconRes, pauseIconRes)
        }
    }

    fun togglePlayPause(playIconRes: Int, pauseIconRes: Int) {
        val player = mediaPlayer
        if (player == null) {
            Log.w("AudioPlayerManager", "togglePlayPause called but MediaPlayer is null.")
            return
        }
        if (!isPrepared) {
            Log.w(
                "AudioPlayerManager",
                "togglePlayPause called before prepared for $currentAudioPath"
            )
            return
        }

        try {
            if (player.isPlaying) {
                player.pause()
                playPauseButton?.setImageResource(playIconRes)
                seekBarUpdateJob?.cancel()
            } else {
                player.start()
                playPauseButton?.setImageResource(pauseIconRes)
                startSeekBarUpdates()
            }
        } catch (e: IllegalStateException) {
            cleanup()
            playPauseButton?.isEnabled = false
        }
    }

    private fun startSeekBarUpdates() {
        val currentScope = ensureActiveScope()
        seekBarUpdateJob?.cancel()

        seekBarUpdateJob = currentScope.launch { // Launch on the active scope
            val player = mediaPlayer // Get local ref
            if (player == null) {
                return@launch
            }

            while (isActive) {
                try {
                    val prepared = isPrepared
                    val playing = try {
                        player.isPlaying
                    } catch (e: IllegalStateException) {
                        false
                    }
                    val currentPos = if (prepared && playing) {
                        try {
                            player.currentPosition
                        } catch (e: IllegalStateException) {
                            -1
                        }
                    } else {
                        -1
                    }

                    if (prepared && playing && currentPos != -1) {
                        waveformSeekBar?.progress = currentPos.toFloat()
                        durationTextView?.text = formatDuration(currentPos)
                    } else {
                        if (!prepared || currentPos == -1) {
                            Log.w(
                                "SeekBarUpdate",
                                "Not updating: Prepared=$prepared, Playing=$playing, Pos=$currentPos. Coroutine active: $isActive"
                            )
                        }
                        if (!isActive) break // Exit loop if coroutine cancelled externally
                    }
                    delay(200) // Update interval
                } catch (e: Exception) { // Catch any unexpected exception within the loop
                    break // Stop the loop on error
                }
            }
        }
    }

    private fun updateUiToCurrentState(playIconRes: Int, pauseIconRes: Int) {
        val player = mediaPlayer
        if (player == null) {
            playPauseButton?.setImageResource(playIconRes)
            playPauseButton?.isEnabled = false
            durationTextView?.text = formatDuration(0)
            waveformSeekBar?.progress = 0f
            return
        }

        if (isPrepared) {
            val duration = try {
                player.duration
            } catch (e: IllegalStateException) {
                0
            }
            val currentPosition = try {
                player.currentPosition
            } catch (e: IllegalStateException) {
                0
            }
            val isPlaying = try {
                player.isPlaying
            } catch (e: IllegalStateException) {
                false
            }

            waveformSeekBar?.maxProgress = duration.toFloat()
            waveformSeekBar?.progress = currentPosition.toFloat()
            durationTextView?.text = formatDuration(currentPosition)
            playPauseButton?.setImageResource(if (isPlaying) pauseIconRes else playIconRes)
            playPauseButton?.isEnabled = true
        } else {
            playPauseButton?.setImageResource(playIconRes) // Default to play
            playPauseButton?.isEnabled = false // Can't play yet
            durationTextView?.text = formatDuration(0) // Show 0:00
            waveformSeekBar?.progress = 0f
            waveformSeekBar?.maxProgress = 100f // Default max progress?
        }
    }

    private fun releasePlayerAndResetJob() {
        seekBarUpdateJob?.cancel() // Cancel specific job
        seekBarUpdateJob = null
        mediaPlayer?.apply {
            try {
                if (isPrepared || isPlaying) { // Basic check if it was ever used
                    if (isPlaying) {
                        stop()
                    }
                    reset() // Resets player to Idle state
                }
                release() // Release resources ALWAYS
            } catch (e: Exception) { // Catch broad exception during release
                try {
                    release()
                } catch (ex: Exception) { /* Ignore */
                }
            }
        }
        mediaPlayer = null
        isPrepared = false
    }

    fun cleanup() {
        releasePlayerAndResetJob()
        managerScope?.cancel()
        managerScope = null
        currentAudioPath = null
        waveformSeekBar = null
        playPauseButton = null
        durationTextView = null

    }

    private fun formatDuration(durationMs: Int): String {
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60
        return String.format("%02d:%02d", minutes.coerceAtLeast(0), seconds.coerceAtLeast(0))
    }
}