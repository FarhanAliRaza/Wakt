package com.example.wakt.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.wakt.services.WaktDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceAdminManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, WaktDeviceAdminReceiver::class.java)

    fun isDeviceAdminEnabled(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    fun createEnableDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable Wakt protection to prevent uninstalling the app until your goals are complete. " +
                "This helps you stay committed to your digital wellness goals."
            )
        }
    }

    fun disableDeviceAdmin() {
        if (isDeviceAdminEnabled()) {
            devicePolicyManager.removeActiveAdmin(adminComponent)
            Toast.makeText(context, "Device admin disabled", Toast.LENGTH_SHORT).show()
        }
    }
}