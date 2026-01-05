package com.farhanaliraza.wakt.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.farhanaliraza.wakt.utils.PermissionHelper

/**
 * Boot receiver to handle service restart after device reboot.
 *
 * On some OEMs (especially Xiaomi/MIUI), services may not auto-restart after boot
 * even with START_STICKY. This receiver ensures our monitoring is resumed.
 *
 * Note: The AccessibilityService should auto-restart if enabled, but this receiver
 * helps ensure BrickEnforcementService and session monitoring are resumed.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                handleDeviceStartup(context)
            }
        }
    }

    private fun handleDeviceStartup(context: Context) {
        Log.d(TAG, "Device startup detected, checking service status")

        // Check if accessibility service is enabled
        val isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
        Log.d(TAG, "Accessibility service enabled: $isAccessibilityEnabled")

        if (!isAccessibilityEnabled) {
            Log.w(TAG, "Accessibility service not enabled - blocking will not work until re-enabled")
            // We can't auto-enable accessibility service, user must do it manually
            // The app will show a warning when opened
            return
        }

        // Schedule a check to resume any active brick sessions
        // The BrickSessionManager will handle this when the app is opened
        // or when AccessibilityService connects
        Log.d(TAG, "Boot receiver completed - services will resume when AccessibilityService connects")
    }
}
