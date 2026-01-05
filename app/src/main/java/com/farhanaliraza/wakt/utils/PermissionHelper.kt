package com.farhanaliraza.wakt.utils

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.farhanaliraza.wakt.services.AppBlockingService

/**
 * Helper for checking and requesting permissions.
 * Note: Overlay permission (SYSTEM_ALERT_WINDOW) is no longer needed -
 * the app now uses TYPE_ACCESSIBILITY_OVERLAY which is granted automatically
 * when the AccessibilityService is enabled.
 */

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

    /**
     * Check if all required permissions are granted.
     * Note: Only Accessibility Service is required now - overlay uses TYPE_ACCESSIBILITY_OVERLAY.
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context)
    }

    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()

        if (!isAccessibilityServiceEnabled(context)) {
            missing.add("Accessibility Service")
        }

        // Note: Overlay permission no longer needed - uses TYPE_ACCESSIBILITY_OVERLAY

        return missing
    }

    // ============== BATTERY OPTIMIZATION ==============

    /**
     * Check if battery optimization is disabled for this app.
     * When disabled, Android won't kill our background services.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error checking battery optimization", e)
            false
        }
    }

    /**
     * Request Android to disable battery optimization for this app.
     * Shows a system dialog asking the user to allow.
     */
    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error requesting battery optimization exemption", e)
            // Fallback: open battery optimization settings
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * Open the battery optimization settings page
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error opening battery settings", e)
        }
    }

    /**
     * Get device manufacturer (lowercase)
     */
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }

    /**
     * Check if this is an OEM with aggressive battery optimization
     */
    fun isAggressiveBatteryOem(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer in listOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "vivo", "oneplus", "realme", "samsung", "meizu", "asus")
    }

    /**
     * Get OEM-specific battery settings intent (if available)
     */
    fun getOemBatterySettingsIntent(context: Context): Intent? {
        val manufacturer = getDeviceManufacturer()

        val intents = when {
            manufacturer in listOf("xiaomi", "redmi", "poco") -> listOf(
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            )
            manufacturer == "huawei" || manufacturer == "honor" -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            )
            manufacturer == "samsung" -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"))
            )
            manufacturer == "oppo" || manufacturer == "realme" -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
            )
            manufacturer == "vivo" -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            )
            manufacturer == "oneplus" -> listOf(
                Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
            )
            manufacturer == "asus" -> listOf(
                Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"))
            )
            manufacturer == "meizu" -> listOf(
                Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"))
            )
            else -> emptyList()
        }

        // Return first intent that can be resolved
        for (intent in intents) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(context.packageManager) != null) {
                return intent
            }
        }

        return null
    }

    /**
     * Try to open OEM-specific battery settings
     */
    fun openOemBatterySettings(context: Context): Boolean {
        val intent = getOemBatterySettingsIntent(context)
        return if (intent != null) {
            try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e("PermissionHelper", "Error opening OEM battery settings", e)
                false
            }
        } else {
            false
        }
    }

    /**
     * Get human-readable instructions for the current OEM
     */
    fun getOemBatteryInstructions(): String {
        return when (getDeviceManufacturer()) {
            "xiaomi", "redmi", "poco" -> "Settings → Apps → Manage apps → Wakt → Battery saver → No restrictions"
            "samsung" -> "Settings → Apps → Wakt → Battery → Unrestricted"
            "huawei", "honor" -> "Settings → Apps → Apps → Wakt → Battery → Launch manually → Enable all"
            "oneplus" -> "Settings → Battery → Battery optimization → Wakt → Don't optimize"
            "oppo", "realme" -> "Settings → Battery → Wakt → Allow background activity"
            "vivo" -> "Settings → Battery → High background power consumption → Wakt"
            "asus" -> "Settings → Power management → Auto-start manager → Enable Wakt"
            "meizu" -> "Settings → Apps → Wakt → Permissions → Background running"
            else -> "Settings → Apps → Wakt → Battery → Unrestricted (or similar)"
        }
    }

    // ============== NOTIFICATION PERMISSION (Android 13+) ==============

    /**
     * Check if notification permission is granted.
     * Required on Android 13+ for foreground service notifications.
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Before Android 13, notifications are allowed by default
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.areNotificationsEnabled() ?: true
        }
    }

    /**
     * Open notification settings
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error opening notification settings", e)
        }
    }

    // ============== MIUI/XIAOMI SPECIFIC ==============

    /**
     * Check if device is running MIUI
     */
    fun isMiuiDevice(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val miuiVersion = method.invoke(null, "ro.miui.ui.version.name") as? String
            !miuiVersion.isNullOrEmpty()
        } catch (e: Exception) {
            // Fallback to manufacturer check
            getDeviceManufacturer() in listOf("xiaomi", "redmi", "poco")
        }
    }

    /**
     * Get MIUI version if available
     */
    fun getMiuiVersion(): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, "ro.miui.ui.version.name") as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Try to open MIUI autostart settings
     */
    fun openMiuiAutostartSettings(context: Context): Boolean {
        val intents = listOf(
            // MIUI 13/14 autostart manager
            Intent().setComponent(ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )),
            // Alternative path
            Intent().setComponent(ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartDetailManagementActivity"
            )),
            // Older MIUI versions
            Intent().setComponent(ComponentName(
                "com.miui.permcenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ))
        )

        for (intent in intents) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.e("PermissionHelper", "Error opening MIUI autostart", e)
                }
            }
        }

        return false
    }

    /**
     * Try to open MIUI battery saver settings for the app
     */
    fun openMiuiBatterySettings(context: Context): Boolean {
        val intents = listOf(
            // Direct app battery settings
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", "Wakt")
            },
            // General power settings
            Intent().setComponent(ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            ))
        )

        for (intent in intents) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.e("PermissionHelper", "Error opening MIUI battery settings", e)
                }
            }
        }

        return false
    }

    /**
     * Get comprehensive MIUI setup instructions
     */
    fun getMiuiSetupInstructions(): List<String> {
        return listOf(
            "1. Enable Autostart:\n   Security → Autostart → Enable Wakt",
            "2. Disable Battery Restrictions:\n   Settings → Apps → Wakt → Battery saver → No restrictions",
            "3. Lock App in Recents:\n   Open Wakt → Open Recent Apps → Long press Wakt → Tap lock icon",
            "4. Disable MIUI Optimizations:\n   Settings → Additional settings → Developer options → MIUI optimization → Off",
            "5. Keep Accessibility Service:\n   Settings → Additional settings → Accessibility → Wakt → Enabled"
        )
    }

    /**
     * Get all required permissions that need attention on aggressive OEMs
     */
    fun getOemPermissionChecklist(context: Context): List<PermissionStatus> {
        val checklist = mutableListOf<PermissionStatus>()

        // Accessibility Service - always required
        checklist.add(PermissionStatus(
            name = "Accessibility Service",
            isGranted = isAccessibilityServiceEnabled(context),
            importance = PermissionImportance.CRITICAL,
            instructions = "Enable in Settings → Accessibility → Wakt"
        ))

        // Battery Optimization
        checklist.add(PermissionStatus(
            name = "Battery Optimization Disabled",
            isGranted = isBatteryOptimizationDisabled(context),
            importance = PermissionImportance.HIGH,
            instructions = getOemBatteryInstructions()
        ))

        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checklist.add(PermissionStatus(
                name = "Notification Permission",
                isGranted = isNotificationPermissionGranted(context),
                importance = PermissionImportance.HIGH,
                instructions = "Allow notifications for service indicators"
            ))
        }

        // MIUI-specific: Autostart
        if (isMiuiDevice()) {
            checklist.add(PermissionStatus(
                name = "Autostart Permission (MIUI)",
                isGranted = null, // Can't check programmatically
                importance = PermissionImportance.CRITICAL,
                instructions = "Security → Autostart → Enable Wakt"
            ))

            checklist.add(PermissionStatus(
                name = "Lock in Recent Apps (MIUI)",
                isGranted = null, // Can't check programmatically
                importance = PermissionImportance.HIGH,
                instructions = "Long press Wakt in recents → Tap lock icon"
            ))
        }

        return checklist
    }

    /**
     * Check if all critical permissions are granted
     */
    fun areAllCriticalPermissionsGranted(context: Context): Boolean {
        // Accessibility service is always critical
        if (!isAccessibilityServiceEnabled(context)) return false

        // On Android 13+, notification permission is needed for foreground services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted(context)) return false
        }

        return true
    }

    /**
     * Data class for permission status
     */
    data class PermissionStatus(
        val name: String,
        val isGranted: Boolean?, // null if can't be checked programmatically
        val importance: PermissionImportance,
        val instructions: String
    )

    enum class PermissionImportance {
        CRITICAL,  // App won't work without this
        HIGH,      // App may malfunction without this
        MEDIUM     // Recommended but not essential
    }
}
