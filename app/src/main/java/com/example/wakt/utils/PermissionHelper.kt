package com.example.wakt.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.wakt.services.AppBlockingService

object PermissionHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            // Method 1: Check via AccessibilityManager
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

            val serviceName = "${context.packageName}/${AppBlockingService::class.java.name}"
            Log.d("PermissionHelper", "Looking for service: $serviceName")

            val isEnabledViaManager = enabledServices.any { service ->
                val id = service.resolveInfo.serviceInfo.let { "${it.packageName}/${it.name}" }
                Log.d("PermissionHelper", "Found enabled service: $id")
                id == serviceName
            }

            // Method 2: Check via Settings (fallback)
            val enabledServicesString = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.d("PermissionHelper", "Enabled services string: $enabledServicesString")

            val isEnabledViaSettings = enabledServicesString?.contains(serviceName) == true

            val result = isEnabledViaManager || isEnabledViaSettings
            Log.d("PermissionHelper", "Accessibility service enabled: $result (manager: $isEnabledViaManager, settings: $isEnabledViaSettings)")

            result
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error checking accessibility service", e)
            false
        }
    }

    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun canDrawOverOtherApps(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Check if Usage Access permission is granted (needed for foreground app detection)
     */
    fun isUsageAccessGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (appOps != null) {
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    context.applicationInfo.uid,
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error checking usage access permission", e)
            false
        }
    }

    /**
     * Open Usage Access settings
     */
    fun requestUsageAccessPermission(context: Context) {
        Log.d("PermissionHelper", "requestUsageAccessPermission() called")
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            Log.d("PermissionHelper", "Starting Usage Access settings activity...")
            context.startActivity(intent)
            Log.d("PermissionHelper", "Opened Usage Access settings successfully")
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Failed to open Usage Access settings: ${e.message}", e)
            try {
                // Fallback: open app settings
                Log.d("PermissionHelper", "Trying fallback to app settings...")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d("PermissionHelper", "Opened app settings as fallback")
            } catch (e2: Exception) {
                Log.e("PermissionHelper", "Fallback also failed: ${e2.message}", e2)
            }
        }
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context) &&
               canDrawOverOtherApps(context)
    }

    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()

        if (!isAccessibilityServiceEnabled(context)) {
            missing.add("Accessibility Service")
        }

        if (!canDrawOverOtherApps(context)) {
            missing.add("Display over other apps")
        }

        return missing
    }
}
