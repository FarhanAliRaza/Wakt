package com.farhanaliraza.wakt.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.farhanaliraza.wakt.data.database.dao.PhoneBrickSessionDao
import com.farhanaliraza.wakt.utils.PermissionHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based watchdog that periodically checks if services are running
 * and restarts them if needed. This is especially important on aggressive OEMs
 * like Xiaomi/MIUI where services can be killed even with battery optimization disabled.
 *
 * This worker runs every 15 minutes to verify the accessibility service is enabled
 * and active brick sessions are being enforced.
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val phoneBrickSessionDao: PhoneBrickSessionDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val WORK_NAME = "service_watchdog"
        private const val REPEAT_INTERVAL_MINUTES = 15L

        /**
         * Schedule the periodic watchdog work.
         * Should be called when the app starts.
         */
        fun schedule(context: Context) {
            Log.d(TAG, "Scheduling service watchdog")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false) // Run even on low battery
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(TAG, "Service watchdog scheduled (every $REPEAT_INTERVAL_MINUTES minutes)")
        }

        /**
         * Cancel the watchdog work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Service watchdog cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Watchdog check running")

        return try {
            // Check if accessibility service is enabled
            val isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)

            if (!isAccessibilityEnabled) {
                Log.w(TAG, "Accessibility service is NOT enabled - blocking will not work!")
                // We can't auto-enable it, but we log this for debugging
                // The app will show a warning when opened
            } else {
                Log.d(TAG, "Accessibility service is enabled")
            }

            // Check for active brick sessions that may need enforcement
            val activeBrickSession = phoneBrickSessionDao.getCurrentlyActiveBrickSession()
            if (activeBrickSession != null) {
                val endTime = activeBrickSession.currentSessionEndTime ?: 0
                if (System.currentTimeMillis() < endTime) {
                    Log.d(TAG, "Active brick session detected: ${activeBrickSession.name}")

                    // The BrickEnforcementService should be running
                    // If accessibility service is enabled, it will handle enforcement
                    if (isAccessibilityEnabled) {
                        // Trigger a check by accessing the static method
                        // This will restart monitoring if the service was killed
                        AppBlockingService.shouldBrickOverlayBeVisible()
                        Log.d(TAG, "Triggered brick enforcement check")
                    }
                } else {
                    Log.d(TAG, "Brick session has expired")
                }
            }

            // Check for scheduled app blocks that are currently active
            val activeSchedules = phoneBrickSessionDao.getActiveSchedules()
            if (activeSchedules.isNotEmpty()) {
                Log.d(TAG, "Found ${activeSchedules.size} active schedules")
            }

            Log.d(TAG, "Watchdog check completed successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Watchdog check failed", e)
            Result.retry()
        }
    }
}
