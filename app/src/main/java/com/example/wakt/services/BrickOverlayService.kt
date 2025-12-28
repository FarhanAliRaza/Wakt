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

    @Inject
    lateinit var globalSettingsManager: com.example.wakt.utils.GlobalSettingsManager

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

    // Emergency override mode - show click challenge inline
    private var isEmergencyMode = false
    private var emergencyClicksRemaining = 0  // Will be set from global settings
    private var emergencyClicksTextView: TextView? = null

    companion object {
        private const val TAG = "BrickOverlayService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "brick_overlay_channel"
        private const val LAUNCH_TIMEOUT_MS = 3000L
        private const val POST_LAUNCH_GRACE_MS = 2000L // Grace period after app detected

        // Singleton reference for external access to temporarily hide/show overlay
        private var instance: BrickOverlayService? = null


        // Track pending app launch - don't block until app opens or times out
        @Volatile
        var pendingLaunchPackage: String? = null
        private var pendingLaunchTime: Long = 0L

        // Track when app was confirmed in foreground - grace period to prevent launcher flash
        @Volatile
        private var launchConfirmedTime: Long = 0L
        @Volatile
        private var confirmedLaunchPackage: String? = null

        // SINGLE SOURCE OF TRUTH: Should overlay be showing?
        // Only modified by: session start, allowed app launch, session end, emergency override
        @Volatile
        var shouldOverlayBeShowing: Boolean = false
            private set

        /**
         * Request to show overlay - called when session starts or blocked app detected
         */
        fun requestShowOverlay(context: Context) {
            shouldOverlayBeShowing = true
            start(context)
            Log.d(TAG, "Overlay requested to show (shouldOverlayBeShowing=true)")
        }

        /**
         * Request to hide overlay - called ONLY when user launches allowed app from overlay
         */
        fun requestHideForAllowedApp() {
            shouldOverlayBeShowing = false
            instance?.hideOverlayInternal()
            Log.d(TAG, "Overlay hidden for allowed app launch (shouldOverlayBeShowing=false)")
        }

        /**
         * Request to hide overlay - called when session ends
         */
        fun requestHideForSessionEnd() {
            shouldOverlayBeShowing = false
            instance?.hideOverlayInternal()
            Log.d(TAG, "Overlay hidden for session end (shouldOverlayBeShowing=false)")
        }

        /**
         * Check if overlay should currently be visible
         */
        fun shouldBeShowing(): Boolean = shouldOverlayBeShowing

        fun isPendingLaunch(packageName: String?): Boolean {
            if (packageName == null) return false

            val now = System.currentTimeMillis()

            // Check if still waiting for app to launch
            if (pendingLaunchPackage != null && packageName == pendingLaunchPackage) {
                if (now - pendingLaunchTime < LAUNCH_TIMEOUT_MS) {
                    return true
                }
            }

            // Check if within grace period after app was confirmed
            // Only allow the SPECIFIC app that was launched, not any app
            if (confirmedLaunchPackage != null &&
                packageName == confirmedLaunchPackage &&
                now - launchConfirmedTime < POST_LAUNCH_GRACE_MS) {
                Log.d(TAG, "Within post-launch grace period - allowing $packageName")
                return true
            }

            return false
        }

        fun clearPendingLaunch() {
            pendingLaunchPackage = null
            // Don't clear confirmed launch - let it expire naturally
        }

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

        /**
         * @deprecated Use requestHideForAllowedApp() or requestHideForSessionEnd() instead
         */
        fun hideOverlay() {
            Log.w(TAG, "hideOverlay() called directly - use requestHideForAllowedApp() or requestHideForSessionEnd() instead")
            // Don't hide if we're supposed to be showing
            if (shouldOverlayBeShowing) {
                Log.d(TAG, "Ignoring hideOverlay() - shouldOverlayBeShowing is true")
                return
            }
            instance?.hideOverlay()
        }

        /**
         * @deprecated Use requestShowOverlay() instead
         */
        fun showOverlay() {
            Log.w(TAG, "showOverlay() called directly - use requestShowOverlay() instead")
            shouldOverlayBeShowing = true
            instance?.showOverlay()
        }

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
     * Load essential apps - always shows Phone and Messages (via intents), plus user-configured apps
     */
    private fun loadAndDisplayEssentialAppsNew(container: LinearLayout) {
        serviceScope.launch {
            try {
                val displayedPackages = mutableSetOf<String>()

                // Add Phone button with intent (works with any dialer)
                addIntentButton(
                    container = container,
                    iconResId = android.R.drawable.sym_action_call,
                    contentDescription = "Phone",
                    intent = Intent(Intent.ACTION_DIAL).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )

                // Add Messages button with intent (works with any SMS app)
                addIntentButton(
                    container = container,
                    iconResId = android.R.drawable.sym_action_email,
                    contentDescription = "Messages",
                    intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("smsto:")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )

                // Add default allowed apps from Settings (GlobalSettingsManager)
                val defaultAllowedApps = globalSettingsManager.getDefaultAllowedApps()
                for (packageName in defaultAllowedApps) {
                    if (!displayedPackages.contains(packageName)) {
                        tryAddAppIcon(container, packageName, displayedPackages)
                    }
                }

                // Add user-added essential apps from database
                val essentialApps = essentialAppsManager.getAllEssentialApps().firstOrNull() ?: emptyList()
                val userApps = essentialApps.filter { it.isUserAdded }

                for (app in userApps) {
                    if (!displayedPackages.contains(app.packageName)) {
                        tryAddAppIcon(container, app.packageName, displayedPackages)
                    }
                }

                Log.d(TAG, "Displayed essential apps: Phone, Messages + ${displayedPackages.size} user apps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading essential apps", e)
            }
        }
    }

    /**
     * Add a button that launches an intent with a custom icon
     */
    private fun addIntentButton(
        container: LinearLayout,
        iconResId: Int,
        contentDescription: String,
        intent: Intent
    ) {
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
            this.contentDescription = contentDescription
            setOnClickListener {
                launchIntent(intent, contentDescription)
            }
        }

        val iconView = ImageView(this@BrickOverlayService).apply {
            setImageResource(iconResId)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Tint to match theme
            setColorFilter(0xFFF1F5F9.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        }
        appButton.addView(iconView)
        container.addView(appButton)
        Log.d(TAG, "Added intent button: $contentDescription")
    }

    /**
     * Launch an intent (for Phone/Messages) - keeps overlay visible until app opens
     */
    private fun launchIntent(intent: Intent, label: String) {
        try {
            // Determine target package for monitoring
            val targetPackage = when (label) {
                "Phone" -> essentialAppsManager.getDefaultDialerPackage()
                "Messages" -> essentialAppsManager.getDefaultSmsPackage()
                else -> null
            }

            // Mark pending launch - don't hide overlay yet
            pendingLaunchPackage = targetPackage
            pendingLaunchTime = System.currentTimeMillis()

            startActivity(intent)
            Log.d(TAG, "Launched: $label (target: $targetPackage) - waiting for foreground")

            // Log access
            serviceScope.launch {
                brickSessionManager.logEssentialAppAccess(label)
            }

            // Start monitoring for app to appear
            if (targetPackage != null) {
                startPendingLaunchMonitor(targetPackage)
            } else {
                // No target package known, hide after short delay
                serviceScope.launch {
                    delay(500)
                    requestHideForAllowedApp()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching $label", e)
            pendingLaunchPackage = null
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
                        // Session ended - update state and hide overlay
                        shouldOverlayBeShowing = false
                        hideOverlayInternal()
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

    /**
     * Internal method to actually hide the overlay view
     * Should only be called by proper state management methods
     */
    private fun hideOverlayInternal() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
                updateJob?.cancel()
                sessionMonitorJob?.cancel()
                Log.d(TAG, "Overlay view removed (isOverlayShowing=false)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay", e)
        }
    }

    /**
     * Hide overlay - wrapper that calls internal method
     * Used by companion object methods
     */
    private fun hideOverlay() {
        hideOverlayInternal()
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // Mark pending launch - don't hide overlay yet
                pendingLaunchPackage = packageName
                pendingLaunchTime = System.currentTimeMillis()

                startActivity(intent)
                Log.d(TAG, "Launched allowed app: $packageName - waiting for foreground")

                // Log app access
                serviceScope.launch {
                    brickSessionManager.logEssentialAppAccess(packageName)
                }

                // Start monitoring for app to appear
                startPendingLaunchMonitor(packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app $packageName", e)
            pendingLaunchPackage = null
        }
    }

    private var pendingLaunchJob: Job? = null

    /**
     * Monitor for target app to reach foreground, then hide overlay
     * Maintains a grace period after detection to prevent launcher flash
     */
    private fun startPendingLaunchMonitor(targetPackage: String) {
        pendingLaunchJob?.cancel()
        pendingLaunchJob = serviceScope.launch {
            // Poll every 100ms for up to 3 seconds
            repeat(30) {
                delay(100)

                val foreground = AppBlockingService.getForegroundPackageReliably()
                if (foreground == targetPackage) {
                    // App is now in foreground - record confirmation time for grace period
                    Log.d(TAG, "Target app $targetPackage confirmed in foreground - hiding for allowed app")
                    confirmedLaunchPackage = targetPackage
                    launchConfirmedTime = System.currentTimeMillis()
                    pendingLaunchPackage = null

                    // Hide overlay using proper method (updates shouldOverlayBeShowing)
                    requestHideForAllowedApp()
                    return@launch
                }

                // Check timeout
                if (System.currentTimeMillis() - pendingLaunchTime > LAUNCH_TIMEOUT_MS) {
                    Log.d(TAG, "Launch timeout for $targetPackage")
                    pendingLaunchPackage = null
                    return@launch
                }
            }

            // Timeout - app didn't open
            Log.d(TAG, "App $targetPackage never reached foreground")
            pendingLaunchPackage = null
        }
    }

    private fun launchEmergencyOverride() {
        // Switch to emergency mode - show click challenge inline
        isEmergencyMode = true
        emergencyClicksRemaining = globalSettingsManager.getClickCount()
        Log.d(TAG, "Entering emergency mode - $emergencyClicksRemaining clicks required")

        // Rebuild the overlay to show emergency challenge
        rebuildOverlayContent()
    }

    private fun rebuildOverlayContent() {
        overlayView?.let { container ->
            // Remove existing content
            container.removeAllViews()

            // Build new content based on mode
            val contentView = if (isEmergencyMode) {
                buildEmergencyContent()
            } else {
                buildOverlayContent()
            }
            container.addView(contentView)
        }
    }

    private fun buildEmergencyContent(): FrameLayout {
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#FF1A1A1A")) // Darker background for emergency
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = dpToPx(32)
                marginEnd = dpToPx(32)
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Warning icon
        val warningText = TextView(this).apply {
            text = "⚠️"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
        }
        contentLayout.addView(warningText)

        // Title
        val titleText = TextView(this).apply {
            text = "EMERGENCY OVERRIDE"
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor("#EF4444")) // Red
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }
        contentLayout.addView(titleText)

        // Subtitle
        val subtitleText = TextView(this).apply {
            text = "Tap the button to end session early"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(32)
            }
        }
        contentLayout.addView(subtitleText)

        // Clicks remaining counter
        emergencyClicksTextView = TextView(this).apply {
            text = "$emergencyClicksRemaining"
            textSize = 64f
            setTextColor(android.graphics.Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }
        contentLayout.addView(emergencyClicksTextView)

        // "taps remaining" label
        val tapsLabel = TextView(this).apply {
            text = "taps remaining"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(32)
            }
        }
        contentLayout.addView(tapsLabel)

        // Big TAP button
        val tapButton = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val size = dpToPx(160)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(32)
            }
            val gradientDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#EF4444"))
            }
            background = gradientDrawable

            setOnClickListener {
                onEmergencyTap()
            }
        }

        val tapText = TextView(this).apply {
            text = "TAP"
            textSize = 32f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        tapButton.addView(tapText)
        contentLayout.addView(tapButton)

        // Cancel button
        val cancelButton = TextView(this).apply {
            text = "Cancel"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setOnClickListener {
                cancelEmergencyMode()
            }
        }
        contentLayout.addView(cancelButton)

        container.addView(contentLayout)
        return container
    }

    private fun onEmergencyTap() {
        emergencyClicksRemaining--
        emergencyClicksTextView?.text = "$emergencyClicksRemaining"

        if (emergencyClicksRemaining <= 0) {
            // Emergency override complete
            Log.d(TAG, "Emergency override complete - ending session")
            serviceScope.launch {
                brickSessionManager.emergencyOverride("User completed emergency challenge")
            }
        }
    }

    private fun cancelEmergencyMode() {
        isEmergencyMode = false
        emergencyClicksRemaining = 0
        Log.d(TAG, "Emergency mode cancelled")
        rebuildOverlayContent()
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
        hideOverlayInternal()
        updateJob?.cancel()
        sessionMonitorJob?.cancel()
        serviceScope.cancel()
        instance = null
        shouldOverlayBeShowing = false
        Log.d(TAG, "BrickOverlayService destroyed")
    }
}
