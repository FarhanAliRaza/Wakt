package com.example.wakt.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.wakt.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WaktDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        showToast(context, "Wakt protection enabled - app will be protected until goals are complete")
        
        // Store admin enabled state
        val prefs = context.getSharedPreferences("wakt_admin", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("admin_enabled", true).apply()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        showToast(context, "Wakt protection disabled")
        
        // Clear admin enabled state
        val prefs = context.getSharedPreferences("wakt_admin", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("admin_enabled", false).apply()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        return "Are you sure you want to disable uninstall protection? This will make it easier to uninstall Wakt."
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}