package com.example.wakt.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.example.wakt.R
import com.example.wakt.presentation.activities.EmergencyOverrideActivity
import com.example.wakt.utils.BrickSessionManager
import com.example.wakt.utils.EssentialAppsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Persistent overlay service that maintains a non-dismissible overlay during brick sessions
 * Uses traditional Android Views instead of Compose for proper Service context
 */
@AndroidEntryPoint
class BrickOverlayService : Service() {

    @Inject
    lateinit var brickSessionManager: BrickSessionManager

    @Inject
    lateinit var essentialAppsManager: EssentialAppsManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var isOverlayShowing = false
    private var updateJob: Job? = null
    private var sessionMonitorJob: Job? = null

    companion object {
        private const val TAG = "BrickOverlayService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "brick_overlay_channel"

        // Singleton reference for external access to temporarily hide/show overlay
        private var instance: BrickOverlayService? = null

        // Flag to suspend overlay during emergency activities
        private var isEmergencySuspended = false

        fun start(context: Context) {
            val intent = Intent(context, BrickOverlayService::class.java)
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
            Log.d(TAG, "BrickOverlayService started")
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrickOverlayService::class.java)
            context.stopService(intent)
            Log.d(TAG, "BrickOverlayService stopped")
        }

        fun hideOverlay() {
            instance?.hideOverlay()
            Log.d(TAG, "Overlay hidden by enforcement service")
        }

        fun showOverlay() {
            instance?.showOverlay()
            Log.d(TAG, "Overlay shown by enforcement service")
        }

        fun suspendForEmergency() {
            isEmergencySuspended = true
            instance?.hideOverlay()
            Log.d(TAG, "Overlay suspended for emergency activity")
        }

        fun resumeAfterEmergency() {
            isEmergencySuspended = false
            instance?.showOverlay()
            Log.d(TAG, "Overlay resumed after emergency activity")
        }

        fun isEmergencySuspended(): Boolean = isEmergencySuspended
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BrickOverlayService created")
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BrickOverlayService started")

        // Create and start foreground service notification
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
        }

        // Show overlay if brick session is active
        if (brickSessionManager.isPhoneBricked()) {
            showOverlay()
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Brick Session Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overlay for brick session enforcement"
                setShowBadge(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val currentSession = brickSessionManager.getCurrentSession()
        val sessionName = currentSession?.name ?: "Focus Mode"
        val timeText = formatRemainingTime()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 $sessionName")
            .setContentText(timeText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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

    private fun showOverlay() {
        if (isOverlayShowing) {
            Log.d(TAG, "Overlay already showing")
            return
        }

        // Don't show overlay if suspended for emergency activities
        if (isEmergencySuspended) {
            Log.d(TAG, "Overlay suspended for emergency activity - not showing")
            return
        }

        // Check if overlay permission is granted
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "CRITICAL: Overlay permission not granted - cannot show overlay")
            Log.e(TAG, "User must grant 'Display over other apps' permission in Settings")
            showPermissionNeededNotification()
            return
        }

        try {
            // Create container for overlay using traditional Views
            overlayView = FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#FF2E2E2E")) // Dark background
                // Note: No onTouchListener - let Views handle their own touch events
                // The overlay view itself will consume touches and prevent passing through
            }

            // Build the overlay content using traditional Views
            val contentView = buildOverlayContent()
            overlayView?.addView(contentView)

            // Window parameters for overlay - TYPE_APPLICATION_OVERLAY stays on top
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                format = android.graphics.PixelFormat.TRANSLUCENT
                flags = (
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.START or Gravity.TOP
                x = 0
                y = 0
            }

            windowManager?.addView(overlayView, layoutParams)
            isOverlayShowing = true
            Log.d(TAG, "Overlay shown successfully")

            // Start update loop for time remaining
            startOverlayUpdateLoop()

            // Start session monitoring - CRITICAL: Hide overlay when session ends
            startSessionMonitoring()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
            isOverlayShowing = false
        }
    }

    private fun buildOverlayContent(): LinearLayout {
        val currentSession = brickSessionManager.getCurrentSession()

        // Main container - aligned at top with proper spacing
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setPadding(24, 40, 24, 24)
        }

        // Lock icon - larger and more prominent
        val lockIcon = TextView(this).apply {
            text = "🔒"
            textSize = 80f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }
        container.addView(lockIcon)

        // Session info card - improved styling
        if (currentSession != null) {
            val sessionCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 40
                }
                setBackgroundColor(android.graphics.Color.parseColor("#FF1F1F1F"))
                setPadding(20, 24, 20, 24)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            // Combined time display
            val timeRemaining = TextView(this).apply {
                text = formatSessionTime()
                textSize = 24f
                setTextColor(android.graphics.Color.parseColor("#FFFF6B6B"))
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            sessionCard.addView(timeRemaining)

            container.addView(sessionCard)
        }

        // Add scroll view for essentials and buttons - takes remaining space
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // Weight to take remaining space
            )
            isVerticalScrollBarEnabled = false
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
        }

        // Load essential apps asynchronously and add to overlay
        loadAndDisplayEssentialApps(scrollContent)

        // Button container - wrapped for proper centering
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(8, 16, 8, 16)
        }

        // Emergency access button - using custom view to avoid FLAG_NOT_FOCUSABLE text clipping
        if (currentSession?.allowEmergencyOverride == true) {
            val emergencyButton = createCustomButton(
                text = "⚠️ Emergency Access",
                backgroundColor = "#FFEF5350",
                marginBottom = 12
            ) {
                Log.d(TAG, "Emergency Access button clicked")
                suspendForEmergency()
                serviceScope.launch {
                    delay(100)
                    try {
                        val intent = Intent(this@BrickOverlayService, EmergencyOverrideActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        startActivity(intent)
                        Log.d(TAG, "Emergency Access activity launched")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching Emergency Access", e)
                        resumeAfterEmergency()
                    }
                }
            }
            buttonContainer.addView(emergencyButton)
        }

        // Emergency call button - using custom view to avoid FLAG_NOT_FOCUSABLE text clipping
        val callButton = createCustomButton(
            text = "📞 Emergency Call",
            backgroundColor = "#FFE53935",
            marginBottom = 0
        ) {
            Log.d(TAG, "Emergency Call button clicked")
            suspendForEmergency()
            serviceScope.launch {
                delay(100)
                try {
                    val dialerIntent = Intent(Intent.ACTION_DIAL).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(dialerIntent)
                    Log.d(TAG, "Dialer intent launched")
                    delay(10000)
                    if (brickSessionManager.isPhoneBricked()) {
                        resumeAfterEmergency()
                        Log.d(TAG, "Overlay resumed after dialer use")
                    } else {
                        resumeAfterEmergency()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching dialer", e)
                    resumeAfterEmergency()
                }
            }
        }
        buttonContainer.addView(callButton)
        scrollContent.addView(buttonContainer)

        scrollView.addView(scrollContent)
        container.addView(scrollView)

        return container
    }

    private fun formatSessionTime(): String {
        val remainingMinutes = brickSessionManager.getCurrentSessionRemainingMinutes()
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds()

        return when {
            remainingMinutes ?: 0 >= 60 -> {
                val hours = (remainingMinutes ?: 0) / 60
                val mins = (remainingMinutes ?: 0) % 60
                if (mins > 0) "${hours}h ${mins}m remaining" else "${hours}h remaining"
            }
            remainingMinutes ?: 0 > 0 -> "${remainingMinutes}m remaining"
            else -> "${remainingSeconds ?: 0}s remaining"
        }
    }

    private fun startOverlayUpdateLoop() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive && isOverlayShowing) {
                try {
                    // Update notification
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, createNotification())
                    delay(30_000) // Update every 30 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating notification", e)
                    delay(30_000)
                }
            }
        }
    }

    /**
     * CRITICAL: Monitor if brick session is still active
     * Hide overlay immediately when session ends - regardless of suspension state
     */
    private fun startSessionMonitoring() {
        sessionMonitorJob?.cancel()
        sessionMonitorJob = serviceScope.launch {
            while (isActive && isOverlayShowing) {
                try {
                    // Check every 500ms if brick session is still active
                    if (!brickSessionManager.isPhoneBricked()) {
                        Log.d(TAG, "CRITICAL: Brick session ended - hiding overlay immediately")
                        // Session ended - hide overlay no matter what (even if suspended)
                        hideOverlay()
                        // Clear suspension flag when session ends
                        isEmergencySuspended = false
                        // Stop the service
                        stopSelf()
                        break
                    }
                    delay(500)
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring session", e)
                    delay(500)
                }
            }
        }
    }

    private fun hideOverlay() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
                updateJob?.cancel()
                sessionMonitorJob?.cancel()
                Log.d(TAG, "Overlay hidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay", e)
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // Hide overlay immediately to let app come to foreground
                hideOverlay()
                Log.d(TAG, "Hidden overlay to launch allowed app: $packageName")

                startActivity(intent)
                Log.d(TAG, "Launched allowed app: $packageName")

                // Log app access
                serviceScope.launch {
                    brickSessionManager.logEssentialAppAccess(packageName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app $packageName", e)
        }
    }

    private fun showPermissionNeededNotification() {
        try {
            // Create intent to open overlay permission settings
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, permissionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⚠️ Permission Required")
                .setContentText("Grant 'Display over other apps' permission to activate focus mode")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID + 1, notification)

            Log.d(TAG, "Shown permission needed notification")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing permission notification", e)
        }
    }

    /**
     * Load session-specific allowed apps asynchronously and add them to the overlay
     * Gets the allowed apps from the current session's allowedApps field
     */
    private fun loadAndDisplayEssentialApps(scrollContent: LinearLayout) {
        serviceScope.launch {
            try {
                // Get the current session and its allowed apps
                val currentSession = brickSessionManager.getCurrentSession()

                if (currentSession != null && currentSession.allowedApps.isNotEmpty()) {
                    // Parse the comma-separated list of package names
                    val packageNames = currentSession.allowedApps
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    if (packageNames.isNotEmpty()) {
                        // Create label
                        val essentialsLabel = TextView(this@BrickOverlayService).apply {
                            text = "Allowed Apps"
                            textSize = 14f
                            setTextColor(android.graphics.Color.WHITE)
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = 8
                            }
                            typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD)
                        }
                        scrollContent.addView(essentialsLabel, 0) // Add at top

                        // Create grid container
                        val gridContainer = GridLayout(this@BrickOverlayService).apply {
                            rowCount = (packageNames.size + 3) / 4
                            columnCount = 4
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = 16
                            }
                        }

                        // Load app info for each package name from PackageManager
                        for (packageName in packageNames) {
                            try {
                                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                                val appName = packageManager.getApplicationLabel(appInfo).toString()

                                val appButton = LinearLayout(this@BrickOverlayService).apply {
                                    orientation = LinearLayout.VERTICAL
                                    gravity = Gravity.CENTER
                                    layoutParams = GridLayout.LayoutParams().apply {
                                        width = 0
                                        height = LinearLayout.LayoutParams.WRAP_CONTENT
                                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                                        setMargins(4, 4, 4, 4)
                                    }
                                    isClickable = true
                                    isFocusable = true
                                    setOnClickListener {
                                        launchApp(packageName)
                                    }
                                }

                                // App icon placeholder (first letter)
                                val appIcon = TextView(this@BrickOverlayService).apply {
                                    text = appName.firstOrNull()?.toString()?.uppercase() ?: "?"
                                    textSize = 16f
                                    setTextColor(android.graphics.Color.WHITE)
                                    gravity = Gravity.CENTER
                                    setBackgroundColor(android.graphics.Color.parseColor("#FF5555FF"))
                                    layoutParams = LinearLayout.LayoutParams(48, 48)
                                }
                                appButton.addView(appIcon)

                                // App name
                                val appNameView = TextView(this@BrickOverlayService).apply {
                                    text = appName
                                    textSize = 10f
                                    setTextColor(android.graphics.Color.WHITE)
                                    gravity = Gravity.CENTER
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        topMargin = 4
                                    }
                                    maxLines = 2
                                }
                                appButton.addView(appNameView)

                                gridContainer.addView(appButton)
                            } catch (e: Exception) {
                                Log.d(TAG, "Could not load app info for $packageName: ${e.message}")
                            }
                        }

                        scrollContent.addView(gridContainer, 1) // Add after label
                        Log.d(TAG, "Loaded ${packageNames.size} session-specific allowed apps")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading allowed apps", e)
            }
        }
    }

    /**
     * Creates a custom button view using LinearLayout + TextView to properly render text
     * This avoids rendering issues with Button widget under FLAG_NOT_FOCUSABLE
     */
    private fun createCustomButton(
        text: String,
        backgroundColor: String,
        marginBottom: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = marginBottom
                leftMargin = 16
                rightMargin = 16
            }
            setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
            setPadding(20, 20, 20, 20)
            isClickable = true
            isFocusable = true
            minimumHeight = 70

            val buttonText = TextView(this@BrickOverlayService).apply {
                this.text = text
                textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setSingleLine(false)
                maxLines = 2
                isClickable = false
            }

            addView(buttonText)
            setOnClickListener { onClick() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        updateJob?.cancel()
        sessionMonitorJob?.cancel()
        serviceScope.cancel()
        instance = null
        Log.d(TAG, "BrickOverlayService destroyed")
    }
}
