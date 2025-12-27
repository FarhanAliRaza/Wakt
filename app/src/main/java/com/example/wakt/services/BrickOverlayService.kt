package com.example.wakt.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.example.wakt.presentation.views.CircularTimerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private var circularTimerView: CircularTimerView? = null
    private var timeTextView: TextView? = null
    private var endTimeTextView: TextView? = null
    private var totalSessionSeconds: Int = 0

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

    private fun buildOverlayContent(): FrameLayout {
        val currentSession = brickSessionManager.getCurrentSession()

        // Calculate total session duration from actual session timestamps (handles try lock seconds properly)
        totalSessionSeconds = if (currentSession != null &&
            currentSession.currentSessionStartTime != null &&
            currentSession.currentSessionEndTime != null) {
            ((currentSession.currentSessionEndTime!! - currentSession.currentSessionStartTime!!) / 1000).toInt()
        } else {
            currentSession?.durationMinutes?.times(60) ?: 0
        }

        // Main container - use FrameLayout for easier centering
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#FF0F172A")) // Dark slate background
        }

        // Card with rounded corners containing timer
        val timerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
            }
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(24), dpToPx(40), dpToPx(24), dpToPx(32))

            // Rounded background
            val cardBackground = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FF1E293B")) // Slate800
                cornerRadius = dpToPx(32).toFloat()
            }
            background = cardBackground
        }

        // Circular timer container
        val timerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(220),
                dpToPx(220)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(32)
            }
        }

        // Circular timer view
        circularTimerView = CircularTimerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setStrokeWidth(dpToPx(6).toFloat())
        }
        timerContainer.addView(circularTimerView)

        // Center content (Left time label + countdown)
        val centerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            gravity = Gravity.CENTER
        }

        val leftTimeLabel = TextView(this).apply {
            text = "Left time"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FF94A3B8")) // Slate 400
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
        }
        centerContent.addView(leftTimeLabel)

        timeTextView = TextView(this).apply {
            text = formatCountdownTime()
            textSize = 32f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        centerContent.addView(timeTextView)

        timerContainer.addView(centerContent)
        timerCard.addView(timerContainer)

        // End time row
        val endTimeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val endTimeLabel = TextView(this).apply {
            text = "End Time"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#FF94A3B8"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        endTimeRow.addView(endTimeLabel)

        endTimeTextView = TextView(this).apply {
            text = formatEndTime()
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        endTimeRow.addView(endTimeTextView)

        timerCard.addView(endTimeRow)

        // Emergency exit button - only show if session allows emergency override
        if (currentSession?.allowEmergencyOverride == true) {
            val emergencyButton = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(24)
                }
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                isClickable = true
                isFocusable = true

                // Rounded border background (outlined button style)
                val buttonBackground = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(dpToPx(1), android.graphics.Color.parseColor("#FFEF4444")) // Red border
                    cornerRadius = dpToPx(8).toFloat()
                }
                background = buttonBackground

                setOnClickListener {
                    launchEmergencyOverride()
                }
            }

            // Warning icon (using text as placeholder since we can't use vector drawables easily)
            val warningIcon = TextView(this).apply {
                text = "⚠️"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(8)
                }
            }
            emergencyButton.addView(warningIcon)

            val emergencyText = TextView(this).apply {
                text = "Emergency Exit"
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#FFEF4444")) // Red text
                typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            emergencyButton.addView(emergencyText)

            timerCard.addView(emergencyButton)
        }

        container.addView(timerCard)

        // Bottom apps container with rounded background
        val appsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dpToPx(48)
            }
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))

            // Rounded background for apps row
            val appsBackground = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FF1E293B")) // Slate800
                cornerRadius = dpToPx(24).toFloat()
            }
            background = appsBackground
        }

        // Load essential apps asynchronously
        loadAndDisplayEssentialAppsNew(appsContainer)

        container.addView(appsContainer)

        // Update timer immediately
        updateTimerDisplay()

        return container
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun formatCountdownTime(): String {
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds() ?: 0
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatEndTime(): String {
        val currentSession = brickSessionManager.getCurrentSession()
        val endTime = currentSession?.currentSessionEndTime ?: (System.currentTimeMillis() + 60000)
        val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(endTime))
    }

    private fun updateTimerDisplay() {
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds() ?: 0
        circularTimerView?.setProgress(remainingSeconds, totalSessionSeconds)
        timeTextView?.text = formatCountdownTime()
    }

    /**
     * Load essential apps - always shows Phone and Messages, plus user-added apps
     */
    private fun loadAndDisplayEssentialAppsNew(container: LinearLayout) {
        serviceScope.launch {
            try {
                val displayedPackages = mutableSetOf<String>()

                // Always show Phone first (try different package names)
                val phonePackages = listOf(
                    "com.google.android.dialer",
                    "com.android.dialer",
                    "com.samsung.android.dialer",
                    "com.android.phone"
                )
                for (pkg in phonePackages) {
                    if (tryAddAppIcon(container, pkg, displayedPackages)) break
                }

                // Always show Messages second
                val messagePackages = listOf(
                    "com.google.android.apps.messaging",
                    "com.android.messaging",
                    "com.samsung.android.messaging",
                    "com.android.mms"
                )
                for (pkg in messagePackages) {
                    if (tryAddAppIcon(container, pkg, displayedPackages)) break
                }

                // Add user-added essential apps (up to 3 more)
                val essentialApps = essentialAppsManager.getAllEssentialApps().firstOrNull() ?: emptyList()
                val userApps = essentialApps.filter { it.isUserAdded }

                for (app in userApps.take(3)) {
                    if (!displayedPackages.contains(app.packageName)) {
                        tryAddAppIcon(container, app.packageName, displayedPackages)
                    }
                }

                Log.d(TAG, "Displayed ${displayedPackages.size} essential apps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading essential apps", e)
            }
        }
    }

    /**
     * Try to add an app icon to the container. Returns true if successful.
     */
    private fun tryAddAppIcon(
        container: LinearLayout,
        packageName: String,
        displayedPackages: MutableSet<String>
    ): Boolean {
        if (displayedPackages.contains(packageName)) return false

        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appIcon = packageManager.getApplicationIcon(appInfo)

            val appButton = FrameLayout(this@BrickOverlayService).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(48),
                    dpToPx(48)
                ).apply {
                    marginStart = dpToPx(8)
                    marginEnd = dpToPx(8)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    launchApp(packageName)
                }
            }

            val iconView = ImageView(this@BrickOverlayService).apply {
                setImageDrawable(appIcon)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            appButton.addView(iconView)
            container.addView(appButton)
            displayedPackages.add(packageName)
            Log.d(TAG, "Added essential app: $packageName")
            return true

        } catch (e: Exception) {
            Log.d(TAG, "App not found: $packageName")
            return false
        }
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

    @android.annotation.SuppressLint("NotificationPermission")
    private fun startOverlayUpdateLoop() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            var notificationUpdateCounter = 0
            while (isActive && isOverlayShowing) {
                try {
                    // Update timer display every second
                    updateTimerDisplay()

                    // Update notification every 30 seconds
                    notificationUpdateCounter++
                    if (notificationUpdateCounter >= 30) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, createNotification())
                        notificationUpdateCounter = 0
                    }

                    delay(1_000) // Update every second
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating overlay", e)
                    delay(1_000)
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

    private fun launchEmergencyOverride() {
        try {
            val intent = Intent(this, EmergencyOverrideActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.d(TAG, "Launched EmergencyOverrideActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching EmergencyOverrideActivity", e)
        }
    }

    @android.annotation.SuppressLint("NotificationPermission")
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
