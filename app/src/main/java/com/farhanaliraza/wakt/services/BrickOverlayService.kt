package com.farhanaliraza.wakt.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.farhanaliraza.wakt.R
import com.farhanaliraza.wakt.utils.BrickSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that maintains a notification during brick sessions.
 * NOTE: The actual overlay is now managed by AppBlockingService using TYPE_ACCESSIBILITY_OVERLAY,
 * which does not trigger the "app is overlaying other apps" system notification.
 */
@AndroidEntryPoint
class BrickOverlayService : Service() {

    @Inject
    lateinit var brickSessionManager: BrickSessionManager

    companion object {
        private const val TAG = "BrickOverlayService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "brick_overlay_channel"

        private var instance: BrickOverlayService? = null

        // SINGLE SOURCE OF TRUTH: Should overlay be showing?
        @Volatile
        var shouldOverlayBeShowing: Boolean = false
            private set

        /**
         * Request to show overlay - delegates to AppBlockingService which uses TYPE_ACCESSIBILITY_OVERLAY
         */
        fun requestShowOverlay(context: Context) {
            shouldOverlayBeShowing = true
            AppBlockingService.requestShowBrickOverlay()
            start(context)
            Log.d(TAG, "Overlay requested - delegated to AppBlockingService")
        }

        /**
         * Request to hide overlay - called when user launches allowed app
         */
        fun requestHideForAllowedApp() {
            shouldOverlayBeShowing = false
            AppBlockingService.requestHideBrickOverlayForAllowedApp()
            Log.d(TAG, "Overlay hidden for allowed app launch")
        }

        /**
         * Request to hide overlay - called when session ends
         */
        fun requestHideForSessionEnd() {
            shouldOverlayBeShowing = false
            AppBlockingService.requestHideBrickOverlayForSessionEnd()
            Log.d(TAG, "Overlay hidden for session end")
        }

        fun shouldBeShowing(): Boolean = shouldOverlayBeShowing

        fun isPendingLaunch(packageName: String?): Boolean {
            return AppBlockingService.isPendingBrickLaunch(packageName)
        }

        fun clearPendingLaunch() {
            AppBlockingService.clearPendingBrickLaunch()
        }

        fun start(context: Context) {
            val intent = Intent(context, BrickOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrickOverlayService::class.java)
            context.stopService(intent)
            Log.d(TAG, "BrickOverlayService stopped")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BrickOverlayService created")
        createNotificationChannel()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BrickOverlayService started (foreground notification only)")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d(TAG, "Started as foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start as foreground service", e)
        }

        if (!brickSessionManager.isPhoneBricked()) {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Brick Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active brick session notification"
                setShowBadge(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Session Active")
            .setContentText("Your phone is in brick mode")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppBlockingService.requestHideBrickOverlayForSessionEnd()
        instance = null
        shouldOverlayBeShowing = false
        Log.d(TAG, "BrickOverlayService destroyed")
    }
}
