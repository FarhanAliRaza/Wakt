package com.farhanaliraza.wakt.services

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.farhanaliraza.wakt.presentation.views.CircularTimerView
import com.farhanaliraza.wakt.utils.BrickSessionManager
import com.farhanaliraza.wakt.utils.EssentialAppsManager
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The full-screen lock screen shown during a brick session: countdown, allowed
 * apps row and the emergency exit challenge. Built with plain Views so it can be
 * attached as a system window.
 *
 * The same UI is used by two owners with different window types:
 * - [AppBlockingService] with TYPE_ACCESSIBILITY_OVERLAY when the accessibility
 *   service is enabled (no "displaying over other apps" notification).
 * - [BrickOverlayService] with TYPE_APPLICATION_OVERLAY when it is not, which
 *   only needs the "Display over other apps" permission and keeps banking apps
 *   (which refuse to run next to an accessibility service) working.
 */
class BrickOverlayController(
    private val context: Context,
    private val windowType: Int,
    private val brickSessionManager: BrickSessionManager,
    private val essentialAppsManager: EssentialAppsManager,
    private val globalSettingsManager: GlobalSettingsManager,
    private val foregroundPackageProvider: () -> String?,
    private val onHideForAllowedApp: () -> Unit,
    private val onSessionEnded: () -> Unit
) {
    companion object {
        private const val TAG = "BrickOverlayController"
        private const val OVERLAY_LAUNCH_TIMEOUT_MS = 3000L
        private const val OVERLAY_POST_LAUNCH_GRACE_MS = 2000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: FrameLayout? = null
    var isShowing = false
        private set
    private var overlayUpdateJob: Job? = null
    private var sessionMonitorJob: Job? = null
    private var circularTimerView: CircularTimerView? = null
    private var timeTextView: TextView? = null
    private var endTimeTextView: TextView? = null
    private var totalSessionSeconds: Int = 0

    // Emergency override mode
    private var isEmergencyMode = false
    private var emergencyClicksRemaining = 0
    private var emergencyClicksTextView: TextView? = null

    // Pending app launch tracking (for hiding the overlay when an allowed app launches)
    private var pendingLaunchPackage: String? = null
    private var pendingLaunchTime: Long = 0L
    private var confirmedLaunchPackage: String? = null
    private var launchConfirmedTime: Long = 0L
    private var pendingLaunchJob: Job? = null

    // ============== PUBLIC API ==============

    fun show() {
        if (isShowing) {
            Log.d(TAG, "Overlay already showing")
            return
        }

        try {
            overlayView = FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#FF2E2E2E"))
            }
            overlayView?.addView(buildOverlayContent())

            val layoutParams = WindowManager.LayoutParams().apply {
                type = windowType
                format = PixelFormat.TRANSLUCENT
                flags = (
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.START or Gravity.TOP
                x = 0
                y = 0
            }

            @Suppress("DEPRECATION")
            overlayView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

            windowManager.addView(overlayView, layoutParams)
            isShowing = true
            Log.d(TAG, "Brick overlay shown (window type $windowType)")

            startOverlayUpdateLoop()
            startSessionMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing brick overlay", e)
            overlayView = null
            isShowing = false
        }
    }

    fun hide() {
        try {
            val view = overlayView
            if (view != null) {
                windowManager.removeView(view)
                overlayView = null
                isShowing = false
                overlayUpdateJob?.cancel()
                sessionMonitorJob?.cancel()
                Log.d(TAG, "Brick overlay hidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding brick overlay", e)
            overlayView = null
            isShowing = false
        }
    }

    /** True while an allowed app launched from the overlay is still coming to the foreground. */
    fun isPendingLaunch(packageName: String?): Boolean {
        if (packageName == null) return false
        val now = System.currentTimeMillis()

        if (pendingLaunchPackage != null && packageName == pendingLaunchPackage) {
            if (now - pendingLaunchTime < OVERLAY_LAUNCH_TIMEOUT_MS) return true
        }

        if (confirmedLaunchPackage != null &&
            packageName == confirmedLaunchPackage &&
            now - launchConfirmedTime < OVERLAY_POST_LAUNCH_GRACE_MS
        ) {
            Log.d(TAG, "Within post-launch grace period - allowing $packageName")
            return true
        }

        return false
    }

    fun clearPendingLaunch() {
        pendingLaunchPackage = null
    }

    fun destroy() {
        hide()
        overlayUpdateJob?.cancel()
        sessionMonitorJob?.cancel()
        pendingLaunchJob?.cancel()
        scope.cancel()
    }

    // ============== CONTENT ==============

    private fun buildOverlayContent(): FrameLayout {
        val currentSession = brickSessionManager.getCurrentSession()

        totalSessionSeconds = if (currentSession != null &&
            currentSession.currentSessionStartTime != null &&
            currentSession.currentSessionEndTime != null
        ) {
            ((currentSession.currentSessionEndTime!! - currentSession.currentSessionStartTime!!) / 1000).toInt()
        } else {
            currentSession?.durationMinutes?.times(60) ?: 0
        }

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#FF0F172A")) // Slate950-ish
        }

        val timerCard = LinearLayout(context).apply {
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
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FF1E293B")) // Slate800
                cornerRadius = dpToPx(32).toFloat()
            }
        }

        val timerContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(220), dpToPx(220)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(32)
            }
        }

        circularTimerView = CircularTimerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setStrokeWidth(dpToPx(6).toFloat())
        }
        timerContainer.addView(circularTimerView)

        val centerContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            gravity = Gravity.CENTER
        }

        val leftTimeLabel = TextView(context).apply {
            text = "Left time"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FF94A3B8"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
        }
        centerContent.addView(leftTimeLabel)

        timeTextView = TextView(context).apply {
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

        val endTimeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val endTimeLabel = TextView(context).apply {
            text = "End Time"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#FF94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        endTimeRow.addView(endTimeLabel)

        endTimeTextView = TextView(context).apply {
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

        if (currentSession?.allowEmergencyOverride == true) {
            val isEmergencyEnabled = globalSettingsManager.isEmergencyExitEnabled()
            val buttonColor = if (isEmergencyEnabled) "#FFEF4444" else "#FF6B7280"

            val emergencyButton = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(24) }
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                isClickable = isEmergencyEnabled
                isFocusable = isEmergencyEnabled
                alpha = if (isEmergencyEnabled) 1.0f else 0.5f
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(dpToPx(1), android.graphics.Color.parseColor(buttonColor))
                    cornerRadius = dpToPx(8).toFloat()
                }
                if (isEmergencyEnabled) {
                    setOnClickListener { launchEmergencyOverride() }
                }
            }

            val warningIcon = TextView(context).apply {
                text = "⚠️"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dpToPx(8) }
            }
            emergencyButton.addView(warningIcon)

            val emergencyText = TextView(context).apply {
                text = if (isEmergencyEnabled) "Emergency Exit" else "Emergency Exit (Disabled)"
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor(buttonColor))
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

        val appsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dpToPx(48) }
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FF1E293B"))
                cornerRadius = dpToPx(24).toFloat()
            }
        }

        loadAndDisplayAllowedApps(appsContainer)
        container.addView(appsContainer)

        updateTimerDisplay()
        return container
    }

    private fun buildEmergencyContent(): FrameLayout {
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#FF1A1A1A"))
        }

        val contentLayout = LinearLayout(context).apply {
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

        contentLayout.addView(TextView(context).apply {
            text = "⚠️"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
        })

        contentLayout.addView(TextView(context).apply {
            text = "EMERGENCY OVERRIDE"
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(8) }
        })

        contentLayout.addView(TextView(context).apply {
            text = "Tap the button to end session early"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(32) }
        })

        emergencyClicksTextView = TextView(context).apply {
            text = "$emergencyClicksRemaining"
            textSize = 64f
            setTextColor(android.graphics.Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(8) }
        }
        contentLayout.addView(emergencyClicksTextView)

        contentLayout.addView(TextView(context).apply {
            text = "taps remaining"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(32) }
        })

        val tapButton = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val size = dpToPx(160)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(32)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#EF4444"))
            }
            setOnClickListener { onEmergencyTap() }
        }
        tapButton.addView(TextView(context).apply {
            text = "TAP"
            textSize = 32f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        })
        contentLayout.addView(tapButton)

        contentLayout.addView(TextView(context).apply {
            text = "Cancel"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            setOnClickListener { cancelEmergencyMode() }
        })

        container.addView(contentLayout)
        return container
    }

    // ============== HELPERS ==============

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    private fun formatCountdownTime(): String {
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds() ?: 0
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
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

    private fun startOverlayUpdateLoop() {
        overlayUpdateJob?.cancel()
        overlayUpdateJob = scope.launch {
            while (isActive && isShowing) {
                try {
                    updateTimerDisplay()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating brick overlay", e)
                }
                delay(1_000)
            }
        }
    }

    private fun startSessionMonitoring() {
        sessionMonitorJob?.cancel()
        sessionMonitorJob = scope.launch {
            while (isActive && isShowing) {
                try {
                    if (!brickSessionManager.isPhoneBricked()) {
                        Log.d(TAG, "Brick session ended - hiding overlay")
                        onSessionEnded()
                        hide()
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring brick session", e)
                }
                delay(500)
            }
        }
    }

    private fun loadAndDisplayAllowedApps(container: LinearLayout) {
        scope.launch {
            try {
                val displayedPackages = mutableSetOf<String>()

                addIntentButton(
                    container = container,
                    iconResId = android.R.drawable.sym_action_call,
                    contentDescription = "Phone",
                    intent = Intent(Intent.ACTION_DIAL).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                )

                addIntentButton(
                    container = container,
                    iconResId = android.R.drawable.sym_action_email,
                    contentDescription = "Messages",
                    intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("smsto:")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )

                // Apps allowed by this session specifically (e.g. the one app a
                // website-filter pause is locked to) come first: the lock screen
                // is the only way into them.
                val sessionApps = brickSessionManager.getCurrentSession()?.allowedApps
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                for (packageName in sessionApps) {
                    tryAddAppIcon(container, packageName, displayedPackages)
                }

                for (packageName in globalSettingsManager.getDefaultAllowedApps()) {
                    tryAddAppIcon(container, packageName, displayedPackages)
                }

                val essentialApps = essentialAppsManager.getAllEssentialApps().firstOrNull() ?: emptyList()
                for (app in essentialApps.filter { it.isUserAdded }) {
                    tryAddAppIcon(container, app.packageName, displayedPackages)
                }

                Log.d(TAG, "Displayed allowed apps: Phone, Messages + ${displayedPackages.size} apps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading allowed apps", e)
            }
        }
    }

    private fun addIntentButton(
        container: LinearLayout,
        iconResId: Int,
        contentDescription: String,
        intent: Intent
    ) {
        val appButton = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
            }
            isClickable = true
            isFocusable = true
            this.contentDescription = contentDescription
            setOnClickListener { launchIntent(intent, contentDescription) }
        }

        val iconView = ImageView(context).apply {
            setImageResource(iconResId)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(0xFFF1F5F9.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        }
        appButton.addView(iconView)
        container.addView(appButton)
    }

    private fun launchIntent(intent: Intent, label: String) {
        try {
            val targetPackage = when (label) {
                "Phone" -> essentialAppsManager.getDefaultDialerPackage()
                "Messages" -> essentialAppsManager.getDefaultSmsPackage()
                else -> null
            }

            pendingLaunchPackage = targetPackage
            pendingLaunchTime = System.currentTimeMillis()

            context.startActivity(intent)
            Log.d(TAG, "Launched: $label (target: $targetPackage)")

            scope.launch { brickSessionManager.logEssentialAppAccess(label) }

            if (targetPackage != null) {
                startPendingLaunchMonitor(targetPackage)
            } else {
                scope.launch {
                    delay(500)
                    onHideForAllowedApp()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching $label", e)
            pendingLaunchPackage = null
        }
    }

    private fun tryAddAppIcon(
        container: LinearLayout,
        packageName: String,
        displayedPackages: MutableSet<String>
    ): Boolean {
        if (displayedPackages.contains(packageName)) return false

        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appIcon = packageManager.getApplicationIcon(appInfo)

            val appButton = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                    marginStart = dpToPx(8)
                    marginEnd = dpToPx(8)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { launchApp(packageName) }
            }

            val iconView = ImageView(context).apply {
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
            true
        } catch (e: Exception) {
            Log.d(TAG, "App not found: $packageName")
            false
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            pendingLaunchPackage = packageName
            pendingLaunchTime = System.currentTimeMillis()

            context.startActivity(intent)
            Log.d(TAG, "Launched allowed app: $packageName")

            scope.launch { brickSessionManager.logEssentialAppAccess(packageName) }

            startPendingLaunchMonitor(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app $packageName", e)
            pendingLaunchPackage = null
        }
    }

    private fun startPendingLaunchMonitor(targetPackage: String) {
        pendingLaunchJob?.cancel()
        pendingLaunchJob = scope.launch {
            repeat(30) {
                delay(100)

                if (foregroundPackageProvider() == targetPackage) {
                    Log.d(TAG, "Target app $targetPackage confirmed in foreground")
                    confirmedLaunchPackage = targetPackage
                    launchConfirmedTime = System.currentTimeMillis()
                    pendingLaunchPackage = null
                    onHideForAllowedApp()
                    return@launch
                }

                if (System.currentTimeMillis() - pendingLaunchTime > OVERLAY_LAUNCH_TIMEOUT_MS) {
                    Log.d(TAG, "Launch timeout for $targetPackage")
                    pendingLaunchPackage = null
                    return@launch
                }
            }

            Log.d(TAG, "App $targetPackage never reached foreground")
            pendingLaunchPackage = null
        }
    }

    private fun launchEmergencyOverride() {
        isEmergencyMode = true
        emergencyClicksRemaining = globalSettingsManager.getClickCount()
        Log.d(TAG, "Entering emergency mode - $emergencyClicksRemaining clicks required")
        rebuildContent()
    }

    private fun rebuildContent() {
        overlayView?.let { container ->
            container.removeAllViews()
            container.addView(if (isEmergencyMode) buildEmergencyContent() else buildOverlayContent())
        }
    }

    private fun onEmergencyTap() {
        emergencyClicksRemaining--
        emergencyClicksTextView?.text = "$emergencyClicksRemaining"

        if (emergencyClicksRemaining <= 0) {
            Log.d(TAG, "Emergency override complete - ending session")
            scope.launch {
                brickSessionManager.emergencyOverride("User completed emergency challenge")
            }
        }
    }

    private fun cancelEmergencyMode() {
        isEmergencyMode = false
        emergencyClicksRemaining = 0
        rebuildContent()
    }
}
