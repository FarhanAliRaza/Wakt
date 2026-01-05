package com.farhanaliraza.wakt

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.farhanaliraza.wakt.services.ServiceWatchdogWorker
import com.farhanaliraza.wakt.utils.PermissionHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WaktApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    companion object {
        private const val TAG = "WaktApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting")

        // Schedule the service watchdog for aggressive OEMs
        scheduleServiceWatchdog()

        // Log device info for debugging
        logDeviceInfo()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    private fun scheduleServiceWatchdog() {
        try {
            ServiceWatchdogWorker.schedule(this)
            Log.d(TAG, "Service watchdog scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule service watchdog", e)
        }
    }

    private fun logDeviceInfo() {
        val manufacturer = PermissionHelper.getDeviceManufacturer()
        val isAggressiveOem = PermissionHelper.isAggressiveBatteryOem()
        val isMiui = PermissionHelper.isMiuiDevice()
        val miuiVersion = PermissionHelper.getMiuiVersion()

        Log.d(TAG, "Device: $manufacturer, Aggressive OEM: $isAggressiveOem, MIUI: $isMiui" +
                if (miuiVersion != null) ", MIUI Version: $miuiVersion" else "")
    }
}