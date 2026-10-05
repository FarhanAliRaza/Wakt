package com.farhanaliraza.wakt.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.farhanaliraza.wakt.data.database.entity.DailyGoal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Draws the goal wallpaper: for each goal a streak number with a flame
 * (Duolingo-style) above a GitHub-style contribution grid of the last weeks.
 * Pure drawing, no Android services, so the live wallpaper engine and the
 * in-app preview share it pixel for pixel.
 */
object GoalWallpaperRenderer {

    data class GoalData(val goal: DailyGoal, val checkIns: Map<String, Boolean>)

    private val BG = 0xFF020617.toInt()          // Slate950
    private val BG_GLOW = 0x331E3A8A              // Blue900 @ 20%
    private val CELL_EMPTY = 0xFF1E293B.toInt()  // Slate800
    private val CELL_FUTURE = 0xFF0F172A.toInt() // Slate900
    private val CELL_KEPT = 0xFF3B82F6.toInt()   // Blue500
    private val CELL_KEPT_SOFT = 0xFF2563EB.toInt() // Blue600
    private val CELL_SLIPPED = 0x99EF4444.toInt() // Destructive @ 60%
    private val TEXT = 0xFFF1F5F9.toInt()        // Slate100
    private val TEXT_MUTED = 0xFF94A3B8.toInt()  // Slate400
    private val FLAME = 0xFFF59E0B.toInt()       // Warning
    private val FLAME_CORE = 0xFFFDE68A.toInt()
    private val SUCCESS = 0xFF22C55E.toInt()
    private val DANGER = 0xFFEF4444.toInt()

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

    fun render(canvas: Canvas, width: Int, height: Int, goals: List<GoalData>, now: Long = System.currentTimeMillis()) {
        val w = width.toFloat()
        val h = height.toFloat()
        val unit = w / 360f // everything scales with width (360 = a typical dp width)

        // Background with a soft glow in the middle band
        canvas.drawColor(BG)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w / 2f, h * 0.55f, w * 0.75f, BG_GLOW, 0x00000000, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, glow)

        val shown = goals.take(3)
        val top = h * 0.30f      // keep the lock-screen clock area clear
        val bottom = h * 0.88f

        if (shown.isEmpty()) {
            drawEmpty(canvas, w, h, unit)
            drawWordmark(canvas, w, h, unit)
            return
        }

        val slot = (bottom - top) / shown.size
        val weeks = when (shown.size) { 1 -> 16; 2 -> 12; else -> 10 }
        shown.forEachIndexed { index, data ->
            drawGoal(canvas, w, unit, top + slot * index, slot, data, weeks, now, compact = shown.size > 1)
        }
        drawWordmark(canvas, w, h, unit)
    }

    private fun drawEmpty(canvas: Canvas, w: Float, h: Float, unit: Float) {
        val title = textPaint(TEXT, 22f * unit, bold = true).apply { textAlign = Paint.Align.CENTER }
        val body = textPaint(TEXT_MUTED, 14f * unit).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("No goals yet", w / 2f, h * 0.55f, title)
        canvas.drawText("Add one in Wakt's Goals tab", w / 2f, h * 0.55f + 28f * unit, body)
    }

    private fun drawWordmark(canvas: Canvas, w: Float, h: Float, unit: Float) {
        val paint = textPaint(TEXT_MUTED, 11f * unit).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
            alpha = 150
        }
        canvas.drawText("WAKT", w / 2f, h * 0.94f, paint)
    }

    private fun drawGoal(
        canvas: Canvas,
        w: Float,
        unit: Float,
        slotTop: Float,
        slotHeight: Float,
        data: GoalData,
        weeks: Int,
        now: Long,
        compact: Boolean
    ) {
        val today = GoalStreaks.dayKey(now)
        val streak = GoalStreaks.currentStreak(data.checkIns, today)
        val todayState = data.checkIns[today]

        val margin = 28f * unit
        val gridWidth = w - margin * 2
        val gap = 3f * unit
        val cell = (gridWidth - gap * (weeks - 1)) / weeks
        val gridHeight = cell * 7 + gap * 6

        val numberSize = if (compact) 56f * unit else 84f * unit
        val titleSize = if (compact) 14f * unit else 16f * unit
        val labelSize = 11f * unit

        // Vertical layout inside the slot: title / number / grid / status
        val headerHeight = titleSize * 1.6f + numberSize * 1.15f
        val statusHeight = labelSize * 2.4f
        val contentHeight = headerHeight + 18f * unit + gridHeight + statusHeight
        var y = slotTop + (slotHeight - contentHeight) / 2f

        // Title
        val titlePaint = textPaint(TEXT_MUTED, titleSize, bold = true).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        y += titleSize * 1.2f
        canvas.drawText(data.goal.title.uppercase(Locale.getDefault()), w / 2f, y, titlePaint)

        // Flame + streak number + "DAY STREAK"
        val numberPaint = textPaint(TEXT, numberSize, bold = true)
        val numberText = streak.toString()
        val numberWidth = numberPaint.measureText(numberText)
        val flameSize = numberSize * 0.72f
        val labelPaint = textPaint(TEXT_MUTED, labelSize, bold = true).apply { letterSpacing = 0.2f }
        val labelText = if (streak == 1) "DAY STREAK" else "DAY STREAK"
        val labelWidth = labelPaint.measureText(labelText)
        val groupWidth = flameSize + 10f * unit + numberWidth + 12f * unit + labelWidth
        val groupLeft = (w - groupWidth) / 2f
        y += numberSize * 1.0f
        val baseline = y
        drawFlame(canvas, groupLeft, baseline - flameSize * 0.95f, flameSize, streak > 0)
        canvas.drawText(numberText, groupLeft + flameSize + 10f * unit, baseline, numberPaint)
        canvas.drawText(
            labelText,
            groupLeft + flameSize + 10f * unit + numberWidth + 12f * unit,
            baseline - numberSize * 0.08f,
            labelPaint
        )

        // Contribution grid
        y += 18f * unit
        drawGrid(canvas, margin, y, cell, gap, weeks, data, today, now, unit)
        y += gridHeight

        // Status line
        val (statusText, statusColor) = when (todayState) {
            true -> "Today: kept it" to SUCCESS
            false -> "Today: slipped. Tomorrow is a new day." to DANGER
            null -> if (streak > 0 && data.checkIns[GoalStreaks.previousDay(today)] == null) {
                "Yesterday was not checked in" to FLAME
            } else {
                "Not checked in today" to TEXT_MUTED
            }
        }
        val statusPaint = textPaint(statusColor, labelSize * 1.15f).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(statusText, w / 2f, y + labelSize * 1.9f, statusPaint)
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        top: Float,
        cell: Float,
        gap: Float,
        weeks: Int,
        data: GoalData,
        today: String,
        now: Long,
        unit: Float
    ) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val todayRow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0 .. Sun=6
        // First cell is the Monday (weeks-1) weeks before this week's Monday
        cal.add(Calendar.DAY_OF_MONTH, -((weeks - 1) * 7 + todayRow))
        val createdDay = GoalStreaks.dayKey(data.goal.createdAt)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * unit
            color = TEXT
        }
        val monthPaint = textPaint(TEXT_MUTED, 9f * unit)
        val radius = cell * 0.22f
        var lastMonth = -1

        for (col in 0 until weeks) {
            for (row in 0 until 7) {
                val dayKey = dayFormat.format(cal.time)
                val isFuture = dayKey > today
                val x = left + col * (cell + gap)
                val y = top + row * (cell + gap)
                val rect = RectF(x, y, x + cell, y + cell)

                if (row == 0) {
                    val month = cal.get(Calendar.MONTH)
                    if (month != lastMonth && (col == 0 || cal.get(Calendar.DAY_OF_MONTH) <= 7)) {
                        canvas.drawText(monthFormat.format(cal.time), x, top - 6f * unit, monthPaint)
                        lastMonth = month
                    }
                }

                cellPaint.color = when {
                    isFuture -> CELL_FUTURE
                    dayKey < createdDay -> CELL_FUTURE
                    data.checkIns[dayKey] == true -> if ((col + row) % 2 == 0) CELL_KEPT else CELL_KEPT_SOFT
                    data.checkIns[dayKey] == false -> CELL_SLIPPED
                    else -> CELL_EMPTY
                }
                canvas.drawRoundRect(rect, radius, radius, cellPaint)
                if (dayKey == today) canvas.drawRoundRect(rect, radius, radius, ringPaint)

                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    /** Teardrop flame; grey when the streak is zero. */
    private fun drawFlame(canvas: Canvas, left: Float, top: Float, size: Float, lit: Boolean) {
        val outer = Path().apply {
            moveTo(0.5f, 0f)
            cubicTo(0.5f, 0.05f, 0.88f, 0.38f, 0.88f, 0.64f)
            cubicTo(0.88f, 0.86f, 0.71f, 1f, 0.5f, 1f)
            cubicTo(0.29f, 1f, 0.12f, 0.86f, 0.12f, 0.64f)
            cubicTo(0.12f, 0.5f, 0.22f, 0.4f, 0.3f, 0.28f)
            cubicTo(0.33f, 0.46f, 0.42f, 0.52f, 0.43f, 0.52f)
            cubicTo(0.4f, 0.35f, 0.5f, 0.15f, 0.5f, 0f)
            close()
        }
        val inner = Path().apply {
            moveTo(0.5f, 0.5f)
            cubicTo(0.5f, 0.55f, 0.68f, 0.68f, 0.68f, 0.8f)
            cubicTo(0.68f, 0.9f, 0.6f, 0.97f, 0.5f, 0.97f)
            cubicTo(0.4f, 0.97f, 0.32f, 0.9f, 0.32f, 0.8f)
            cubicTo(0.32f, 0.7f, 0.42f, 0.62f, 0.5f, 0.5f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size, size)
        paint.color = if (lit) FLAME else CELL_EMPTY
        canvas.drawPath(outer, paint)
        paint.color = if (lit) FLAME_CORE else CELL_FUTURE
        canvas.drawPath(inner, paint)
        canvas.restore()
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

    /** Convenience for previews: today's data map from raw check-ins. */
    fun dayKeyOf(date: Date): String = dayFormat.format(date)
}
