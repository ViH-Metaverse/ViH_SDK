package com.vihmessenger.vihchatbot.ui.activity

import ai.ultravox.UltravoxSession
import ai.ultravox.UltravoxSessionStatus
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.constants.BaseAPIConstants
import com.vihmessenger.vihchatbot.data.model.CallDetailsRequest
import com.vihmessenger.vihchatbot.data.model.LoanApprovalRequest
import com.vihmessenger.vihchatbot.databinding.ActivityVoicebotBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Voice-call screen.
 *
 * Flow:
 *  1. Caller passes EXTRA_SESSION_ID (ChatSession.session_id of the chat).
 *  2. Request RECORD_AUDIO, then POST /main/loan-approval/ to mint the
 *     Ultravox call (returns voice_ws_url + call_id).
 *  3. UltravoxSession.joinCall(url) opens the WebRTC call. Session status
 *     drives both the on-screen state text and the orb animation (no raw
 *     audio-level API on the SDK).
 *  4. On hang-up — or on a server-side DISCONNECTED — POST /main/call-details/
 *     so Prana gets a "call ended" signal and the analysis can be fetched.
 */
class VoicebotActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoicebotActivity"
        const val EXTRA_SESSION_ID = "extra_session_id"

        private const val AGENT_NAME = "ViH Shruti"

        fun startIntent(context: Context, sessionId: String): Intent {
            return Intent(context, VoicebotActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }
    }

    private lateinit var binding: ActivityVoicebotBinding
    private var session: UltravoxSession? = null
    private var callId: String? = null
    private var callDetailsFired = false
    private var speakerOn = true
    private var speakerInitialised = false

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val recordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startConnectFlow()
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
        binding = ActivityVoicebotBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.tvAgentName.text = AGENT_NAME
        binding.tvStatus.text = "Calling $AGENT_NAME…"

        binding.btnHangUp.setOnClickListener { hangUp() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }
        refreshSpeakerIcon()

        ensurePermissionAndStart()
    }

    private fun ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startConnectFlow()
        } else {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startConnectFlow() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        if (sessionId.isBlank()) {
            showErrorAndFinish("Missing session id")
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.postLoanApproval(
                    BaseAPIConstants.LOAN_APPROVAL_URL,
                    LoanApprovalRequest(session_id = sessionId)
                )
                if (!response.isSuccessful) {
                    val err = try { response.errorBody()?.string() } catch (_: Throwable) { null }
                    Log.e(TAG, "loan-approval HTTP ${response.code()} error=$err")
                    showErrorAndFinish("Failed to start call (${response.code()})")
                    return@launch
                }
                val body = response.body()
                val url = body?.voiceWsUrl?.takeIf { it.isNotBlank() } ?: body?.data?.url
                val id = body?.callId?.takeIf { it.isNotBlank() } ?: body?.data?.callId
                if (url.isNullOrBlank() || id.isNullOrBlank()) {
                    Log.e(TAG, "loan-approval body missing url/callId: $body")
                    showErrorAndFinish("Invalid loan-approval response")
                    return@launch
                }
                Log.i(TAG, "loan-approval ok: url=$url callId=$id")
                callId = id
                connectToUltravox(url)
            } catch (t: Throwable) {
                Log.e(TAG, "loan-approval failed", t)
                showErrorAndFinish("Could not reach loan-approval service")
            }
        }
    }

    private fun connectToUltravox(joinUrl: String) {
        val newSession = UltravoxSession(applicationContext, lifecycleScope)
        session = newSession

        newSession.listen("status") {
            val status = newSession.status
            Log.i(TAG, "ultravox status=$status")
            runOnUiThread { applyStatus(status) }
        }

        try {
            newSession.joinCall(joinUrl)
        } catch (t: Throwable) {
            Log.e(TAG, "ultravox joinCall failed", t)
            showErrorAndFinish("Connection failed")
        }
    }

    /**
     * Drives the orb + status text from the Ultravox session state. The SDK
     * does not expose a raw audio level, so the orb pulses at status-derived
     * fixed amplitudes.
     */
    private fun applyStatus(status: UltravoxSessionStatus) {
        // The first time the call is actually live, route audio to the loudspeaker
        // by default — voice-agent UX is hands-free.
        if (!speakerInitialised && status.live) {
            speakerInitialised = true
            applySpeakerRouting(speakerOn)
        }
        when (status) {
            UltravoxSessionStatus.DISCONNECTED -> {
                binding.orbView.setSpeakingLevel(0f)
                // Server-side disconnect — fire call-details once and finish.
                fireCallDetails()
                if (!isFinishing) finish()
            }
            UltravoxSessionStatus.DISCONNECTING -> {
                binding.orbView.setSpeakingLevel(0f)
            }
            UltravoxSessionStatus.CONNECTING -> {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = "Connecting…"
            }
            UltravoxSessionStatus.IDLE -> {
                binding.tvStatus.visibility = View.GONE
                binding.orbView.setSpeakingLevel(0.1f)
            }
            UltravoxSessionStatus.LISTENING -> {
                binding.tvStatus.visibility = View.GONE
                binding.orbView.setSpeakingLevel(0.2f)
            }
            UltravoxSessionStatus.THINKING -> {
                binding.tvStatus.visibility = View.GONE
                binding.orbView.setSpeakingLevel(0.45f)
            }
            UltravoxSessionStatus.SPEAKING -> {
                binding.tvStatus.visibility = View.GONE
                binding.orbView.setSpeakingLevel(0.8f)
            }
        }
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

    /**
     * Routes call audio between the loudspeaker and the earpiece. Uses the
     * modern [AudioManager.setCommunicationDevice] on API 31+; falls back to
     * the deprecated [AudioManager.setSpeakerphoneOn] on older devices.
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
                    Log.w(TAG, "no communication device of type=$targetType")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applySpeakerRouting failed", t)
        }
    }

    private fun toggleMute() {
        val s = session ?: return
        s.toggleMicMuted()
        val muted = s.micMuted
        binding.btnMute.isSelected = muted
        binding.btnMute.setImageResource(
            if (muted) com.vihmessenger.vihchatbot.R.drawable.ic_mic_off
            else com.vihmessenger.vihchatbot.R.drawable.ic_mic_on
        )
    }

    private fun hangUp() {
        fireCallDetails()
        teardown()
        finish()
    }

    /**
     * Fire-and-forget POST to /main/call-details/. Idempotent — guarded by
     * [callDetailsFired] so the user-hang-up path and the server-disconnect
     * status path don't both fire it. Uses GlobalScope so the request
     * completes even after the activity finishes.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun fireCallDetails() {
        if (callDetailsFired) return
        val id = callId
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        if (id.isNullOrBlank() || sessionId.isBlank()) {
            Log.w(TAG, "skip call-details: callId=$id sessionIdBlank=${sessionId.isBlank()}")
            return
        }
        callDetailsFired = true
        Log.i(TAG, "call-details firing: callId=$id sessionId=$sessionId")
        GlobalScope.launch {
            try {
                val resp = ApiClient.apiService.postCallDetails(
                    BaseAPIConstants.CALL_DETAILS_URL,
                    CallDetailsRequest(call_id = id, session_id = sessionId)
                )
                Log.i(TAG, "call-details posted: code=${resp.code()}")
            } catch (t: Throwable) {
                Log.e(TAG, "call-details failed", t)
            }
        }
    }

    private fun teardown() {
        session?.leaveCall()
        session = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        } catch (_: Throwable) {
        }
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (!callId.isNullOrBlank()) fireCallDetails()
        finish()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }
}
