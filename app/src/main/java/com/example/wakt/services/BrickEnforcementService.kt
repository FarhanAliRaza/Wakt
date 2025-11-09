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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class BrickEnforcementService : Service() {
    
    @Inject
    lateinit var brickSessionManager: BrickSessionManager
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var enforcementJob: Job? = null
    private var isEnforcing = false
    
    companion object {
        private const val TAG = "BrickEnforcementService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "brick_enforcement_channel"
        private const val ENFORCEMENT_INTERVAL_MS = 1500L // Check every 1.5 seconds
        
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
    
    private fun startEnforcement() {
        if (isEnforcing) return

        isEnforcing = true
        enforcementJob = serviceScope.launch {
            Log.d(TAG, "Starting brick session enforcement with overlay service")

            var notificationUpdateCounter = 0

            while (isActive && isEnforcing) {
                try {
                    // Check if brick session is still active
                    if (!brickSessionManager.isPhoneBricked()) {
                        Log.d(TAG, "Brick session ended, stopping enforcement")
                        // Stop overlay service
                        BrickOverlayService.stop(this@BrickEnforcementService)
                        stopSelf()
                        break
                    }

                    // Start/maintain overlay service - ensures persistent overlay is showing
                    try {
                        BrickOverlayService.start(this@BrickEnforcementService)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting overlay service", e)
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