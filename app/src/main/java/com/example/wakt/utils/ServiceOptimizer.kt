package com.example.wakt.utils

import android.content.Context
import android.content.Intent
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.entity.BlockType
// import com.example.wakt.services.WebsiteBlockingVpnService // VPN service disabled for battery optimization
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
                val blockedItems = blockedItemDao.getAllBlockedItemsList()
                val hasWebsites = blockedItems.any { it.type == BlockType.WEBSITE }
                val hasApps = blockedItems.any { it.type == BlockType.APP }
                
                // VPN service management disabled for battery optimization
                // Website blocking now handled through accessibility service only
                /*
                // Manage VPN service based on blocked websites
                if (hasWebsites) {
                    startVpnServiceIfNeeded()
                } else {
                    stopVpnServiceIfRunning()
                }
                */
                
                // Note: Accessibility service lifecycle is managed by the system
                // We optimize it through configuration, not starting/stopping
                
            } catch (e: Exception) {
                // Handle error gracefully
            }
        }
    }
    
    // VPN service functions disabled - keeping for future use
    /*
    private fun startVpnServiceIfNeeded() {
        val intent = Intent(context, WebsiteBlockingVpnService::class.java).apply {
            action = WebsiteBlockingVpnService.ACTION_START_VPN
        }
        context.startService(intent)
    }
    
    private fun stopVpnServiceIfRunning() {
        val intent = Intent(context, WebsiteBlockingVpnService::class.java).apply {
            action = WebsiteBlockingVpnService.ACTION_STOP_VPN
        }
        context.startService(intent)
    }
    */
}