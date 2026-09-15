package com.vihmessenger.vihchatbot.utils

import kotlin.math.sqrt

/**
 * Decides when the user has started talking over the voicebot.
 *
 * The agent service never sends an `event/barge_in`, so the interruption has to be detected
 * locally from the uplink frames the call screen is already producing. Kept out of
 * `VoicebotActivity` so the thresholds — the risky part — are testable without a device.
 *
 * Deliberately conservative. A false positive silences the agent mid-sentence, which is far
 * worse than a barge-in that takes one extra frame to register, so speech must clear
 * [rmsThreshold] continuously for [sustainMs] and may not fire until [armDelayMs] into the
 * agent's turn.
 *
 * @param sampleRate  uplink rate in Hz, used to convert frame lengths to milliseconds.
 * @param rmsThreshold on the int16 scale (0–32767). Room noise sits well under 500;
 *   conversational speech into a handset runs several thousand.
 * @param sustainMs how long speech must hold, so a cough, a door or a single echo-leak frame
 *   cannot cut the agent off.
 * @param armDelayMs grace period after the turn starts, giving the platform echo canceller
 *   time to converge on the new output. Without it the agent's own voice, leaking into the
 *   mic on speakerphone, can interrupt it.
 */
class BargeInDetector(
    private val sampleRate: Int,
    private val rmsThreshold: Double = 1800.0,
    private val sustainMs: Long = 300L,
    private val armDelayMs: Long = 400L
) {
    private var voicedMs = 0L

    fun reset() {
        voicedMs = 0L
    }

    /**
     * Feed one uplink frame.
     *
     * @param buf little-endian mono s16le samples.
     * @param length valid bytes in [buf].
     * @param agentSpeaking whether the agent currently holds the floor — there is nothing to
     *   interrupt otherwise.
     * @param msSinceTurnStart elapsed time since the agent's turn began.
     * @return true exactly once, on the frame where the interruption is confirmed. The caller
     *   should flush playback; the detector resets itself.
     */
    fun offer(
        buf: ByteArray,
        length: Int,
        agentSpeaking: Boolean,
        msSinceTurnStart: Long
    ): Boolean {
        if (!agentSpeaking || msSinceTurnStart < armDelayMs) {
            voicedMs = 0L
            return false
        }
        if (rms(buf, length) < rmsThreshold) {
            voicedMs = 0L
            return false
        }
        voicedMs += (length / 2) * 1000L / sampleRate
        if (voicedMs < sustainMs) return false
        voicedMs = 0L
        return true
    }

    companion object {
        /** RMS amplitude of [length] bytes of little-endian mono s16le, on the 0–32767 scale. */
        fun rms(buf: ByteArray, length: Int): Double {
            var sum = 0.0
            var i = 0
            while (i + 1 < length) {
                val sample =
                    ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                sum += sample.toDouble() * sample.toDouble()
                i += 2
            }
            val samples = length / 2
            return if (samples == 0) 0.0 else sqrt(sum / samples)
        }
    }
}
