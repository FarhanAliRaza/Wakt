package com.farhanaliraza.wakt.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.farhanaliraza.wakt.R
import com.farhanaliraza.wakt.utils.BrickSessionManager
import com.farhanaliraza.wakt.utils.EssentialAppsManager
import com.farhanaliraza.wakt.utils.ForegroundAppDetector
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Owns the brick-session lock screen when the accessibility service is NOT
 * enabled: a foreground service that attaches [BrickOverlayController] as a
 * TYPE_APPLICATION_OVERLAY window ("Display over other apps" permission).
 *
 * When the accessibility service IS connected, every request here is forwarded
 * to it instead, because its TYPE_ACCESSIBILITY_OVERLAY window avoids the
 * system's "displaying over other apps" notification. Callers never need to
 * know which path is active.
 */
@AndroidEntryPoint
class BrickOverlayService : Service() {

    @Inject lateinit var brickSessionManager: BrickSessionManager
    @Inject lateinit var essentialAppsManager: EssentialAppsManager
    @Inject lateinit var globalSettingsManager: GlobalSettingsManager
    @Inject lateinit var foregroundAppDetector: ForegroundAppDetector

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: BrickOverlayController? = null

    companion object {
        private const val TAG = "BrickOverlayService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "brick_overlay_channel"

        private var instance: BrickOverlayService? = null

        // SINGLE SOURCE OF TRUTH: Should the lock screen be showing?
        @Volatile
        var shouldOverlayBeShowing: Boolean = false
            private set

        /** Show the lock screen through whichever overlay path is available. */
        fun requestShowOverlay(context: Context) {
            shouldOverlayBeShowing = true
            if (AppBlockingService.isConnected()) {
                AppBlockingService.requestShowBrickOverlay()
            } else {
                instance?.showOwnOverlay()
            }
            // Keeps the foreground service alive; shows the overlay once started if needed
            start(context)
            Log.d(TAG, "Overlay requested (accessibility=${AppBlockingService.isConnected()})")
        }

        /** Hide the lock screen because the user launched an allowed app from it. */
        fun requestHideForAllowedApp() {
            shouldOverlayBeShowing = false
            AppBlockingService.requestHideBrickOverlayForAllowedApp()
            instance?.hideOwnOverlay()
            Log.d(TAG, "Overlay hidden for allowed app launch")
        }

        /** Hide the lock screen because the session ended. */
        fun requestHideForSessionEnd() {
            shouldOverlayBeShowing = false
            AppBlockingService.requestHideBrickOverlayForSessionEnd()
            instance?.hideOwnOverlay()
            Log.d(TAG, "Overlay hidden for session end")
        }

        fun shouldBeShowing(): Boolean = shouldOverlayBeShowing

        fun isPendingLaunch(packageName: String?): Boolean {
            if (AppBlockingService.isPendingBrickLaunch(packageName)) return true
            return instance?.overlay?.isPendingLaunch(packageName) == true
        }

        fun clearPendingLaunch() {
            AppBlockingService.clearPendingBrickLaunch()
            instance?.overlay?.clearPendingLaunch()
        }

        fun start(context: Context) {
            val intent = Intent(context, BrickOverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not start BrickOverlayService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BrickOverlayService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Could not stop BrickOverlayService", e)
            }
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start as foreground service", e)
        }

        if (!brickSessionManager.isPhoneBricked()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (shouldOverlayBeShowing && !AppBlockingService.isConnected()) {
            showOwnOverlay()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOwnOverlay() {
        mainHandler.post {
            if (!brickSessionManager.isPhoneBricked()) return@post
            if (!PermissionHelper.isOverlayPermissionGranted(this)) {
                Log.w(TAG, "Cannot show lock screen: 'Display over other apps' permission missing")
                return@post
            }
            if (overlay == null) {
                @Suppress("DEPRECATION")
                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                overlay = BrickOverlayController(
                    context = this,
                    windowType = windowType,
                    brickSessionManager = brickSessionManager,
                    essentialAppsManager = essentialAppsManager,
                    globalSettingsManager = globalSettingsManager,
                    foregroundPackageProvider = { foregroundAppDetector.currentForegroundPackage() },
                    onHideForAllowedApp = { requestHideForAllowedApp() },
                    onSessionEnded = { requestHideForSessionEnd() }
                )
            }
            overlay?.show()
        }
    }

    private fun hideOwnOverlay() {
        mainHandler.post { overlay?.hide() }
    }

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
        overlay?.destroy()
        overlay = null
        AppBlockingService.requestHideBrickOverlayForSessionEnd()
        instance = null
        shouldOverlayBeShowing = false
        Log.d(TAG, "BrickOverlayService destroyed")
    }
}
