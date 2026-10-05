package com.farhanaliraza.wakt.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.farhanaliraza.wakt.utils.BrickSessionManager
import com.farhanaliraza.wakt.utils.GoalWallpaperUpdater
import com.farhanaliraza.wakt.utils.ServiceOptimizer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Brings enforcement back after a reboot or app update without relying on the
 * accessibility service (which the system restarts on its own, but which may
 * be disabled on purpose so banking apps keep working).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var serviceOptimizer: ServiceOptimizer
    @Inject lateinit var goalWallpaperUpdater: GoalWallpaperUpdater

    // Injecting the manager constructs it, which resumes any ongoing session.
    @Inject lateinit var brickSessionManager: BrickSessionManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.d(TAG, "Received $action, restoring enforcement services")
        serviceOptimizer.optimizeServices()
        goalWallpaperUpdater.refresh()
        goalWallpaperUpdater.scheduleMidnightRefresh()
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
