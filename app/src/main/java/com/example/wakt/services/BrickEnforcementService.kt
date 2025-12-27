package com.example.wakt.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.example.wakt.R
import com.example.wakt.presentation.activities.BrickLauncherActivity
import com.example.wakt.presentation.activities.EmergencyOverrideActivity
import com.example.wakt.utils.BrickSessionManager
import com.example.wakt.utils.EssentialAppsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@AndroidEntryPoint
class BrickEnforcementService : Service() {
    
    @Inject
    lateinit var brickSessionManager: BrickSessionManager

    @Inject
    lateinit var essentialAppsManager: EssentialAppsManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var enforcementJob: Job? = null
    private var isEnforcing = false
    private var lastKnownForegroundApp: String? = null  // Cache to prevent flickering
    private var launcherDetectedTime: Long = 0L  // When we last detected launcher (for sticky overlay)
    private var lastAllowedAppTime: Long = 0L  // When we last confirmed an allowed app was in foreground
    
    companion object {
        private const val TAG = "BrickEnforcementService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "brick_enforcement_channel"
        private const val ENFORCEMENT_INTERVAL_MS = 3000L // Check every 3 seconds - gives more time for app to launch
        
        fun start(context: Context) {
            val intent = Intent(context, BrickEnforcementService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    // If startForegroundService fails (e.g., app in background),
                    // fall back to regular startService
                    Log.w(TAG, "startForegroundService failed, falling back to startService", e)
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, BrickEnforcementService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BrickEnforcementService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BrickEnforcementService started")

        // Try to start as foreground service for persistence
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires foreground service type
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d(TAG, "Started as foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start as foreground service", e)
            // Continue without foreground - we'll still work
        }

        // Start enforcement
        startEnforcement()

        // Return START_STICKY to restart if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Mode Enforcement",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Focus Mode active"
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        // Create intent that opens BrickLauncher when notification is tapped
        val notificationIntent = Intent(this, BrickLauncherActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val currentSession = brickSessionManager.getCurrentSession()
        val sessionName = currentSession?.name ?: "Focus Mode"
        val timeText = formatRemainingTime()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 $sessionName")
            .setContentText(timeText)
            .setSmallIcon(android.R.drawable.ic_lock_lock) // Lock icon
            .setOngoing(true) // Can't be swiped away
            .setAutoCancel(false) // Don't remove when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for persistence
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    @android.annotation.SuppressLint("NotificationPermission")
    private fun startEnforcement() {
        if (isEnforcing) return

        isEnforcing = true
        enforcementJob = serviceScope.launch {
            Log.d(TAG, "Starting brick session enforcement with overlay service")

            // Debug: dump all essential apps from database
            try {
                val essentialApps = essentialAppsManager.getAllEssentialApps().firstOrNull() ?: emptyList()
                Log.d(TAG, "=== ESSENTIAL APPS IN DATABASE (${essentialApps.size} total) ===")
                essentialApps.forEach { app ->
                    Log.d(TAG, "  - ${app.appName}: ${app.packageName} (active=${app.isActive})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dumping essential apps", e)
            }

            var notificationUpdateCounter = 0
            var lastCheckedApp: String? = null
            var lastAllowedState: Boolean? = null

            while (isActive && isEnforcing) {
                try {
                    // Check if brick session is still active
                    if (!brickSessionManager.isPhoneBricked()) {
                        Log.d(TAG, "Brick session ended, stopping enforcement")
                        BrickOverlayService.stop(this@BrickEnforcementService)
                        stopSelf()
                        break
                    }

                    val currentTime = System.currentTimeMillis()

                    // Get foreground app - returns null if can't determine
                    val foregroundApp = getForegroundPackageName()

                    // If we can't determine foreground app, default to showing overlay
                    // unless we recently confirmed an allowed app (within 5 seconds)
                    if (foregroundApp == null) {
                        if (currentTime - lastAllowedAppTime > 5000L) {
                            // Haven't seen an allowed app recently - show overlay
                            if (lastAllowedState != false) {
                                Log.d(TAG, "✗ Can't detect foreground app - showing overlay")
                                lastAllowedState = false
                                BrickOverlayService.start(this@BrickEnforcementService)
                            }
                        }
                        delay(ENFORCEMENT_INTERVAL_MS)
                        notificationUpdateCounter++
                        continue
                    }

                    lastKnownForegroundApp = foregroundApp

                    // Skip if app hasn't changed
                    if (foregroundApp == lastCheckedApp && lastAllowedState != null) {
                        delay(ENFORCEMENT_INTERVAL_MS)
                        notificationUpdateCounter++
                        continue
                    }

                    lastCheckedApp = foregroundApp
                    val isAppAllowed = isEmergencyOrEssentialApp(foregroundApp) ||
                            brickSessionManager.isAppAllowedInCurrentSession(foregroundApp)

                    // Track when we last saw an allowed app
                    if (isAppAllowed) {
                        lastAllowedAppTime = currentTime
                    }

                    // Only update overlay if state changed
                    if (isAppAllowed != lastAllowedState) {
                        lastAllowedState = isAppAllowed
                        if (isAppAllowed) {
                            Log.d(TAG, "✓ Allowed: $foregroundApp - hiding overlay")
                            BrickOverlayService.hideOverlay()
                        } else {
                            Log.d(TAG, "✗ Blocked: $foregroundApp - showing overlay")
                            BrickOverlayService.start(this@BrickEnforcementService)
                        }
                    }

                    // Update notification every 20 iterations (30 seconds)
                    notificationUpdateCounter++
                    if (notificationUpdateCounter >= 20) {
                        try {
                            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.notify(NOTIFICATION_ID, createNotification())
                            notificationUpdateCounter = 0
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating notification", e)
                        }
                    }

                    delay(ENFORCEMENT_INTERVAL_MS)

                } catch (e: Exception) {
                    Log.e(TAG, "Error during enforcement", e)
                    delay(ENFORCEMENT_INTERVAL_MS)
                }
            }
        }
    }
    
    
    private fun formatRemainingTime(): String {
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds() ?: 0
        val remainingMinutes = brickSessionManager.getCurrentSessionRemainingMinutes() ?: 0

        return when {
            remainingMinutes >= 60 -> {
                val hours = remainingMinutes / 60
                val mins = remainingMinutes % 60
                if (mins > 0) "${hours}h ${mins}m remaining" else "${hours}h remaining"
            }
            remainingMinutes > 1 -> "${remainingMinutes}m remaining"
            remainingMinutes == 1 -> {
                val seconds = remainingSeconds % 60
                if (seconds > 0) "${remainingMinutes}m ${seconds}s remaining" else "1m remaining"
            }
            remainingSeconds > 0 -> "${remainingSeconds}s remaining"
            else -> "Ending now"
        }
    }

    /**
     * Get the currently active foreground app package name
     * Uses the single reliable source from AppBlockingService (AccessibilityService)
     */
    private val launcherPackages = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher"
    )

    private fun getForegroundPackageName(): String? {
        val currentTime = System.currentTimeMillis()

        // Method 1: Try AccessibilityService for real-time data (most reliable)
        val accessibilityPackage = AppBlockingService.getForegroundPackageReliably()
        if (!accessibilityPackage.isNullOrBlank()) {
            // Check if it's a launcher - return it to trigger overlay
            if (launcherPackages.any { accessibilityPackage.startsWith(it) }) {
                return accessibilityPackage
            }
            // Valid non-launcher app
            return accessibilityPackage
        }

        // Method 2: UsageStatsManager fallback
        val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return null

        val stats = usageManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_BEST,
            currentTime - 10000L,  // Only look at last 10 seconds for freshness
            currentTime
        )

        val sorted = stats.sortedByDescending { it.lastTimeUsed }
        if (sorted.isEmpty()) return null

        val topApp = sorted.first()

        // Check if data is fresh (used within last 5 seconds)
        val dataAge = currentTime - topApp.lastTimeUsed
        if (dataAge > 5000L) {
            // Data is stale - return null to trigger "can't detect" logic
            Log.d(TAG, "Stale data: ${topApp.packageName} last used ${dataAge}ms ago")
            return null
        }

        return topApp.packageName
    }

    /**
     * Check if app is an emergency or essential app that should always be allowed
     * Checks user's essential apps from database (includes Phone, Messages, user-selected apps)
     */
    private suspend fun isEmergencyOrEssentialApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false

        // Launchers - show overlay (user on home screen)
        val launcherPackages = listOf(
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher"
        )
        if (launcherPackages.any { packageName.startsWith(it) }) return false

        // System essentials - always allowed
        val safetyPackages = listOf("com.android.systemui", "com.android.settings")
        if (safetyPackages.any { packageName.startsWith(it) }) return true

        // Check user's essential apps from database
        return essentialAppsManager.isAppEssential(packageName)
    }

    private fun stopEnforcement() {
        isEnforcing = false
        enforcementJob?.cancel()
        enforcementJob = null
        Log.d(TAG, "Enforcement stopped")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopEnforcement()
        serviceScope.cancel()
        Log.d(TAG, "BrickEnforcementService destroyed")
    }
}