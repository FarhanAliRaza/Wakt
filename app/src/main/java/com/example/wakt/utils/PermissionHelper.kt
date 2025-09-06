package com.example.wakt.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
// import android.net.VpnService // VPN service disabled for battery optimization
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.wakt.services.AppBlockingService
import com.example.wakt.services.WaktDeviceAdminReceiver
// import com.example.wakt.services.WebsiteBlockingVpnService // VPN service disabled

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
    
    // VPN permission functions disabled - keeping for future use
    /*
    fun isVpnPermissionGranted(context: Context): Boolean {
        val intent = VpnService.prepare(context)
        return intent == null
    }
    
    fun requestVpnPermission(activity: Activity, requestCode: Int = 1000) {
        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, requestCode)
        }
    }
    
    fun startVpnService(context: Context) {
        val intent = Intent(context, WebsiteBlockingVpnService::class.java).apply {
            action = WebsiteBlockingVpnService.ACTION_START_VPN
        }
        context.startForegroundService(intent)
    }
    
    fun stopVpnService(context: Context) {
        val intent = Intent(context, WebsiteBlockingVpnService::class.java).apply {
            action = WebsiteBlockingVpnService.ACTION_STOP_VPN
        }
        context.startService(intent)
    }
    */
    
    fun isDeviceAdminEnabled(context: Context): Boolean {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, WaktDeviceAdminReceiver::class.java)
        return devicePolicyManager.isAdminActive(adminComponent)
    }
    
    fun requestDeviceAdminPermission(activity: Activity, requestCode: Int = 2000) {
        val devicePolicyManager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(activity, WaktDeviceAdminReceiver::class.java)
        
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable Wakt protection to prevent uninstalling the app until your goals are complete. " +
                "This helps you stay committed to your digital wellness goals."
            )
        }
        activity.startActivityForResult(intent, requestCode)
    }
    
    fun areAllPermissionsGranted(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context) && 
               canDrawOverOtherApps(context)
               // VPN permission removed for battery optimization
               // && isVpnPermissionGranted(context)
               // Device admin is optional and handled separately based on goals
    }
    
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        
        if (!isAccessibilityServiceEnabled(context)) {
            missing.add("Accessibility Service")
        }
        
        if (!canDrawOverOtherApps(context)) {
            missing.add("Display over other apps")
        }
        
        // VPN permission check removed for battery optimization
        /*
        if (!isVpnPermissionGranted(context)) {
            missing.add("VPN Service")
        }
        */
        
        return missing
    }
    
    fun getMissingPermissionsWithAdmin(context: Context, includeDeviceAdmin: Boolean = false): List<String> {
        val missing = getMissingPermissions(context).toMutableList()
        
        if (includeDeviceAdmin && !isDeviceAdminEnabled(context)) {
            missing.add("Device Admin (Goal Protection)")
        }
        
        return missing
    }
}