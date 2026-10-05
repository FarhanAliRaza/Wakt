package com.farhanaliraza.wakt.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.farhanaliraza.wakt.data.database.dao.DailyGoalDao
import com.farhanaliraza.wakt.data.database.entity.DailyGoal
import com.farhanaliraza.wakt.receivers.GoalWallpaperReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Paints the daily-goal streaks onto the wallpaper so the number is visible
 * without opening the app, and refreshes it shortly after midnight so a day
 * that was not checked in shows up as such.
 */
@Singleton
class GoalWallpaperUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dailyGoalDao: DailyGoalDao,
    private val globalSettingsManager: GlobalSettingsManager
) {
    companion object {
        private const val TAG = "GoalWallpaper"
        private const val REQUEST_CODE = 4102
        const val TARGET_LOCK = "lock"
        const val TARGET_HOME = "home"
        const val TARGET_BOTH = "both"
    }

    /** Re-renders the wallpaper from the current goals, in the background. Safe to call often. */
    fun refresh() {
        if (!globalSettingsManager.isGoalWallpaperEnabled()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val goals = dailyGoalDao.getActiveGoalsList().filter { it.showOnWallpaper }
                val checkIns = dailyGoalDao.getAllCheckInsList()
                val byGoal = checkIns.groupBy { it.goalId }
                    .mapValues { entry -> entry.value.associate { it.day to it.success } }
                val bitmap = render(goals, byGoal)
                apply(bitmap)
                Log.d(TAG, "Wallpaper updated for ${goals.size} goals")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update wallpaper", e)
            }
        }
    }

    /** Schedules the next after-midnight refresh (inexact, battery friendly). */
    fun scheduleMidnightRefresh() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, GoalWallpaperReceiver::class.java).apply {
                action = GoalWallpaperReceiver.ACTION_REFRESH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            val triggerAt = System.currentTimeMillis() + GoalStreaks.millisUntilNextMidnight()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule midnight refresh", e)
        }
    }

    private fun apply(bitmap: Bitmap) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val target = globalSettingsManager.getGoalWallpaperTarget()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = when (target) {
                TARGET_LOCK -> WallpaperManager.FLAG_LOCK
                TARGET_HOME -> WallpaperManager.FLAG_SYSTEM
                else -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
            }
            wallpaperManager.setBitmap(bitmap, null, true, flags)
        } else {
            wallpaperManager.setBitmap(bitmap)
        }
    }

    private fun render(goals: List<DailyGoal>, checkIns: Map<Long, Map<String, Boolean>>): Bitmap {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(720)
        val height = metrics.heightPixels.coerceAtLeast(1280)
        val density = metrics.density
        fun dp(value: Float): Float = value * density

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FF020617")) // Slate950

        // Soft backdrop blob
        val blob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#331E3A8A") }
        canvas.drawCircle(width * 0.5f, height * 0.58f, width * 0.55f, blob)

        val today = GoalStreaks.today()
        val dateText = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())

        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF94A3B8") // Slate400
            textAlign = Paint.Align.CENTER
            textSize = dp(14f)
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF1F5F9") // Slate100
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF3B82F6") // Blue500
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val status = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = dp(14f)
        }

        // Keep clear of the lock-screen clock at the top; use the middle band.
        val top = height * 0.38f
        val bottom = height * 0.86f
        val shown = goals.take(3)

        if (shown.isEmpty()) {
            title.textSize = dp(22f)
            canvas.drawText("No goals yet", width / 2f, height * 0.55f, title)
            canvas.drawText("Add one in Wakt", width / 2f, height * 0.55f + dp(28f), muted)
        } else {
            val slot = (bottom - top) / shown.size
            val numberSize = when (shown.size) { 1 -> dp(96f); 2 -> dp(64f); else -> dp(48f) }
            val titleSize = when (shown.size) { 1 -> dp(22f); 2 -> dp(18f); else -> dp(16f) }
            shown.forEachIndexed { index, goal ->
                val centerY = top + slot * index + slot / 2f
                val ins = checkIns[goal.id] ?: emptyMap()
                val streak = GoalStreaks.currentStreak(ins, today)
                val todayState = ins[today]
                val yesterdayState = ins[GoalStreaks.previousDay(today)]

                title.textSize = titleSize
                number.textSize = numberSize
                canvas.drawText(goal.title, width / 2f, centerY - numberSize * 0.55f, title)
                canvas.drawText("Day $streak", width / 2f, centerY + numberSize * 0.35f, number)

                val (statusText, statusColor) = when {
                    todayState == true -> "Today: done" to "#FF22C55E"
                    todayState == false -> "Today: slipped. Tomorrow is a new day" to "#FFEF4444"
                    yesterdayState == null && streak > 0 -> "Yesterday was not checked in" to "#FFF59E0B"
                    else -> "Today: not checked in yet" to "#FF94A3B8"
                }
                status.color = Color.parseColor(statusColor)
                canvas.drawText(statusText, width / 2f, centerY + numberSize * 0.35f + dp(26f), status)
            }
        }

        canvas.drawText(dateText, width / 2f, height * 0.33f, muted)
        muted.textSize = dp(12f)
        canvas.drawText("Wakt", width / 2f, height * 0.93f, muted)
        return bitmap
    }
}
