package com.farhanaliraza.wakt.utils

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.farhanaliraza.wakt.data.database.dao.BlockedItemDao
import com.farhanaliraza.wakt.data.database.entity.BlockType
import com.farhanaliraza.wakt.services.WebsiteBlockingVpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service optimizer for battery efficiency
 * Manages service lifecycle based on blocked items
 */
@Singleton
class ServiceOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedItemDao: BlockedItemDao
) {

    fun optimizeServices() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentTime = System.currentTimeMillis()
                val blockedItems = blockedItemDao.getAllBlockedItemsList()
                val hasWebsites = blockedItems.any {
                    it.type == BlockType.WEBSITE &&
                        (it.blockEndTime == null || it.blockEndTime!! > currentTime)
                }

                // DNS-only VPN: only DNS queries are routed through the TUN,
                // so running it has no impact on throughput or battery.
                if (hasWebsites) {
                    startVpnServiceIfPossible()
                } else {
                    stopVpnServiceIfRunning()
                }

                // Note: Accessibility service lifecycle is managed by the system
                // We optimize it through configuration, not starting/stopping

            } catch (e: Exception) {
                Log.e("ServiceOptimizer", "Error optimizing services", e)
            }
        }
    }

    private fun startVpnServiceIfPossible() {
        // VpnService.prepare() returns null once the user has granted VPN
        // consent (requested from the UI when a website block is added).
        if (VpnService.prepare(context) != null) {
            Log.d("ServiceOptimizer", "VPN consent not granted yet, skipping VPN start")
            return
        }
        try {
            val intent = Intent(context, WebsiteBlockingVpnService::class.java).apply {
                action = WebsiteBlockingVpnService.ACTION_START_VPN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("ServiceOptimizer", "Failed to start VPN service", e)
        }
    }

    private fun stopVpnServiceIfRunning() {
        // Don't spin the service up just to deliver a stop action
        if (!WebsiteBlockingVpnService.isServiceRunning) return
        try {
            val intent = Intent(context, WebsiteBlockingVpnService::class.java).apply {
                action = WebsiteBlockingVpnService.ACTION_STOP_VPN
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("ServiceOptimizer", "Failed to stop VPN service", e)
        }
    }
}
