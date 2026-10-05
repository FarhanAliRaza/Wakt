package com.farhanaliraza.wakt.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.farhanaliraza.wakt.receivers.GoalWallpaperReceiver
import com.farhanaliraza.wakt.services.GoalWallpaperService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glue around the goal live wallpaper: tells running engines to redraw, keeps
 * an after-midnight refresh scheduled, and builds the system picker intent.
 */
@Singleton
class GoalWallpaperUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoalWallpaper"
        private const val REQUEST_CODE = 4102
    }

    /** Redraws the live wallpaper if it is set. Cheap; safe to call on every change. */
    fun refresh() {
        GoalWallpaperService.notifyChanged()
    }

    /** True when Wakt's live wallpaper is the current wallpaper. */
    fun isLiveWallpaperActive(): Boolean {
        return try {
            WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    /** System screen that previews the wallpaper and lets the user apply it to home, lock or both. */
    fun pickerIntent(): Intent {
        return Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, GoalWallpaperService::class.java)
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Schedules the next after-midnight redraw so a new day shows up even if the screen stays on. */
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
}
