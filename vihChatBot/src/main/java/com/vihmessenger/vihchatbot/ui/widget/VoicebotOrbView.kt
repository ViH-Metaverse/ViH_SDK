package com.vihmessenger.vihchatbot.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Round "orb" with animated rings on its circumference. The ring amplitude
 * is driven by [setSpeakingLevel] — call with a 0..1 value (e.g. from the
 * bot's audio level) to make the orb appear to "speak". When the level
 * stays at 0 the rings settle into a slow ambient breathe.
 */
class VoicebotOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val orbColorInner = Color.parseColor("#7A6CF8")
    private val orbColorOuter = Color.parseColor("#3A3299")
    private val ringColor = Color.parseColor("#A99CFF")

    /** Smoothed speaking level (0..1). [setSpeakingLevel] feeds the target. */
    private var smoothedLevel: Float = 0f
    private var targetLevel: Float = 0f

    /** Phase used to animate the ring rotation/pulse. */
    private var phase: Float = 0f

    private val phaseAnimator: ValueAnimator =
        ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 4000L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                // exponential smoothing toward target level
                smoothedLevel += (targetLevel - smoothedLevel) * 0.15f
                invalidate()
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!phaseAnimator.isStarted) phaseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        phaseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    /**
     * @param level 0..1. Higher = larger ring amplitude. Values outside the range
     *              are clamped.
     */
    fun setSpeakingLevel(level: Float) {
        targetLevel = level.coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) / 2f * 0.55f

        // Background rings — three concentric rings expanding & fading with phase.
        // Amplitude scales with smoothedLevel so silence = subtle breathe,
        // speech = pronounced rings.
        val amplitude = 0.15f + smoothedLevel * 0.55f
        for (i in 0..2) {
            val ringPhase = (phase + i * (Math.PI / 3).toFloat()) % (Math.PI * 2).toFloat()
            val expand = (sin(ringPhase.toDouble()).toFloat() + 1f) / 2f // 0..1
            val ringRadius = baseRadius * (1f + amplitude * (0.25f + 0.45f * expand))
            val alpha = ((1f - expand) * 180f * (0.4f + smoothedLevel * 0.6f)).toInt().coerceIn(0, 255)
            ringPaint.color = ringColor
            ringPaint.alpha = alpha
            ringPaint.strokeWidth = 4f + smoothedLevel * 6f
            canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        }

        // Orb body — radial gradient.
        orbPaint.shader = RadialGradient(
            cx - baseRadius * 0.3f,
            cy - baseRadius * 0.3f,
            baseRadius * 1.2f,
            intArrayOf(orbColorInner, orbColorOuter),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseRadius, orbPaint)

        // Bright "blob" on the circumference that tracks the phase, giving
        // a visual cue the orb is "active" while the bot speaks.
        if (smoothedLevel > 0.05f) {
            val blobAngle = phase * 2f
            val blobRadius = baseRadius * (0.95f + smoothedLevel * 0.15f)
            val bx = cx + cos(blobAngle.toDouble()).toFloat() * blobRadius
            val by = cy + sin(blobAngle.toDouble()).toFloat() * blobRadius
            val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    bx, by,
                    baseRadius * 0.35f * smoothedLevel.coerceAtLeast(0.3f),
                    Color.argb((220 * smoothedLevel).toInt().coerceIn(0, 255), 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(bx, by, baseRadius * 0.5f, highlight)
        }
    }
}
