package com.farhanaliraza.wakt.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Uninstall and force-stop protection.
 *
 * While Wakt is an active device admin, Android disables the "Force stop"
 * button on its App Info page and refuses to uninstall it until the admin is
 * deactivated. Wakt requests no policies at all (see res/xml/device_admin.xml);
 * it only needs to be active. Deactivation cannot be prevented, but
 * [onDisableRequested] puts a warning in the system's confirmation dialog.
 */
class WaktDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled: force stop and uninstall are now protected")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "This protection is what stops Force stop and Uninstall from ending your " +
            "blocks, locks and focus sessions early. Turning it off defeats the point of " +
            "a commitment lock: if one is running, you will be able to kill Wakt and " +
            "get the blocked apps back right away.\n\n" +
            "If you only need Wakt gone temporarily, use the filter pause or the " +
            "early-unlock flow inside Wakt instead. They keep you honest."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled: Wakt can be force stopped and uninstalled again")
    }

    companion object {
        private const val TAG = "WaktDeviceAdmin"

        fun componentName(context: Context): ComponentName =
            ComponentName(context, WaktDeviceAdminReceiver::class.java)
    }
}
