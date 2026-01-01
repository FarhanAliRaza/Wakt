package com.farhanaliraza.wakt.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.farhanaliraza.wakt.R
import com.farhanaliraza.wakt.data.database.dao.BlockedItemDao
import com.farhanaliraza.wakt.data.database.dao.GoalBlockDao
import com.farhanaliraza.wakt.data.database.dao.GoalBlockItemDao
import com.farhanaliraza.wakt.data.database.dao.PhoneBrickSessionDao
import com.farhanaliraza.wakt.data.database.entity.BlockType
import com.farhanaliraza.wakt.data.database.entity.ChallengeType
import com.farhanaliraza.wakt.data.database.entity.PhoneBrickSession
import com.farhanaliraza.wakt.presentation.activities.BlockingOverlayActivity
import com.farhanaliraza.wakt.presentation.activities.BrickBlockingOverlay
import com.farhanaliraza.wakt.presentation.views.CircularTimerView
import com.farhanaliraza.wakt.utils.BrickSessionManager
import com.farhanaliraza.wakt.utils.EssentialAppsManager
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.TemporaryUnlock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockingService : AccessibilityService() {
    
    @Inject
    lateinit var blockedItemDao: BlockedItemDao
    
    @Inject
    lateinit var goalBlockDao: GoalBlockDao
    
    @Inject
    lateinit var goalBlockItemDao: GoalBlockItemDao

    @Inject
    lateinit var phoneBrickSessionDao: PhoneBrickSessionDao

    @Inject
    lateinit var temporaryUnlock: TemporaryUnlock
    
    @Inject
    lateinit var brickSessionManager: BrickSessionManager

    @Inject
    lateinit var essentialAppsManager: EssentialAppsManager

    @Inject
    lateinit var globalSettingsManager: GlobalSettingsManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Brick overlay properties (using TYPE_ACCESSIBILITY_OVERLAY - no "overlaying other apps" notification)
    private var windowManager: WindowManager? = null
    private var brickOverlayView: FrameLayout? = null
    private var isBrickOverlayShowing = false
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

    // Pending app launch tracking (for hiding overlay when allowed app launches)
    private var pendingLaunchPackage: String? = null
    private var pendingLaunchTime: Long = 0L
    private var confirmedLaunchPackage: String? = null
    private var launchConfirmedTime: Long = 0L
    private var pendingLaunchJob: Job? = null
    private var lastCheckedPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val blockedWebsiteCooldown = mutableMapOf<String, Long>()
    private val blockedAppCooldown = mutableMapOf<String, Long>()
    private val lastContentChangeTime = mutableMapOf<String, Long>() // Battery optimization: Rate limiting
    
    // Track active overlays to prevent duplicate blocking
    private val activeOverlays = mutableMapOf<String, Long>() // key: url, value: timestamp when overlay was shown
    
    // Enhanced app monitoring
    private val activeBlockedApps = mutableSetOf<String>() // Apps currently being blocked
    private var monitoringJob: Job? = null
    
    companion object {
        private const val TAG = "AppBlockingService"
        private const val CHECK_DELAY_MS = 1000L // Delay to avoid too frequent checks
        private const val WEBSITE_BLOCK_COOLDOWN_MS = 2000L // 2 seconds cooldown for tab close animation
        private const val APP_BLOCK_COOLDOWN_MS = 5000L // 5 seconds cooldown for app blocks

        // Overlay constants
        private const val OVERLAY_LAUNCH_TIMEOUT_MS = 3000L
        private const val OVERLAY_POST_LAUNCH_GRACE_MS = 2000L

        // SINGLE SOURCE OF TRUTH: Should overlay be showing?
        @Volatile
        var shouldBrickOverlayBeShowing: Boolean = false
            private set

        // Static reference to the service instance for clearing cooldowns and getting foreground package
        private var instance: AppBlockingService? = null

        // Last known foreground package from accessibility events (fallback when other methods fail)
        private var lastKnownForegroundPackage: String? = null

        /**
         * Update the last known foreground package (called from accessibility events)
         */
        fun updateLastForegroundPackage(packageName: String?) {
            if (!packageName.isNullOrBlank()) {
                lastKnownForegroundPackage = packageName
                Log.d(TAG, "Updated lastKnownForegroundPackage: $packageName")
            }
        }

        /**
         * Get the foreground package name - single reliable source for all services
         * PRIORITY: AccessibilityService (real-time) > UsageStatsManager (delayed)
         */
        fun getForegroundPackageReliably(): String? {
            // Method 1: Use lastKnownForegroundPackage from accessibility events (REAL-TIME)
            // This is updated immediately when TYPE_WINDOW_STATE_CHANGED fires
            if (!lastKnownForegroundPackage.isNullOrBlank()) {
                return lastKnownForegroundPackage
            }

            // Method 2: Try rootInActiveWindow (AccessibilityService)
            try {
                val packageName = instance?.rootInActiveWindow?.packageName?.toString()
                if (!packageName.isNullOrBlank()) {
                    Log.d(TAG, "getForegroundPackageReliably: Got from rootInActiveWindow: $packageName")
                    return packageName
                }
            } catch (e: Exception) {
                Log.e(TAG, "getForegroundPackageReliably: rootInActiveWindow failed: ${e.message}")
            }

            // Method 3: UsageStatsManager as last resort (has delays)
            val context = instance ?: return null
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                    if (usageManager != null) {
                        val currentTime = System.currentTimeMillis()
                        val queryUsageStats = usageManager.queryUsageStats(
                            android.app.usage.UsageStatsManager.INTERVAL_BEST,
                            currentTime - 5000L,
                            currentTime
                        )

                        if (queryUsageStats.isNotEmpty()) {
                            val sorted = queryUsageStats.sortedByDescending { it.lastTimeUsed }
                            val packageName = sorted.firstOrNull()?.packageName
                            if (!packageName.isNullOrBlank()) {
                                Log.d(TAG, "getForegroundPackageReliably: Got from UsageStatsManager: $packageName")
                                return packageName
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "UsageStatsManager failed: ${e.message}")
            }

            return null
        }
        
        fun clearWebsiteCooldown(url: String, browserPackage: String) {
            instance?.blockedWebsiteCooldown?.remove("$url|$browserPackage")
        }
        
        fun onOverlayDismissed(url: String?, browserPackage: String?) {
            // Clear overlay tracking when overlay is dismissed
            if (url != null) {
                instance?.activeOverlays?.remove(url)
                // Also try to close the tab immediately
                if (browserPackage != null) {
                    instance?.closeBlockedWebsiteTab(browserPackage)
                }
            }
        }
        
        private fun getBrowserPackages(): List<String> {
            return listOf(
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "org.mozilla.firefox",
                "com.microsoft.emmx", // Edge
                "com.opera.browser",
                "com.brave.browser",
                "com.duckduckgo.mobile.android",
                "com.samsung.android.sbrowser", // Samsung Internet
                "com.UCMobile.intl", // UC Browser
                "com.android.browser" // Stock browser
            )
        }

        // ============== BRICK OVERLAY CONTROL METHODS ==============

        /**
         * Request to show brick overlay - uses TYPE_ACCESSIBILITY_OVERLAY (no system notification!)
         */
        fun requestShowBrickOverlay() {
            shouldBrickOverlayBeShowing = true
            instance?.showBrickOverlayInternal()
            Log.d(TAG, "Brick overlay requested to show (shouldBrickOverlayBeShowing=true)")
        }

        /**
         * Request to hide overlay - called when user launches allowed app from overlay
         */
        fun requestHideBrickOverlayForAllowedApp() {
            shouldBrickOverlayBeShowing = false
            instance?.hideBrickOverlayInternal()
            Log.d(TAG, "Brick overlay hidden for allowed app launch (shouldBrickOverlayBeShowing=false)")
        }

        /**
         * Request to hide overlay - called when session ends
         */
        fun requestHideBrickOverlayForSessionEnd() {
            shouldBrickOverlayBeShowing = false
            instance?.hideBrickOverlayInternal()
            Log.d(TAG, "Brick overlay hidden for session end (shouldBrickOverlayBeShowing=false)")
        }

        /**
         * Check if brick overlay should be visible
         */
        fun shouldBrickOverlayBeVisible(): Boolean = shouldBrickOverlayBeShowing

        /**
         * Check if pending launch for package (used during brick mode)
         */
        fun isPendingBrickLaunch(packageName: String?): Boolean {
            if (packageName == null) return false
            val inst = instance ?: return false

            val now = System.currentTimeMillis()

            // Check if still waiting for app to launch
            if (inst.pendingLaunchPackage != null && packageName == inst.pendingLaunchPackage) {
                if (now - inst.pendingLaunchTime < OVERLAY_LAUNCH_TIMEOUT_MS) {
                    return true
                }
            }

            // Check if within grace period after app was confirmed
            if (inst.confirmedLaunchPackage != null &&
                packageName == inst.confirmedLaunchPackage &&
                now - inst.launchConfirmedTime < OVERLAY_POST_LAUNCH_GRACE_MS) {
                Log.d(TAG, "Within post-launch grace period - allowing $packageName")
                return true
            }

            return false
        }

        /**
         * Clear pending launch tracking
         */
        fun clearPendingBrickLaunch() {
            instance?.pendingLaunchPackage = null
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "AppBlockingService connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100 // Faster response for better blocking
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info

        // Start periodic monitoring for blocked apps
        startPeriodicAppMonitoring()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            val packageName = it.packageName?.toString()

            // Update last known foreground package for all window state changes
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !packageName.isNullOrBlank()) {
                updateLastForegroundPackage(packageName)
            }

            // PRIORITY: Check brick mode for ALL events
            if (brickSessionManager.isPhoneBricked()) {
                // Always allow our own app
                if (packageName == applicationContext.packageName) {
                    return
                }

                // For any other app during brick mode, check if it's essential
                if (packageName != null && it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    Log.d(TAG, "Checking brick mode access for: $packageName")
                    handleBrickedPhoneAccess(packageName)
                    return // Don't process normal blocking logic during brick mode
                }
            }
            
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(it)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    handleContentChanged(it)
                }
                // Monitor when views gain focus (app comes to foreground)
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handleWindowActivated(it)
                }
            }
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        
        if (packageName.isNullOrEmpty()) {
            return
        }
        
        Log.d(TAG, "Window state changed for package: $packageName")
        
        // Check if phone is currently bricked - PRIORITY CHECK
        if (brickSessionManager.isPhoneBricked()) {
            // Immediate enforcement for brick mode
            handleBrickedPhoneAccess(packageName)
            return // Don't process normal blocks during brick mode
        }
        
        // Normal blocking logic for individual apps
        checkIfAppIsBlocked(packageName, forceCheck = false)
    }
    
    private fun handleWindowActivated(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        
        if (packageName.isNullOrEmpty()) {
            return
        }
        
        Log.d(TAG, "Window activated for package: $packageName")
        
        // Check if phone is currently bricked
        if (brickSessionManager.isPhoneBricked()) {
            handleBrickedPhoneAccess(packageName)
        } else {
            // Force check when window is activated (app comes to foreground)
            checkIfAppIsBlocked(packageName, forceCheck = true)
        }
    }
    
    private fun handleContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        
        // Battery optimization: Only process browser events and limit frequency
        if (!isBrowserApp(packageName)) return
        
        // Rate limit content change processing to avoid excessive CPU usage
        val currentTime = System.currentTimeMillis()
        if (currentTime - (lastContentChangeTime[packageName] ?: 0) < 1000) {
            return // Skip if less than 1 second since last processing for this package
        }
        lastContentChangeTime[packageName!!] = currentTime
        
        // Only process if this is the active tab
        if (!isActiveTab(event)) {
            Log.d(TAG, "Skipping background tab in $packageName")
            return
        }
        
        extractUrlFromBrowser(event)?.let { url ->
            Log.d(TAG, "Active browser tab URL detected: $url in $packageName")
            checkIfWebsiteIsBlocked(url, packageName, event)
        }
    }
    
    private fun isActiveTab(event: AccessibilityEvent): Boolean {
        try {
            // Check if the source node is visible and focused
            val source = event.source ?: return false
            
            // Check if the window is active
            val window = source.window
            if (window != null && !window.isActive) {
                return false
            }
            
            // Check if the node is visible to user and either focused or its window is focused
            val isVisible = source.isVisibleToUser
            val isFocused = source.isFocused || source.isAccessibilityFocused
            
            // For browsers, also check if we're looking at the main content area
            // not a background tab
            return isVisible && (isFocused || window?.isActive == true)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if tab is active", e)
            // Default to true to maintain blocking functionality
            return true
        }
    }
    
    private fun isBrowserApp(packageName: String?): Boolean {
        val browserPackages = listOf(
            "com.android.chrome",
            "com.chrome.beta", 
            "com.chrome.dev",
            "org.mozilla.firefox",
            "com.microsoft.emmx", // Edge
            "com.opera.browser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.samsung.android.sbrowser", // Samsung Internet
            "com.UCMobile.intl", // UC Browser
            "com.android.browser" // Stock browser
        )
        return browserPackages.contains(packageName)
    }
    
    private fun isSystemLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Common system launcher packages
        val systemLaunchers = listOf(
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher", // Pixel Launcher
            "com.samsung.android.app.launcher", // Samsung OneUI Home
            "com.miui.home", // MIUI Launcher
            "com.huawei.android.launcher", // Huawei Launcher
            "com.oppo.launcher", // OPPO Launcher
            "com.oneplus.launcher", // OnePlus Launcher
            "com.android.settings" // Settings app
        )
        
        return systemLaunchers.any { packageName.startsWith(it) } ||
               packageName.contains("launcher") && isSystemApp(packageName)
    }
    
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the default dialer package dynamically
     */
    private fun getDefaultDialerPackage(): String? {
        return try {
            val telecomManager = getSystemService(android.content.Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            telecomManager?.defaultDialerPackage
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default dialer", e)
            null
        }
    }

    /**
     * Get the default SMS package dynamically
     */
    private fun getDefaultSmsPackage(): String? {
        return try {
            android.provider.Telephony.Sms.getDefaultSmsPackage(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default SMS app", e)
            null
        }
    }

    private fun extractUrlFromBrowser(event: AccessibilityEvent): String? {
        try {
            val rootNode = rootInActiveWindow ?: return null
            
            // Try to find URL bar by common resource IDs and content descriptions
            val urlCandidates = mutableListOf<String>()
            
            // Search for nodes that might contain URLs
            findUrlNodes(rootNode, urlCandidates)
            
            // Return the most likely URL (longest valid URL)
            return urlCandidates
                .filter { it.contains(".") && (it.startsWith("http") || !it.contains(" ")) }
                .maxByOrNull { it.length }
                ?.let { cleanUrl(it) }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting URL from browser", e)
            return null
        }
    }
    
    private fun findUrlNodes(node: AccessibilityNodeInfo?, urlCandidates: MutableList<String>) {
        node?.let { 
            // Check if this node contains URL-like text
            val text = it.text?.toString()
            val contentDesc = it.contentDescription?.toString()
            
            // Add text that looks like URLs
            text?.let { t ->
                if (t.contains(".") && (t.startsWith("http") || t.contains("www.") || isValidDomain(t))) {
                    urlCandidates.add(t)
                }
            }
            
            contentDesc?.let { desc ->
                if (desc.contains(".") && (desc.startsWith("http") || desc.contains("www.") || isValidDomain(desc))) {
                    urlCandidates.add(desc)
                }
            }
            
            // Check resource ID for URL bar indicators
            val resourceId = try { it.viewIdResourceName } catch (e: Exception) { null }
            if (resourceId?.contains("url") == true || resourceId?.contains("address") == true) {
                text?.let { t -> if (t.isNotBlank()) urlCandidates.add(t) }
            }
            
            // Recursively search child nodes
            for (i in 0 until it.childCount) {
                findUrlNodes(it.getChild(i), urlCandidates)
            }
        }
    }
    
    private fun isValidDomain(text: String): Boolean {
        return text.contains(".") && 
               !text.contains(" ") && 
               text.length > 3 && 
               text.matches(Regex("[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*"))
    }
    
    private fun cleanUrl(url: String): String {
        return url.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .split("/")[0] // Take only domain part
    }
    
    private fun checkIfAppIsBlocked(packageName: String, forceCheck: Boolean = false) {
        serviceScope.launch {
            try {
                // Skip our own blocking overlay
                if (packageName == applicationContext.packageName) {
                    return@launch
                }
                
                val currentTime = System.currentTimeMillis()
                
                // Check cooldown to prevent repeated blocking (unless forced)
                if (!forceCheck) {
                    val lastBlockTime = blockedAppCooldown[packageName] ?: 0
                    if (currentTime - lastBlockTime < APP_BLOCK_COOLDOWN_MS) {
                        return@launch // Still in cooldown
                    }
                }
                
                // Clean up expired blocks first
                withContext(Dispatchers.IO) {
                    blockedItemDao.deleteExpiredBlocks()
                    goalBlockDao.markExpiredGoalsAsCompleted()
                }
                
                // Check regular blocks first
                val blockedApp = withContext(Dispatchers.IO) {
                    blockedItemDao.getActiveBlockedItem(packageName)
                }
                
                // Check goal blocks if no regular block found
                val goalBlock = if (blockedApp == null) {
                    withContext(Dispatchers.IO) {
                        // First check new goal_block_items table
                        val goalItem = goalBlockItemDao.getActiveGoalItemForPackageOrUrl(packageName)
                        if (goalItem != null) {
                            goalBlockDao.getGoalById(goalItem.goalId)
                        } else {
                            // Fall back to old single-item goals for backward compatibility
                            goalBlockDao.getActiveGoalBlock(packageName)
                        }
                    }
                } else null

                // Check scheduled app blocks if no regular or goal block found
                val scheduledBlock = if (blockedApp == null && goalBlock == null) {
                    withContext(Dispatchers.IO) {
                        val appSchedules = phoneBrickSessionDao.getActiveAppSchedulesForPackage(packageName)
                        appSchedules.find { schedule ->
                            isCurrentTimeInScheduleWindow(schedule)
                        }
                    }
                } else null

                // Use whichever block was found (priority: regular > goal > scheduled)
                val hasBlock = blockedApp != null || goalBlock != null || scheduledBlock != null

                if (hasBlock) {
                    // Check if temporarily unlocked
                    if (temporaryUnlock.isTemporarilyUnlocked(packageName)) {
                        Log.d(TAG, "App $packageName is temporarily unlocked")
                        return@launch
                    }

                    val isGoalBlock = goalBlock != null
                    val isScheduledBlock = scheduledBlock != null
                    Log.d(TAG, "Blocked app detected: $packageName (Goal: $isGoalBlock, Scheduled: $isScheduledBlock)")
                    activeBlockedApps.add(packageName)
                    blockedAppCooldown[packageName] = currentTime

                    // Use appropriate challenge data based on block type
                    val challengeType = when {
                        blockedApp != null -> blockedApp.challengeType
                        goalBlock != null -> goalBlock.challengeType
                        else -> scheduledBlock!!.challengeType
                    }
                    val challengeData = when {
                        blockedApp != null -> blockedApp.challengeData
                        goalBlock != null -> goalBlock.challengeData
                        else -> scheduledBlock!!.challengeData
                    }
                    val name = when {
                        blockedApp != null -> blockedApp.name
                        goalBlock != null -> goalBlock.name
                        else -> scheduledBlock!!.name
                    }

                    // For scheduled blocks, pass the schedule end time
                    val scheduleEndTime = if (isScheduledBlock) {
                        calculateScheduleEndTime(scheduledBlock!!)
                    } else 0L

                    triggerAppBlocking(name, packageName, challengeType, challengeData, isGoalBlock, isScheduledBlock, scheduleEndTime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking blocked apps", e)
            }
        }
    }
    
    private fun checkIfWebsiteIsBlocked(url: String, browserPackage: String, event: AccessibilityEvent? = null) {
        serviceScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                
                // Check if overlay is already showing for this URL
                val overlayShownTime = activeOverlays[url] ?: 0
                if (currentTime - overlayShownTime < 60000) { // Overlay shown within last minute
                    Log.d(TAG, "Overlay already active for $url, skipping")
                    return@launch
                }
                
                // Check cooldown to prevent repeated blocking for same website
                val websiteKey = "$url|$browserPackage"
                val lastBlockTime = blockedWebsiteCooldown[websiteKey] ?: 0
                if (currentTime - lastBlockTime < WEBSITE_BLOCK_COOLDOWN_MS) {
                    return@launch // Still in cooldown (2 seconds for tab close animation)
                }
                
                // Clean up expired blocks first
                withContext(Dispatchers.IO) {
                    blockedItemDao.deleteExpiredBlocks()
                    goalBlockDao.markExpiredGoalsAsCompleted()
                }
                
                // Check regular blocks first
                val blockedWebsite = withContext(Dispatchers.IO) {
                    blockedItemDao.getActiveBlockedItem(url)
                } ?: withContext(Dispatchers.IO) {
                    // If direct match fails, check for partial matches in active blocks
                    val allActiveBlocks = blockedItemDao.getAllBlockedItemsList()
                    allActiveBlocks.find { item ->
                        item.type == BlockType.WEBSITE && 
                        (item.blockEndTime == null || item.blockEndTime!! > System.currentTimeMillis()) &&
                        (url.contains(item.packageNameOrUrl) || item.packageNameOrUrl.contains(url))
                    }
                }
                
                // Check goal blocks if no regular block found
                val goalBlock = if (blockedWebsite == null) {
                    withContext(Dispatchers.IO) {
                        // First check new goal_block_items table
                        val goalItem = goalBlockItemDao.getActiveGoalItemForPackageOrUrl(url)
                        if (goalItem != null) {
                            goalBlockDao.getGoalById(goalItem.goalId)
                        } else {
                            // Check for partial matches in new goal items
                            val allActiveGoalItems = goalBlockItemDao.getAllActiveGoalItems()
                            val matchingItem = allActiveGoalItems.find { item ->
                                item.itemType == BlockType.WEBSITE && 
                                (url.contains(item.packageOrUrl) || item.packageOrUrl.contains(url))
                            }
                            if (matchingItem != null) {
                                goalBlockDao.getGoalById(matchingItem.goalId)
                            } else {
                                // Fall back to old single-item goals for backward compatibility
                                goalBlockDao.getActiveGoalBlock(url) ?: run {
                                    // Check for partial matches in old goal blocks
                                    val allActiveGoals = goalBlockDao.getAllActiveGoalsList()
                                    allActiveGoals.find { goal ->
                                        goal.type == BlockType.WEBSITE && 
                                        (url.contains(goal.packageNameOrUrl) || goal.packageNameOrUrl.contains(url))
                                    }
                                }
                            }
                        }
                    }
                } else null
                
                // Use whichever block was found
                val activeBlock = blockedWebsite ?: goalBlock
                
                if (activeBlock != null) {
                    // Check if temporarily unlocked
                    if (temporaryUnlock.isTemporarilyUnlocked(url)) {
                        Log.d(TAG, "Website $url is temporarily unlocked")
                        return@launch
                    }
                    
                    val isGoalBlock = goalBlock != null
                    Log.d(TAG, "Blocked website detected: $url (Goal: $isGoalBlock)")
                    
                    // Track that overlay is being shown for this URL
                    activeOverlays[url] = currentTime
                    blockedWebsiteCooldown[websiteKey] = currentTime
                    
                    // Clean up old overlay tracking entries (older than 1 minute)
                    activeOverlays.entries.removeIf { entry ->
                        currentTime - entry.value > 60000
                    }
                    
                    val challengeType = if (blockedWebsite != null) blockedWebsite.challengeType else goalBlock!!.challengeType
                    val challengeData = if (blockedWebsite != null) blockedWebsite.challengeData else goalBlock!!.challengeData
                    val name = if (blockedWebsite != null) blockedWebsite.name else goalBlock!!.name
                    
                    triggerWebsiteBlocking(name, url, browserPackage, challengeType, challengeData, isGoalBlock)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking blocked websites", e)
            }
        }
    }
    
    private fun triggerAppBlocking(
        appName: String,
        packageName: String,
        challengeType: ChallengeType,
        challengeData: String,
        isGoalBlock: Boolean = false,
        isScheduledBlock: Boolean = false,
        scheduleEndTime: Long = 0L
    ) {
        Log.d(TAG, "Triggering app blocking for: $appName ($packageName), scheduled=$isScheduledBlock")

        // First show blocking overlay
        val intent = Intent(this, BlockingOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra("app_name", appName)
            putExtra("package_name", packageName)
            putExtra("challenge_type", challengeType.name)
            putExtra("challenge_data", challengeData)
            putExtra("is_goal_block", isGoalBlock)
            putExtra("is_scheduled_block", isScheduledBlock)
            putExtra("schedule_end_time", scheduleEndTime)
        }
        startActivity(intent)

        // Keep monitoring this app but don't force home (which dismisses overlay)
        activeBlockedApps.add(packageName)
    }
    
    private fun triggerWebsiteBlocking(
        websiteName: String,
        url: String, 
        browserPackage: String,
        challengeType: ChallengeType,
        challengeData: String,
        isGoalBlock: Boolean = false
    ) {
        Log.d(TAG, "Triggering website blocking for: $websiteName ($url) in $browserPackage")
        
        // First, try to close the current tab or navigate away
        closeBlockedWebsiteTab(browserPackage)
        
        // Show blocking overlay (similar to app blocking)
        val intent = Intent(this, BlockingOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("app_name", "Website: $websiteName")
            putExtra("package_name", browserPackage)
            putExtra("website_url", url)
            putExtra("challenge_type", challengeType.name)
            putExtra("challenge_data", challengeData)
            putExtra("is_website_block", true)
            putExtra("is_goal_block", isGoalBlock)
        }
        startActivity(intent)
    }
    
    private fun closeBlockedWebsiteTab(browserPackage: String) {
        try {
            Log.d(TAG, "Attempting to close blocked website tab in $browserPackage")
            
            // Add a small delay to ensure the browser has processed the URL
            Thread.sleep(200)
            
            val rootNode = rootInActiveWindow ?: return
            
            // Try to close the tab only once per detection
            val tabClosed = when (browserPackage) {
                "com.android.chrome", "com.chrome.beta", "com.chrome.dev" -> {
                    closeChromeTab(rootNode)
                }
                "org.mozilla.firefox" -> {
                    closeFirefoxTab(rootNode)
                }
                "com.microsoft.emmx" -> {
                    closeEdgeTab(rootNode)
                }
                else -> {
                    // Generic approach for other browsers
                    closeGenericBrowserTab(rootNode)
                }
            }
            
            if (!tabClosed) {
                // If tab closing failed, use back navigation as fallback
                Log.d(TAG, "Tab close failed, using back navigation")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing browser tab for $browserPackage", e)
        }
    }
    
    private fun closeChromeTab(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            // Method 1: Try to find and click the close tab button (X)
            val closeButtons = findNodesByText(rootNode, "Close tab") +
                             findNodesByDescription(rootNode, "Close tab") +
                             findNodesByResourceId(rootNode, "close_tab")
            
            if (closeButtons.isNotEmpty()) {
                closeButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Chrome tab close button clicked")
                return true
            }
            
            // Method 2: Navigate to new tab or home page
            return navigateToNewTab(rootNode) || navigateToHomePage(rootNode)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Chrome tab", e)
            return false
        }
    }
    
    private fun closeFirefoxTab(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            // Similar approach for Firefox
            val closeButtons = findNodesByDescription(rootNode, "Close tab") +
                             findNodesByText(rootNode, "×")
            
            if (closeButtons.isNotEmpty()) {
                closeButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Firefox tab close button clicked")
                return true
            }
            
            return navigateToNewTab(rootNode) || navigateToHomePage(rootNode)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Firefox tab", e)
            return false
        }
    }
    
    private fun closeEdgeTab(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            val closeButtons = findNodesByDescription(rootNode, "Close tab") +
                             findNodesByText(rootNode, "Close")
            
            if (closeButtons.isNotEmpty()) {
                closeButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Edge tab close button clicked")
                return true
            }
            
            return navigateToNewTab(rootNode) || navigateToHomePage(rootNode)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Edge tab", e)
            return false
        }
    }
    
    private fun closeGenericBrowserTab(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            // Generic approach - look for common close/home patterns
            val actionButtons = findNodesByText(rootNode, "×") +
                              findNodesByText(rootNode, "Close") +
                              findNodesByDescription(rootNode, "Close") +
                              findNodesByDescription(rootNode, "Home")
            
            if (actionButtons.isNotEmpty()) {
                actionButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Generic browser action performed")
                return true
            }
            
            // Try back button as last resort
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d(TAG, "Back action performed as fallback")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error with generic browser tab close", e)
            return false
        }
    }
    
    private fun navigateToNewTab(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            val newTabButtons = findNodesByText(rootNode, "New tab") +
                              findNodesByDescription(rootNode, "New tab") +
                              findNodesByText(rootNode, "+")
            
            if (newTabButtons.isNotEmpty()) {
                newTabButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "New tab button clicked")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to new tab", e)
        }
        return false
    }
    
    private fun navigateToHomePage(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            val homeButtons = findNodesByText(rootNode, "Home") +
                            findNodesByDescription(rootNode, "Home") +
                            findNodesByDescription(rootNode, "Homepage")
            
            if (homeButtons.isNotEmpty()) {
                homeButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Home button clicked")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to home page", e)
        }
        return false
    }
    
    private fun findNodesByText(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByTextRecursive(node, text, results)
        return results
    }
    
    private fun findNodesByTextRecursive(node: AccessibilityNodeInfo?, text: String, results: MutableList<AccessibilityNodeInfo>) {
        node?.let { 
            if (it.text?.toString()?.contains(text, ignoreCase = true) == true) {
                results.add(it)
            }
            
            for (i in 0 until it.childCount) {
                findNodesByTextRecursive(it.getChild(i), text, results)
            }
        }
    }
    
    private fun findNodesByDescription(node: AccessibilityNodeInfo, description: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByDescriptionRecursive(node, description, results)
        return results
    }
    
    private fun findNodesByDescriptionRecursive(node: AccessibilityNodeInfo?, description: String, results: MutableList<AccessibilityNodeInfo>) {
        node?.let { 
            if (it.contentDescription?.toString()?.contains(description, ignoreCase = true) == true) {
                results.add(it)
            }
            
            for (i in 0 until it.childCount) {
                findNodesByDescriptionRecursive(it.getChild(i), description, results)
            }
        }
    }
    
    private fun findNodesByResourceId(node: AccessibilityNodeInfo, resourceId: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByResourceIdRecursive(node, resourceId, results)
        return results
    }
    
    private fun findNodesByResourceIdRecursive(node: AccessibilityNodeInfo?, resourceId: String, results: MutableList<AccessibilityNodeInfo>) {
        node?.let { 
            try {
                if (it.viewIdResourceName?.contains(resourceId, ignoreCase = true) == true) {
                    results.add(it)
                }
            } catch (e: Exception) {
                // Ignore resource ID access errors
            }
            
            for (i in 0 until it.childCount) {
                findNodesByResourceIdRecursive(it.getChild(i), resourceId, results)
            }
        }
    }
    
    private fun closeApp(packageName: String) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // For newer Android versions, we can't kill other apps directly
            // Instead, we rely on bringing our overlay to the front
            // The user will need to manually close the app or our overlay will cover it
            
            Log.d(TAG, "Requesting to close app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing app: $packageName", e)
        }
    }
    
    private fun forceStopApp(packageName: String) {
        try {
            // Navigate to home to ensure the app loses focus
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "Force stopped app by navigating home: $packageName")
            
            // Keep monitoring this app more aggressively
            activeBlockedApps.add(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error force stopping app", e)
        }
    }
    
    private fun startPeriodicAppMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                delay(2000) // Check every 2 seconds
                
                // Check if any blocked app is currently in foreground
                val currentPackage = getCurrentForegroundPackage()
                if (currentPackage != null && activeBlockedApps.contains(currentPackage)) {
                    Log.d(TAG, "Blocked app $currentPackage detected in periodic check")
                    checkIfAppIsBlocked(currentPackage, forceCheck = true)
                }
                
                // Clean up old entries from activeBlockedApps
                val currentTime = System.currentTimeMillis()
                activeBlockedApps.removeIf { pkg ->
                    val lastBlockTime = blockedAppCooldown[pkg] ?: 0
                    currentTime - lastBlockTime > 60000 // Remove after 1 minute
                }
            }
        }
    }
    
    private fun getCurrentForegroundPackage(): String? {
        return try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current foreground package", e)
            null
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "AppBlockingService interrupted")
    }
    
    /**
     * Handle app access attempts during a brick session - NOTIFICATION-BASED BLOCKING
     * NOTE: This check must be synchronous to prevent race conditions
     */
    private fun handleBrickedPhoneAccess(packageName: String) {
        // IMMEDIATE enforcement - synchronous check
        try {
            // Always allow our own app package (includes all our activities)
            if (packageName == applicationContext.packageName) {
                Log.d(TAG, "Allowing our own app during brick session: $packageName")
                return
            }

            // Check if this is an app we're currently trying to launch from overlay
            if (isPendingBrickLaunch(packageName)) {
                Log.d(TAG, "Allowing pending launch app: $packageName")
                return
            }

            // Allow default dialer for emergency calls (dynamic detection)
            val defaultDialer = getDefaultDialerPackage()
            if (defaultDialer != null && packageName == defaultDialer) {
                Log.d(TAG, "Allowing default dialer during brick session: $packageName")
                return
            }
            // Also allow com.android.phone as it handles call UI on some devices
            if (packageName == "com.android.phone") {
                Log.d(TAG, "Allowing phone service during brick session: $packageName")
                return
            }

            // Allow default SMS app
            val defaultSms = getDefaultSmsPackage()
            if (defaultSms != null && packageName == defaultSms) {
                Log.d(TAG, "Allowing default SMS app during brick session: $packageName")
                return
            }

            // Allow SystemUI for essential system functions
            if (packageName == "com.android.systemui") {
                Log.d(TAG, "Allowing SystemUI during brick session")
                return
            }

            // Allow keyboards/input methods - they appear when typing in allowed apps
            if (packageName.contains("inputmethod") || packageName.contains("keyboard")) {
                Log.d(TAG, "Allowing keyboard/input method during brick session: $packageName")
                return
            }

            // Check if this is an allowed or essential app during brick sessions
            val currentSession = brickSessionManager.getCurrentSession()
            if (currentSession == null) {
                Log.w(TAG, "No active brick session found, blocking app as precaution: $packageName")
                blockNonEssentialApp(packageName)
                return
            }

            // FIRST: Check if app is in the session's allowedApps list (from overlay)
            val isAllowedApp = brickSessionManager.isAppAllowedInCurrentSession(packageName)

            if (isAllowedApp) {
                Log.d(TAG, "Allowing app from session allowed apps list: $packageName")
                // Log access asynchronously
                serviceScope.launch {
                    brickSessionManager.logEssentialAppAccess(packageName)
                }
                return // Allow it and exit
            }

            // SECOND: Check if it's an essential app
            val isEssential = try {
                // Run on IO thread but block execution (synchronous)
                val result = java.util.concurrent.atomic.AtomicBoolean(false)
                val latch = java.util.concurrent.CountDownLatch(1)

                serviceScope.launch(Dispatchers.Default) {
                    try {
                        result.set(
                            essentialAppsManager.isAppEssentialForSessionType(
                                packageName, currentSession.sessionType
                            )
                        )
                    } finally {
                        latch.countDown()
                    }
                }

                // Wait up to 500ms for result (should be instant with cache)
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                result.get()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking essential app status, blocking as fallback", e)
                false // Default to blocking on error (safer)
            }

            if (isEssential) {
                Log.d(TAG, "Allowing essential app during brick session: $packageName")
                // Log access asynchronously
                serviceScope.launch {
                    brickSessionManager.logEssentialAppAccess(packageName)
                }
            } else {
                // Not essential and not allowed - block it
                Log.d(TAG, "BLOCKING non-essential, non-allowed app during brick session: $packageName")
                blockNonEssentialApp(packageName)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in brick enforcement", e)
            // Even on error, block the app (safer default)
            blockNonEssentialApp(packageName)
        }
    }
    
    /**
     * Block a non-essential app during brick mode
     */
    private fun blockNonEssentialApp(packageName: String) {
        Log.d(TAG, "BLOCKING non-essential app during brick session: $packageName")

        try {
            // Show overlay immediately using TYPE_ACCESSIBILITY_OVERLAY (no system notification!)
            requestShowBrickOverlay()

            // Log bypass attempt asynchronously
            serviceScope.launch {
                brickSessionManager.logBypassAttempt()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error blocking non-essential app", e)
        }
    }

    /**
     * Check if the current time falls within a schedule's time window
     */
    private fun isCurrentTimeInScheduleWindow(schedule: PhoneBrickSession): Boolean {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat

        // Convert to our format (1=Mon, 7=Sun)
        val dayOfWeek = if (currentDayOfWeek == 1) 7 else currentDayOfWeek - 1

        // Check if today is an active day
        if (!schedule.activeDaysOfWeek.contains(dayOfWeek.toString())) {
            return false
        }

        val startHour = schedule.startHour ?: return false
        val startMinute = schedule.startMinute ?: return false
        val endHour = schedule.endHour ?: return false
        val endMinute = schedule.endMinute ?: return false

        val currentMinutes = currentHour * 60 + currentMinute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        // Handle overnight schedules (e.g., 22:00 - 06:00)
        return if (startMinutes <= endMinutes) {
            // Same day schedule (e.g., 09:00 - 17:00)
            currentMinutes >= startMinutes && currentMinutes < endMinutes
        } else {
            // Overnight schedule (e.g., 22:00 - 06:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    /**
     * Calculate the end time for a scheduled block in milliseconds
     */
    private fun calculateScheduleEndTime(schedule: PhoneBrickSession): Long {
        val now = Calendar.getInstance()
        val endHour = schedule.endHour ?: return 0
        val endMinute = schedule.endMinute ?: return 0

        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If end time is before current time, it's tomorrow (overnight schedule)
            if (this.before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return endTime.timeInMillis
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBrickOverlayInternal()
        overlayUpdateJob?.cancel()
        sessionMonitorJob?.cancel()
        pendingLaunchJob?.cancel()
        instance = null
        monitoringJob?.cancel()
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        shouldBrickOverlayBeShowing = false
        Log.d(TAG, "AppBlockingService destroyed")
    }

    // ============== BRICK OVERLAY IMPLEMENTATION (TYPE_ACCESSIBILITY_OVERLAY) ==============

    /**
     * Internal method to show the brick overlay using TYPE_ACCESSIBILITY_OVERLAY
     * This overlay type does NOT trigger the "app is overlaying other apps" notification!
     */
    private fun showBrickOverlayInternal() {
        if (isBrickOverlayShowing) {
            Log.d(TAG, "Brick overlay already showing")
            return
        }

        try {
            // Create container for overlay using traditional Views
            brickOverlayView = FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#FF2E2E2E"))
            }

            // Build the overlay content
            val contentView = buildBrickOverlayContent()
            brickOverlayView?.addView(contentView)

            // Window parameters using TYPE_ACCESSIBILITY_OVERLAY (the key change!)
            val layoutParams = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY  // NO SYSTEM NOTIFICATION!
                format = android.graphics.PixelFormat.TRANSLUCENT
                flags = (
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or  // Extend beyond screen (cover status bar)
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or  // Hide status bar
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.START or Gravity.TOP
                x = 0
                y = 0
            }

            // Set system UI visibility to hide status bar and prevent notification shade
            @Suppress("DEPRECATION")
            brickOverlayView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

            windowManager?.addView(brickOverlayView, layoutParams)
            isBrickOverlayShowing = true
            Log.d(TAG, "Brick overlay shown successfully using TYPE_ACCESSIBILITY_OVERLAY")

            // Start update loop for time remaining
            startBrickOverlayUpdateLoop()

            // Start session monitoring
            startBrickSessionMonitoring()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing brick overlay", e)
            isBrickOverlayShowing = false
        }
    }

    /**
     * Internal method to hide the brick overlay
     */
    private fun hideBrickOverlayInternal() {
        try {
            if (brickOverlayView != null && windowManager != null) {
                windowManager?.removeView(brickOverlayView)
                brickOverlayView = null
                isBrickOverlayShowing = false
                overlayUpdateJob?.cancel()
                sessionMonitorJob?.cancel()
                Log.d(TAG, "Brick overlay hidden (isBrickOverlayShowing=false)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding brick overlay", e)
        }
    }

    /**
     * Build the main overlay content with timer
     */
    private fun buildBrickOverlayContent(): FrameLayout {
        val currentSession = brickSessionManager.getCurrentSession()

        // Calculate total session duration
        totalSessionSeconds = if (currentSession != null &&
            currentSession.currentSessionStartTime != null &&
            currentSession.currentSessionEndTime != null) {
            ((currentSession.currentSessionEndTime!! - currentSession.currentSessionStartTime!!) / 1000).toInt()
        } else {
            currentSession?.durationMinutes?.times(60) ?: 0
        }

        // Main container
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
            setTextColor(android.graphics.Color.parseColor("#FF94A3B8"))
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
            text = formatBrickCountdownTime()
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
            text = formatBrickEndTime()
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

        // Emergency exit button
        if (currentSession?.allowEmergencyOverride == true) {
            val isEmergencyEnabled = globalSettingsManager.isEmergencyExitEnabled()
            val buttonColor = if (isEmergencyEnabled) "#FFEF4444" else "#FF6B7280"

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
                isClickable = isEmergencyEnabled
                isFocusable = isEmergencyEnabled
                alpha = if (isEmergencyEnabled) 1.0f else 0.5f

                val buttonBackground = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(dpToPx(1), android.graphics.Color.parseColor(buttonColor))
                    cornerRadius = dpToPx(8).toFloat()
                }
                background = buttonBackground

                if (isEmergencyEnabled) {
                    setOnClickListener {
                        launchBrickEmergencyOverride()
                    }
                }
            }

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

        // Bottom apps container
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

            val appsBackground = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FF1E293B"))
                cornerRadius = dpToPx(24).toFloat()
            }
            background = appsBackground
        }

        // Load essential apps asynchronously
        loadAndDisplayBrickEssentialApps(appsContainer)

        container.addView(appsContainer)

        // Update timer immediately
        updateBrickTimerDisplay()

        return container
    }

    /**
     * Build emergency mode content with click challenge
     */
    private fun buildBrickEmergencyContent(): FrameLayout {
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#FF1A1A1A"))
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
                onBrickEmergencyTap()
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
                cancelBrickEmergencyMode()
            }
        }
        contentLayout.addView(cancelButton)

        container.addView(contentLayout)
        return container
    }

    // ============== BRICK OVERLAY HELPER METHODS ==============

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun formatBrickCountdownTime(): String {
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds() ?: 0
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatBrickEndTime(): String {
        val currentSession = brickSessionManager.getCurrentSession()
        val endTime = currentSession?.currentSessionEndTime ?: (System.currentTimeMillis() + 60000)
        val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(endTime))
    }

    private fun updateBrickTimerDisplay() {
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds() ?: 0
        circularTimerView?.setProgress(remainingSeconds, totalSessionSeconds)
        timeTextView?.text = formatBrickCountdownTime()
    }

    private fun startBrickOverlayUpdateLoop() {
        overlayUpdateJob?.cancel()
        overlayUpdateJob = serviceScope.launch {
            while (isActive && isBrickOverlayShowing) {
                try {
                    updateBrickTimerDisplay()
                    delay(1_000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating brick overlay", e)
                    delay(1_000)
                }
            }
        }
    }

    private fun startBrickSessionMonitoring() {
        sessionMonitorJob?.cancel()
        sessionMonitorJob = serviceScope.launch {
            while (isActive && isBrickOverlayShowing) {
                try {
                    if (!brickSessionManager.isPhoneBricked()) {
                        Log.d(TAG, "CRITICAL: Brick session ended - hiding overlay immediately")
                        shouldBrickOverlayBeShowing = false
                        hideBrickOverlayInternal()
                        break
                    }
                    delay(500)
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring brick session", e)
                    delay(500)
                }
            }
        }
    }

    private fun loadAndDisplayBrickEssentialApps(container: LinearLayout) {
        serviceScope.launch {
            try {
                val displayedPackages = mutableSetOf<String>()

                // Add Phone button
                addBrickIntentButton(
                    container = container,
                    iconResId = android.R.drawable.sym_action_call,
                    contentDescription = "Phone",
                    intent = Intent(Intent.ACTION_DIAL).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )

                // Add Messages button
                addBrickIntentButton(
                    container = container,
                    iconResId = android.R.drawable.sym_action_email,
                    contentDescription = "Messages",
                    intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("smsto:")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )

                // Add default allowed apps from Settings
                val defaultAllowedApps = globalSettingsManager.getDefaultAllowedApps()
                for (packageName in defaultAllowedApps) {
                    if (!displayedPackages.contains(packageName)) {
                        tryAddBrickAppIcon(container, packageName, displayedPackages)
                    }
                }

                // Add user-added essential apps
                val essentialApps = essentialAppsManager.getAllEssentialApps().firstOrNull() ?: emptyList()
                val userApps = essentialApps.filter { it.isUserAdded }

                for (app in userApps) {
                    if (!displayedPackages.contains(app.packageName)) {
                        tryAddBrickAppIcon(container, app.packageName, displayedPackages)
                    }
                }

                Log.d(TAG, "Displayed essential apps: Phone, Messages + ${displayedPackages.size} user apps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading essential apps", e)
            }
        }
    }

    private fun addBrickIntentButton(
        container: LinearLayout,
        iconResId: Int,
        contentDescription: String,
        intent: Intent
    ) {
        val appButton = FrameLayout(this).apply {
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
                launchBrickIntent(intent, contentDescription)
            }
        }

        val iconView = ImageView(this).apply {
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

    private fun launchBrickIntent(intent: Intent, label: String) {
        try {
            val targetPackage = when (label) {
                "Phone" -> essentialAppsManager.getDefaultDialerPackage()
                "Messages" -> essentialAppsManager.getDefaultSmsPackage()
                else -> null
            }

            pendingLaunchPackage = targetPackage
            pendingLaunchTime = System.currentTimeMillis()

            startActivity(intent)
            Log.d(TAG, "Launched: $label (target: $targetPackage)")

            serviceScope.launch {
                brickSessionManager.logEssentialAppAccess(label)
            }

            if (targetPackage != null) {
                startBrickPendingLaunchMonitor(targetPackage)
            } else {
                serviceScope.launch {
                    delay(500)
                    requestHideBrickOverlayForAllowedApp()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching $label", e)
            pendingLaunchPackage = null
        }
    }

    private fun tryAddBrickAppIcon(
        container: LinearLayout,
        packageName: String,
        displayedPackages: MutableSet<String>
    ): Boolean {
        if (displayedPackages.contains(packageName)) return false

        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appIcon = packageManager.getApplicationIcon(appInfo)

            val appButton = FrameLayout(this).apply {
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
                    launchBrickApp(packageName)
                }
            }

            val iconView = ImageView(this).apply {
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
            return true

        } catch (e: Exception) {
            Log.d(TAG, "App not found: $packageName")
            return false
        }
    }

    private fun launchBrickApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                pendingLaunchPackage = packageName
                pendingLaunchTime = System.currentTimeMillis()

                startActivity(intent)
                Log.d(TAG, "Launched allowed app: $packageName")

                serviceScope.launch {
                    brickSessionManager.logEssentialAppAccess(packageName)
                }

                startBrickPendingLaunchMonitor(packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app $packageName", e)
            pendingLaunchPackage = null
        }
    }

    private fun startBrickPendingLaunchMonitor(targetPackage: String) {
        pendingLaunchJob?.cancel()
        pendingLaunchJob = serviceScope.launch {
            repeat(30) {
                delay(100)

                val foreground = getForegroundPackageReliably()
                if (foreground == targetPackage) {
                    Log.d(TAG, "Target app $targetPackage confirmed in foreground")
                    confirmedLaunchPackage = targetPackage
                    launchConfirmedTime = System.currentTimeMillis()
                    pendingLaunchPackage = null

                    requestHideBrickOverlayForAllowedApp()
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

    private fun launchBrickEmergencyOverride() {
        isEmergencyMode = true
        emergencyClicksRemaining = globalSettingsManager.getClickCount()
        Log.d(TAG, "Entering emergency mode - $emergencyClicksRemaining clicks required")
        rebuildBrickOverlayContent()
    }

    private fun rebuildBrickOverlayContent() {
        brickOverlayView?.let { container ->
            container.removeAllViews()
            val contentView = if (isEmergencyMode) {
                buildBrickEmergencyContent()
            } else {
                buildBrickOverlayContent()
            }
            container.addView(contentView)
        }
    }

    private fun onBrickEmergencyTap() {
        emergencyClicksRemaining--
        emergencyClicksTextView?.text = "$emergencyClicksRemaining"

        if (emergencyClicksRemaining <= 0) {
            Log.d(TAG, "Emergency override complete - ending session")
            serviceScope.launch {
                brickSessionManager.emergencyOverride("User completed emergency challenge")
            }
        }
    }

    private fun cancelBrickEmergencyMode() {
        isEmergencyMode = false
        emergencyClicksRemaining = 0
        Log.d(TAG, "Emergency mode cancelled")
        rebuildBrickOverlayContent()
    }
}