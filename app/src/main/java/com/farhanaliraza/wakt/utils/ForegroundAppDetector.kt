package com.farhanaliraza.wakt.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.farhanaliraza.wakt.services.AppBlockingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "which app is in the foreground right now".
 *
 * Prefers the accessibility service when it is connected (real-time events),
 * and otherwise falls back to Usage Access, which works without any
 * accessibility service and therefore keeps banking apps happy. The usage
 * path reads the activity-resumed event stream, which is far more accurate
 * than the aggregated usage stats the old fallback relied on.
 */
@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ForegroundAppDetector"
        private const val EVENT_WINDOW_MS = 120_000L
        private const val REUSE_LAST_RESULT_MS = 15_000L
        private const val STATS_FRESHNESS_MS = 10_000L
    }

    @Volatile private var lastResult: String? = null
    @Volatile private var lastResultTime = 0L

    /** True when at least one detection method can work right now. */
    fun isAvailable(): Boolean =
        AppBlockingService.isConnected() || PermissionHelper.isUsageAccessGranted(context)

    /** Best-effort foreground package, or null when it cannot be determined. */
    fun currentForegroundPackage(): String? {
        AppBlockingService.getForegroundPackageFromAccessibility()?.let { return it }
        return fromUsageAccess()
    }

    private fun fromUsageAccess(): String? {
        if (!PermissionHelper.isUsageAccessGranted(context)) return null
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()

        try {
            val events = usageStatsManager.queryEvents(now - EVENT_WINDOW_MS, now)
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // ACTIVITY_RESUMED (API 29+) shares the value of MOVE_TO_FOREGROUND.
                @Suppress("DEPRECATION")
                val resumed = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (resumed && event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            if (latestPackage != null) {
                lastResult = latestPackage
                lastResultTime = now
                return latestPackage
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents failed", e)
        }

        // Quiet period with no new events: the last answer is still the best guess.
        lastResult?.let { if (now - lastResultTime < REUSE_LAST_RESULT_MS) return it }

        return try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, now - STATS_FRESHNESS_MS, now
            )
            val top = stats?.maxByOrNull { it.lastTimeUsed } ?: return null
            if (now - top.lastTimeUsed > STATS_FRESHNESS_MS) null else top.packageName
        } catch (e: Exception) {
            Log.w(TAG, "queryUsageStats failed", e)
            null
        }
    }
}
