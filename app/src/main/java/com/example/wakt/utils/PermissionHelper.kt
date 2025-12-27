package com.example.wakt.utils

import android.accessibilityservice.AccessibilityServiceInfo
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
