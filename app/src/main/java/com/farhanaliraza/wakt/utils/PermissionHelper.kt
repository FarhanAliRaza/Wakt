package com.farhanaliraza.wakt.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.farhanaliraza.wakt.receivers.WaktDeviceAdminReceiver
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

    // ============== UNINSTALL / FORCE-STOP PROTECTION (device admin) ==============

    /** Wakt is an active device admin: Force stop is greyed out and Uninstall refused. */
    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(WaktDeviceAdminReceiver.componentName(context))
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error checking device admin", e)
            false
        }
    }

    /**
     * Opens the system's "Activate device admin" screen for Wakt.
     *
     * The system activity refuses to start as a new task ("Cannot start
     * ADD_DEVICE_ADMIN as a new task" in Settings' DeviceAdminAdd) and finishes
     * at once, which brings whatever Settings page was open last back to the
     * front. So, unlike the other permission requests here, this one must be
     * started from an Activity without FLAG_ACTIVITY_NEW_TASK.
     */
    fun requestDeviceAdmin(context: Context) {
        val activity = findActivity(context)
        if (activity == null) {
            Log.e("PermissionHelper", "Device admin activation needs an Activity context")
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, WaktDeviceAdminReceiver.componentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Keeps Wakt running: Android will no longer let you force stop or uninstall it " +
                    "until this is turned off again. Wakt asks for no device policies."
            )
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Could not open device admin activation, falling back to security settings", e)
            try {
                activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (e2: Exception) {
                Log.e("PermissionHelper", "Could not open security settings", e2)
            }
        }
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return null
    }

    /** Opens Wakt's own App Info page (where "Allow restricted settings" lives). */
    fun openAppInfo(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Could not open app info", e)
        }
    }

    /** Shown when the device admin screen did not appear or activation was refused. */
    const val RESTRICTED_SETTINGS_HINT =
        "If Android said this setting is restricted: for apps installed outside an " +
            "app store it is hidden behind \"Restricted settings\". Open Wakt's App info, " +
            "tap the three dots at the top right, choose \"Allow restricted settings\", " +
            "then tap Turn on again."

    /**
     * Deactivates Wakt's device admin. Callers must only offer this while no
     * commitment lock or lock session is running; the system itself cannot
     * tell the two situations apart.
     */
    fun removeDeviceAdmin(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.removeActiveAdmin(WaktDeviceAdminReceiver.componentName(context))
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Could not remove device admin", e)
        }
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

    // ============== OVERLAY ("Display over other apps") ==============

    const val PERMISSION_USAGE_ACCESS = "Usage Access"
    const val PERMISSION_OVERLAY = "Display over other apps"

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Failed to open overlay permission settings", e)
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e("PermissionHelper", "Fallback to app settings failed", e2)
            }
        }
    }

    // ============== PERMISSION MODEL ==============
    //
    // Two ways to run, so banking apps (which refuse to work while any
    // third-party accessibility service is enabled) stay usable:
    //
    //  Standard : Usage Access (foreground detection) + Display over other apps
    //             (lock screen). Nothing else required.
    //  Enhanced : Accessibility Service alone covers both, reacts instantly and
    //             adds URL-bar website blocking in browsers. Optional.

    /** Something can tell us which app is in the foreground. */
    fun hasForegroundDetection(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context) || isUsageAccessGranted(context)
    }

    /** Something can draw the lock screen over other apps. */
    fun canShowLockScreen(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context) || isOverlayPermissionGranted(context)
    }

    /** Check if the app can enforce blocks right now, through either setup. */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return hasForegroundDetection(context) && canShowLockScreen(context)
    }

    /**
     * Permissions still needed for the standard (accessibility-free) setup.
     * Empty when the accessibility service is enabled, since it covers everything.
     */
    fun getMissingPermissions(context: Context): List<String> {
        if (isAccessibilityServiceEnabled(context)) return emptyList()
        val missing = mutableListOf<String>()
        if (!isUsageAccessGranted(context)) missing.add(PERMISSION_USAGE_ACCESS)
        if (!isOverlayPermissionGranted(context)) missing.add(PERMISSION_OVERLAY)
        return missing
    }

    /**
     * Some OEMs additionally gate starting a screen from the background behind
     * their own toggle. Without it the challenge screen cannot appear over a
     * blocked app in the standard setup.
     */
    fun getBackgroundPopupInstructions(): String? {
        return when (getDeviceManufacturer()) {
            "xiaomi", "redmi", "poco" ->
                "Settings → Apps → Manage apps → Wakt → Other permissions → " +
                    "\"Display pop-up windows while running in the background\" → Allow"
            "vivo" -> "Settings → Apps → Wakt → Permissions → \"Background pop-ups\" → Allow"
            "oppo", "realme", "oneplus" -> "Settings → Apps → Wakt → \"Allow pop-up windows in background\""
            else -> null
        }
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
}
