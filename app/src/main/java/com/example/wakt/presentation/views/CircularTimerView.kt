package com.example.wakt.presentation.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * Custom View for displaying a circular countdown timer with smooth continuous animation
 * Uses real-time calculation for perfectly smooth progress
 */
class CircularTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var endTimeMillis: Long = 0
    private var totalDurationMillis: Long = 0
    private var strokeWidth: Float = 24f
    private var isRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private val frameRate = 16L // ~60fps

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                invalidate()
                handler.postDelayed(this, frameRate)
            }
        }
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#334155") // Slate700
        style = Paint.Style.STROKE
        strokeWidth = this@CircularTimerView.strokeWidth
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint().apply {
        color = Color.parseColor("#3B82F6") // Blue500 - Primary
        style = Paint.Style.STROKE
        strokeWidth = this@CircularTimerView.strokeWidth
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRect = RectF()

    /**
     * Set the timer with remaining and total seconds
     * Calculates end time for smooth real-time animation
     */
    fun setProgress(remainingSeconds: Int, totalSeconds: Int) {
        val currentTime = System.currentTimeMillis()
        endTimeMillis = currentTime + (remainingSeconds * 1000L)
        totalDurationMillis = totalSeconds * 1000L

        if (!isRunning && remainingSeconds > 0) {
            startAnimation()
        } else if (remainingSeconds <= 0) {
            stopAnimation()
        }

        invalidate()
    }

    private fun startAnimation() {
        isRunning = true
        handler.post(updateRunnable)
    }

    private fun stopAnimation() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
    }

    fun setStrokeWidth(width: Float) {
        this.strokeWidth = width
        backgroundPaint.strokeWidth = width
        progressPaint.strokeWidth = width
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) - strokeWidth

        arcRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Draw background circle
        canvas.drawArc(arcRect, 0f, 360f, false, backgroundPaint)

        // Calculate progress based on current time (smooth real-time)
        val currentTime = System.currentTimeMillis()
        val remainingMillis = (endTimeMillis - currentTime).coerceAtLeast(0)
        val progress = if (totalDurationMillis > 0) {
            remainingMillis.toFloat() / totalDurationMillis.toFloat()
        } else {
            0f
        }

        // Draw progress arc (starts from top, -90 degrees)
        val sweepAngle = 360f * progress
        if (sweepAngle > 0) {
            canvas.drawArc(arcRect, -90f, sweepAngle, false, progressPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isRunning) {
            handler.post(updateRunnable)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
