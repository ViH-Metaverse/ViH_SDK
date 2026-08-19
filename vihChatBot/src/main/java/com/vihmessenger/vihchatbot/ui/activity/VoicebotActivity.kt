package com.vihmessenger.vihchatbot.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.vihmessenger.vihchatbot.utils.VihLog
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vihmessenger.vihchatbot.databinding.ActivityVoicebotBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import com.vihmessenger.vihchatbot.utils.ScreenCapturePolicy
import com.vihmessenger.vihchatbot.config.VihConfigStore

/**
 * Voice-bot call screen (protocol v2).
 *
 * Transport is a raw WebSocket ([ws]) to the agent's permanent endpoint
 * (`wss://…/ws/agent_<id>`, taken from the message's `voice_bot.ws_url`) carrying JSON control
 * frames (text) and raw PCM audio (binary): 16 kHz mono s16le uplink, 24 kHz mono s16le
 * downlink. One socket is one call. Presentation (orb / mute / speaker / hang-up) is unchanged.
 *
 * Lifecycle:
 *  1. Caller passes the message's voice_bot ws_url (+ agent name for the title).
 *  2. Request RECORD_AUDIO, then open the socket — the session starts on connect, there is no
 *     start frame, no bot key and no per-call context.
 *  3. On `ready`, start streaming the mic; play agent audio back gaplessly as it arrives
 *     (announced by `tts_start`).
 *  4. Hang-up / server close (`1000` after ~30 s of silence, a normal end) → tear down.
 */
class VoicebotActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoicebotActivity"
        private const val EXTRA_WS_URL = "extra_ws_url"
        private const val EXTRA_AGENT_NAME = "extra_agent_name"
        private const val EXTRA_CUSTOMER_NAME = "extra_customer_name"

        // Uplink: 16 kHz mono s16le, 100 ms per frame (3,200 bytes — within the server's
        // 3.2k–8k "sweet spot").
        private const val IN_RATE = 16000
        private const val FRAME_BYTES = IN_RATE / 10 * 2
        // Downlink rate is NOT fixed — the server announces it per-turn in `tts_start`
        // (observed 16 kHz on live agents). This is only the initial default until the first
        // tts_start; playback is rebuilt to the announced rate. See [ensurePlaybackRate].
        private const val OUT_RATE = 24000

        fun startIntent(
            context: Context,
            wsUrl: String,
            agentName: String,
            customerName: String = ""
        ): Intent {
            return Intent(context, VoicebotActivity::class.java).apply {
                putExtra(EXTRA_WS_URL, wsUrl)
                putExtra(EXTRA_AGENT_NAME, agentName)
                putExtra(EXTRA_CUSTOMER_NAME, customerName)
            }
        }
    }

    private lateinit var binding: ActivityVoicebotBinding

    private val client: OkHttpClient by lazy { OkHttpClient() }
    private var ws: WebSocket? = null
    @Volatile private var callEnded = false

    // Capture
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false
    @Volatile private var muted = false

    // Playback — a small queue keeps the WS reader thread free so control frames (e.g.
    // barge_in) are processed promptly rather than blocked behind AudioTrack.write.
    private var track: AudioTrack? = null
    // The output sample rate currently backing [track]. Starts at OUT_RATE, then follows
    // whatever each `tts_start` announces (see [ensurePlaybackRate]).
    @Volatile private var playbackRate = OUT_RATE
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackThread: Thread? = null
    @Volatile private var playing = false

    private var speakerOn = true
    private val mainHandler = Handler(Looper.getMainLooper())
    private val orbIdleRunnable = Runnable { binding.orbView.setSpeakingLevel(0.15f) }

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val recordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                connect()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required to start the voice call",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SECURITY (VAPT F-09): this screen bypasses BaseActivity, so apply the policy directly.
        ScreenCapturePolicy.apply(this)
        binding = ActivityVoicebotBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val agentName = intent.getStringExtra(EXTRA_AGENT_NAME)?.takeIf { it.isNotBlank() }
            ?: "Voice Assistant"
        binding.tvAgentName.text = agentName
        binding.tvStatus.text = "Calling $agentName…"

        binding.btnHangUp.setOnClickListener { hangUp() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }
        refreshSpeakerIcon()
        refreshMuteIcon()

        ensurePermissionAndStart()
    }

    private fun ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            connect()
        } else {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ------------------------------------------------------------------ transport

    /**
     * True when [wsUrl] is either encrypted (`wss://`) or the host app has explicitly
     * permitted cleartext voice via [VihSecurity.allowInsecureVoiceTransport].
     *
     * Scheme comparison is case-insensitive because the URL arrives from the server's
     * `voice_bot.ws_url` and is not normalised anywhere upstream.
     */
    private fun isTransportAcceptable(wsUrl: String): Boolean {
        val isEncrypted = wsUrl.startsWith("wss://", ignoreCase = true)
        if (isEncrypted) return true
        val allowed = VihConfigStore.config?.security?.allowInsecureVoiceTransport == true
        if (allowed) {
            VihLog.w(TAG, "Voice call is running over cleartext ws:// by host opt-in.")
        }
        return allowed
    }

    private fun connect() {
        val wsUrl = intent.getStringExtra(EXTRA_WS_URL).orEmpty()
        if (wsUrl.isBlank()) {
            showErrorAndFinish("Missing call details")
            return
        }

        // SECURITY (VAPT F-04, CWE-319): the voice transport carries the whole PCM conversation
        // with no auth, so on cleartext anyone on the path can record the call. v2 agent URLs are
        // wss:// and pass this on the first branch; fail closed on anything else unless the host
        // app has explicitly accepted the risk, so the insecure path stays a deliberate choice.
        if (!isTransportAcceptable(wsUrl)) {
            VihLog.e(TAG, "Refusing cleartext ws:// voice call — host has not opted in.")
            showErrorAndFinish("Secure voice calling is unavailable")
            return
        }

        setCallAudioMode()
        startPlayback()

        // The agent personalises its greeting from a {{customer_name}} placeholder. The service
        // fills that placeholder from a `customer_name` QUERY PARAM on the socket URL — verified
        // against a live agent (`?customer_name=Rishabh` → "Namaste Rishabh ji!"), with no
        // unsubstituted-placeholder warning. It is NOT read from an in-band frame. Agents whose
        // prompt has no placeholder simply ignore the extra param.
        val customerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME).orEmpty().trim()
        val connectUrl = if (customerName.isEmpty()) wsUrl else {
            val sep = if (wsUrl.contains("?")) "&" else "?"
            wsUrl + sep + "customer_name=" + java.net.URLEncoder.encode(customerName, "UTF-8")
        }
        val req = Request.Builder().url(connectUrl).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                VihLog.d(TAG, "ws open (customer_name=${customerName.ifEmpty { "-" }})")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (val type = o.optString("type")) {
                    "ready" -> runOnUiThread {
                        VihLog.d(TAG, "ready -> starting mic capture")
                        binding.tvStatus.visibility = View.GONE
                        binding.orbView.setSpeakingLevel(0.15f)
                        startCapture(webSocket)
                    }
                    // The agent is about to speak; the binary frames that follow are its audio.
                    // The server announces the sample rate here and it varies per agent (16 kHz
                    // seen live), so retune the output track to match — otherwise it plays back at
                    // the wrong pitch/speed. This frame always precedes the audio, so the track is
                    // correct before the first chunk arrives.
                    "tts_start" -> {
                        val rate = o.optInt("sample_rate", playbackRate)
                        if (rate > 0) ensurePlaybackRate(rate)
                    }
                    "response_text" -> VihLog.d(TAG, "agent said: ${o.optString("text")}")
                    // The agent has ended the session (it wrapped up the call, or hit its
                    // inactivity timeout). End the call on the app immediately so the screen
                    // doesn't linger waiting for the socket close that trails it. Any goodbye
                    // line's audio has already arrived (its tts_end precedes this frame).
                    "session_end" -> {
                        VihLog.i(TAG, "agent ended session: ${o.optString("session_id")}")
                        runOnUiThread { endCall(remote = true) }
                    }
                    // Not documented for this service (v2 doc §5) — handled defensively so the
                    // cut is immediate if it does arrive.
                    "event" -> when (o.optString("name")) {
                        "barge_in" -> flushPlayback()
                        "call_ended" -> runOnUiThread { endCall(remote = true) }
                    }
                    "error" -> runOnUiThread { showBusyOrError(o.optString("message")) }
                    else -> VihLog.d(TAG, "unhandled frame type=$type")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size > 0) onAudioDown(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, r: Response?) {
                VihLog.e(TAG, "ws failure httpCode=${r?.code}", t)
                // 403 = unknown/expired agent id: the handshake fails and the socket never opens.
                val message = if (r?.code == 403) "Couldn't start the call" else "Connection failed"
                runOnUiThread { showErrorAndFinish(message) }
            }

            // The server ends the call itself — it speaks a goodbye and closes with 1000 after
            // ~30 s of silence. OkHttp only delivers onClosed once we have also sent a close, so
            // acknowledge here; without it a server-initiated hang-up left this screen hanging.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                VihLog.i(TAG, "ws closing code=$code reason=$reason")
                runCatching { webSocket.close(1000, null) }
                runOnUiThread { endCall(remote = true) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                VihLog.i(TAG, "ws closed code=$code reason=$reason")
                runOnUiThread { endCall(remote = true) }
            }
        })
    }

    // ------------------------------------------------------------------ capture (mic -> WS)

    @SuppressLint("MissingPermission") // RECORD_AUDIO is verified before connect()
    private fun startCapture(webSocket: WebSocket) {
        if (capturing) return
        val minBuf = AudioRecord.getMinBufferSize(
            IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            IN_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME_BYTES * 4)
        )
        recorder = rec
        runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        runCatching { NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }

        rec.startRecording()
        VihLog.d(TAG, "startCapture: state=${rec.state} recState=${rec.recordingState}")
        capturing = true
        captureThread = Thread {
            val buf = ByteArray(FRAME_BYTES)
            val silence = ByteArray(FRAME_BYTES)
            while (capturing) {
                val n = rec.read(buf, 0, buf.size) // real-time paced; blocks ~100 ms
                if (n <= 0) continue
                // Stream continuously, INCLUDING silence — the server's VAD needs it. Mute
                // sends zeroed frames, never stops.
                val frame = if (muted) silence else buf.copyOf(n)
                runCatching { webSocket.send(ByteString.of(*frame)) }
            }
        }.apply { start() }
    }

    private fun stopCapture() {
        capturing = false
        captureThread = null
        recorder?.let { rec ->
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        recorder = null
    }

    // ------------------------------------------------------------------ playback (WS -> speaker)

    private fun startPlayback() {
        buildPlaybackTrack(playbackRate)
        playing = true
        playbackThread = Thread {
            while (playing) {
                val chunk = runCatching { playbackQueue.take() }.getOrNull() ?: continue
                if (chunk.isEmpty()) continue
                runCatching { track?.write(chunk, 0, chunk.size) }
            }
        }.apply { start() }
    }

    /**
     * Retune playback to [rate] when the server announces a new one in `tts_start`.
     *
     * The playback thread reads the [track] field afresh on every loop, so swapping it here is
     * safe: we stand up the new track first, then release the old one. Called on the WS reader
     * thread, always before the turn's audio frames arrive.
     */
    private fun ensurePlaybackRate(rate: Int) {
        if (track != null && rate == playbackRate) return
        VihLog.d(TAG, "playback rate ${playbackRate} -> $rate")
        val old = track
        buildPlaybackTrack(rate)
        old?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
    }

    private fun buildPlaybackTrack(rate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, rate / 5 * 2)) // ~200 ms
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        track = newTrack
        playbackRate = rate
        newTrack.play()
    }

    private fun onAudioDown(bytes: ByteArray) {
        if (!playing) return
        playbackQueue.offer(bytes)
        // Drive the orb from live audio: pulse while the agent speaks, decay to idle shortly
        // after the last chunk.
        mainHandler.post {
            binding.orbView.setSpeakingLevel(0.85f)
            mainHandler.removeCallbacks(orbIdleRunnable)
            mainHandler.postDelayed(orbIdleRunnable, 250)
        }
    }

    /** Barge-in: drop everything buffered/queued so the cut feels immediate. */
    private fun flushPlayback() {
        playbackQueue.clear()
        runCatching {
            track?.pause()
            track?.flush()
            track?.play()
        }
        mainHandler.post {
            mainHandler.removeCallbacks(orbIdleRunnable)
            binding.orbView.setSpeakingLevel(0.15f)
        }
    }

    private fun stopPlayback() {
        playing = false
        playbackQueue.clear()
        playbackQueue.offer(ByteArray(0)) // unblock take()
        playbackThread = null
        track?.let { t ->
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        track = null
    }

    // ------------------------------------------------------------------ controls

    private fun toggleMute() {
        muted = !muted
        refreshMuteIcon()
    }

    private fun refreshMuteIcon() {
        binding.btnMute.isSelected = muted
        binding.btnMute.setImageResource(
            if (muted) com.vihmessenger.vihchatbot.R.drawable.ic_mic_off
            else com.vihmessenger.vihchatbot.R.drawable.ic_mic_on
        )
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        applySpeakerRouting(speakerOn)
        refreshSpeakerIcon()
    }

    private fun refreshSpeakerIcon() {
        binding.btnSpeaker.isSelected = speakerOn
        binding.btnSpeaker.setImageResource(
            if (speakerOn) com.vihmessenger.vihchatbot.R.drawable.ic_speaker_on
            else com.vihmessenger.vihchatbot.R.drawable.ic_speaker_off
        )
    }

    private fun setCallAudioMode() {
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        applySpeakerRouting(speakerOn)
    }

    /**
     * Routes call audio between the loudspeaker and the earpiece. Uses the modern
     * [AudioManager.setCommunicationDevice] on API 31+; falls back to the deprecated
     * [AudioManager.setSpeakerphoneOn] on older devices.
     */
    private fun applySpeakerRouting(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val device = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == targetType }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    VihLog.w(TAG, "no communication device of type=$targetType")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
            }
        } catch (t: Throwable) {
            VihLog.e(TAG, "applySpeakerRouting failed", t)
        }
    }

    // ------------------------------------------------------------------ teardown

    private fun hangUp() {
        // v2 has no application-level "end" frame — closing the socket ends the session.
        endCall(remote = false)
    }

    private fun showBusyOrError(message: String) {
        val friendly = if (message.contains("busy", ignoreCase = true)) {
            "All lines are busy, please try again."
        } else {
            "Call error. Please try again."
        }
        Toast.makeText(this, friendly, Toast.LENGTH_LONG).show()
        endCall(remote = true)
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        endCall(remote = true)
    }

    /** Idempotent full teardown. [remote] just distinguishes who initiated (for logging). */
    private fun endCall(remote: Boolean) {
        if (callEnded) return
        callEnded = true
        VihLog.i(TAG, "endCall remote=$remote")
        mainHandler.removeCallbacks(orbIdleRunnable)
        stopCapture()
        stopPlayback()
        runCatching { ws?.close(1000, "bye") }
        ws = null
        restoreAudioMode()
        if (!isFinishing) finish()
    }

    private fun restoreAudioMode() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        }
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
    }

    override fun onDestroy() {
        endCall(remote = false)
        super.onDestroy()
    }
}
