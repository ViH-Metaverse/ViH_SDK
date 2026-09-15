package com.vihmessenger.vihchatbot.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Cover for the voicebot barge-in thresholds.
 *
 * The dangerous failure here is a FALSE positive — the agent's own voice leaking into the mic
 * and silencing it mid-sentence — so most of these assert that barge-in does *not* fire.
 */
class BargeInDetectorTest {

    private val rate = 16000
    private val frameBytes = rate / 10 * 2 // 100 ms, as VoicebotActivity uplinks
    private val armed = 1_000L             // comfortably past the arm delay

    /** One 100 ms frame of a 300 Hz tone at [amplitude] on the int16 scale. */
    private fun tone(amplitude: Int): ByteArray {
        val buf = ByteArray(frameBytes)
        for (i in 0 until frameBytes / 2) {
            val v = (amplitude * sin(2.0 * PI * 300.0 * i / rate)).toInt()
            buf[i * 2] = (v and 0xFF).toByte()
            buf[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return buf
    }

    private fun silence() = ByteArray(frameBytes)

    private fun feed(d: BargeInDetector, frame: ByteArray, frames: Int, armedFor: Long = armed): Boolean {
        var fired = false
        repeat(frames) {
            if (d.offer(frame, frame.size, agentSpeaking = true, msSinceTurnStart = armedFor)) fired = true
        }
        return fired
    }

    @Test
    fun `sustained speech over the agent fires barge-in`() {
        // 3 x 100 ms frames == the 300 ms sustain window.
        assertTrue(feed(BargeInDetector(rate), tone(6000), frames = 3))
    }

    @Test
    fun `a single loud frame does not fire`() {
        // A cough or a door: loud, but nowhere near the sustain window.
        assertFalse(feed(BargeInDetector(rate), tone(9000), frames = 2))
    }

    @Test
    fun `silence and room noise never fire`() {
        assertFalse(feed(BargeInDetector(rate), silence(), frames = 20))
        assertFalse(feed(BargeInDetector(rate), tone(300), frames = 20))
    }

    @Test
    fun `speech broken by a quiet frame restarts the sustain window`() {
        val d = BargeInDetector(rate)
        val loud = tone(6000)
        assertFalse(feed(d, loud, frames = 2))
        assertFalse(feed(d, silence(), frames = 1))
        // Only two loud frames since the reset — still short of 300 ms.
        assertFalse(feed(d, loud, frames = 2))
    }

    @Test
    fun `nothing fires while the agent is not speaking`() {
        val d = BargeInDetector(rate)
        val loud = tone(9000)
        repeat(20) {
            assertFalse(d.offer(loud, loud.size, agentSpeaking = false, msSinceTurnStart = armed))
        }
    }

    @Test
    fun `nothing fires inside the arm delay`() {
        // Guards against the agent's own leading audio, echoed back, interrupting it.
        assertFalse(feed(BargeInDetector(rate), tone(9000), frames = 20, armedFor = 100L))
    }

    @Test
    fun `rms matches the known amplitude of a tone`() {
        // RMS of a sine is amplitude / sqrt(2).
        assertEquals(6000 / 1.4142, BargeInDetector.rms(tone(6000), frameBytes), 60.0)
        assertEquals(0.0, BargeInDetector.rms(silence(), frameBytes), 0.001)
    }
}
