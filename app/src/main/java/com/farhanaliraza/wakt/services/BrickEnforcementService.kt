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
import com.farhanaliraza.wakt.presentation.activities.BlockingOverlayActivity
import com.farhanaliraza.wakt.utils.AppBlockChecker
import com.farhanaliraza.wakt.utils.BrickSessionManager
import com.farhanaliraza.wakt.utils.EssentialAppsManager
import com.farhanaliraza.wakt.utils.ForegroundAppDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Polling enforcer that works without the accessibility service.
 *
 * - During a brick session it keeps the lock screen up whenever a non-allowed
 *   app is in the foreground (with or without accessibility).
 * - Outside a session it enforces individual app blocks and app schedules, but
 *   only while the accessibility service is NOT connected, since that service
 *   reacts to window events instantly and would otherwise double-trigger.
 *
 * Foreground detection comes from [ForegroundAppDetector] (accessibility when
 * available, Usage Access otherwise).
 */
@AndroidEntryPoint
class BrickEnforcementService : Service() {

    @Inject lateinit var brickSessionManager: BrickSessionManager
    @Inject lateinit var essentialAppsManager: EssentialAppsManager
    @Inject lateinit var foregroundAppDetector: ForegroundAppDetector
    @Inject lateinit var appBlockChecker: AppBlockChecker

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var enforcementJob: Job? = null
    private var isEnforcing = false

    // Brick session state
    private var lastAllowedAppTime: Long = 0L
    private var lastCheckedApp: String? = null
    private var lastAllowedState: Boolean? = null

    // App block state
    private val appBlockCooldown = mutableMapOf<String, Long>()
    private var lastBlocklistCheckTime = 0L
    private var hasAppBlocks = true

    companion object {
        private const val TAG = "BrickEnforcementService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "brick_enforcement_channel"
        private const val BRICK_INTERVAL_MS = 1000L
        private const val APP_INTERVAL_MS = 1000L
        private const val BLOCKLIST_RECHECK_MS = 15_000L
        private const val APP_BLOCK_COOLDOWN_MS = 5000L
        private const val UNKNOWN_FOREGROUND_GRACE_MS = 5000L

        fun start(context: Context) {
            val intent = Intent(context, BrickEnforcementService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Background foreground-service starts can be refused; the next
                // app open or boot will retry through ServiceOptimizer.
                Log.w(TAG, "Could not start enforcement service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BrickEnforcementService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop enforcement service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BrickEnforcementService created")
        createNotificationChannel()
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

        startEnforcement()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Blocking Enforcement",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps app blocking and focus sessions active"
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wakt is active")
            .setContentText("Blocking is enforced in the background")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startEnforcement() {
        if (isEnforcing) return
        isEnforcing = true

        enforcementJob = serviceScope.launch {
            Log.d(TAG, "Enforcement loop started")
            while (isActive && isEnforcing) {
                try {
                    if (brickSessionManager.isPhoneBricked()) {
                        enforceBrickSession()
                        delay(BRICK_INTERVAL_MS)
                        continue
                    }

                    // Not in a session. Individual app blocks belong to the
                    // accessibility service whenever it is running.
                    if (AppBlockingService.isConnected()) {
                        Log.d(TAG, "Accessibility service active and no session - stopping")
                        stopSelf()
                        break
                    }
                    if (!foregroundAppDetector.isAvailable()) {
                        Log.w(TAG, "No foreground detection available (Usage Access missing) - stopping")
                        stopSelf()
                        break
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastBlocklistCheckTime > BLOCKLIST_RECHECK_MS) {
                        hasAppBlocks = appBlockChecker.hasAnyAppBlocks()
                        lastBlocklistCheckTime = now
                        if (!hasAppBlocks) {
                            Log.d(TAG, "Nothing to enforce - stopping")
                            stopSelf()
                            break
                        }
                    }

                    enforceAppBlocks()
                    delay(APP_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during enforcement", e)
                    delay(APP_INTERVAL_MS)
                }
            }
        }
    }

    // ============== BRICK SESSION ==============

    private suspend fun enforceBrickSession() {
        val currentTime = System.currentTimeMillis()
        val foregroundApp = foregroundAppDetector.currentForegroundPackage()

        // Unknown foreground: show the lock screen unless an allowed app was
        // confirmed very recently (keeps a just-launched allowed app usable).
        if (foregroundApp == null) {
            if (currentTime - lastAllowedAppTime > UNKNOWN_FOREGROUND_GRACE_MS && lastAllowedState != false) {
                Log.d(TAG, "Foreground unknown - showing lock screen")
                lastAllowedState = false
                BrickOverlayService.requestShowOverlay(this)
            }
            return
        }

        if (foregroundApp == lastCheckedApp && lastAllowedState != null) return
        lastCheckedApp = foregroundApp

        if (BrickOverlayService.isPendingLaunch(foregroundApp)) {
            lastAllowedAppTime = currentTime
            if (lastAllowedState != true) {
                lastAllowedState = true
                BrickOverlayService.requestHideForAllowedApp()
            }
            return
        }

        val isAppAllowed = isEmergencyOrEssentialApp(foregroundApp) ||
            brickSessionManager.isAppAllowedInCurrentSession(foregroundApp)

        if (isAppAllowed) {
            lastAllowedAppTime = currentTime
            lastAllowedState = true
            Log.d(TAG, "Allowed: $foregroundApp")
            // The lock screen is only hidden by the overlay itself (allowed app
            // launched from it), by session end, or by emergency override.
        } else if (lastAllowedState != false) {
            lastAllowedState = false
            Log.d(TAG, "Blocked: $foregroundApp - showing lock screen")
            BrickOverlayService.requestShowOverlay(this)
            serviceScope.launch { brickSessionManager.logBypassAttempt() }
        }
    }

    private suspend fun isEmergencyOrEssentialApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == applicationContext.packageName) return true

        val safetyPackages = listOf("com.android.systemui", "com.android.settings", "com.android.phone")
        if (safetyPackages.any { packageName.startsWith(it) }) return true
        if (packageName.contains("inputmethod") || packageName.contains("keyboard")) return true

        val defaultDialer = essentialAppsManager.getDefaultDialerPackage()
        if (defaultDialer != null && packageName == defaultDialer) return true
        val defaultSms = essentialAppsManager.getDefaultSmsPackage()
        if (defaultSms != null && packageName == defaultSms) return true

        return essentialAppsManager.isAppEssential(packageName)
    }

    // ============== INDIVIDUAL APP BLOCKS (no accessibility) ==============

    private suspend fun enforceAppBlocks() {
        val foregroundApp = foregroundAppDetector.currentForegroundPackage() ?: return
        if (foregroundApp == applicationContext.packageName) return

        val now = System.currentTimeMillis()
        appBlockCooldown.entries.removeIf { now - it.value > 60_000L }
        val lastBlockTime = appBlockCooldown[foregroundApp] ?: 0L
        if (now - lastBlockTime < APP_BLOCK_COOLDOWN_MS) return

        val decision = appBlockChecker.check(foregroundApp) ?: return
        appBlockCooldown[foregroundApp] = now
        Log.d(TAG, "Blocked app in foreground: $foregroundApp (${decision.name})")

        val intent = Intent(this, BlockingOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra("app_name", decision.name)
            putExtra("package_name", foregroundApp)
            putExtra("challenge_type", decision.challengeType.name)
            putExtra("challenge_data", decision.challengeData)
            putExtra("is_goal_block", decision.isGoalBlock)
            putExtra("is_scheduled_block", decision.isScheduledBlock)
            putExtra("schedule_end_time", decision.scheduleEndTime)
            putExtra("is_locked", decision.isLocked)
            putExtra("lock_expires_at", decision.lockExpiresAt)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not show blocking screen for $foregroundApp", e)
        }
    }

    private fun stopEnforcement() {
        isEnforcing = false
        enforcementJob?.cancel()
        enforcementJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEnforcement()
        serviceScope.cancel()
        Log.d(TAG, "BrickEnforcementService destroyed")
    }
}
