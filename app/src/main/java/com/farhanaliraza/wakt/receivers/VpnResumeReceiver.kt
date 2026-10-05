package com.farhanaliraza.wakt.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.ServiceOptimizer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Safety net for a website-filter pause: fires when the pause is due to end and
 * brings the DNS filter back even if the lock session that normally does so
 * never got to finish (process killed, clock changes, ...).
 */
@AndroidEntryPoint
class VpnResumeReceiver : BroadcastReceiver() {

    @Inject lateinit var globalSettingsManager: GlobalSettingsManager
    @Inject lateinit var serviceOptimizer: ServiceOptimizer

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESUME) return
        val pause = globalSettingsManager.getVpnPause()
        if (pause != null) {
            // Woke up early (e.g. inexact alarm); try again at the real end time.
            Log.d(TAG, "Pause still has ${pause.until - System.currentTimeMillis()} ms left, rescheduling")
            schedule(context, pause.until)
            return
        }
        Log.i(TAG, "Website filter pause ended, resuming filter")
        globalSettingsManager.clearVpnPause()
        serviceOptimizer.optimizeServices()
    }

    companion object {
        private const val TAG = "VpnResumeReceiver"
        private const val REQUEST_CODE = 7102
        const val ACTION_RESUME = "com.farhanaliraza.wakt.action.RESUME_WEBSITE_FILTER"

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, VpnResumeReceiver::class.java).apply { action = ACTION_RESUME }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }

        /** Schedules the resume alarm a few seconds after [atMillis] so the pause has expired by then. */
        fun schedule(context: Context, atMillis: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val triggerAt = atMillis + 2_000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not schedule filter resume", e)
            }
        }

        fun cancel(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent(context))
            } catch (e: Exception) {
                Log.w(TAG, "Could not cancel filter resume", e)
            }
        }
    }
}
