package com.example.wakt.services

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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.dao.GoalBlockDao
import com.example.wakt.data.database.dao.GoalBlockItemDao
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.presentation.activities.BlockingOverlayActivity
import com.example.wakt.presentation.activities.BrickBlockingOverlay
import com.example.wakt.utils.TemporaryUnlock
import com.example.wakt.utils.BrickSessionManager
import com.example.wakt.utils.EssentialAppsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.Job
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
    lateinit var temporaryUnlock: TemporaryUnlock
    
    @Inject
    lateinit var brickSessionManager: BrickSessionManager
    
    @Inject
    lateinit var essentialAppsManager: EssentialAppsManager
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

        // Static reference to the service instance for clearing cooldowns and getting foreground package
        private var instance: AppBlockingService? = null

        /**
         * Get the foreground package name - single reliable source for all services
         * Uses UsageStatsManager (requires PACKAGE_USAGE_STATS permission and Usage Access grant)
         * Falls back to AccessibilityService if UsageStatsManager not available
         */
        fun getForegroundPackageReliably(): String? {
            val context = instance ?: return null

            // Method 1: UsageStatsManager (most reliable on modern Android)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                    if (usageManager != null) {
                        val timeInterval = 1000L // Last 1 second
                        val currentTime = System.currentTimeMillis()
                        val queryUsageStats = usageManager.queryUsageStats(
                            android.app.usage.UsageStatsManager.INTERVAL_BEST,
                            currentTime - timeInterval,
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
                Log.d(TAG, "UsageStatsManager method failed (may need Usage Access permission): ${e.message}")
            }

            // Method 2: Fallback to AccessibilityService if UsageStatsManager unavailable
            return try {
                val packageName = instance?.rootInActiveWindow?.packageName?.toString()
                if (!packageName.isNullOrBlank()) {
                    Log.d(TAG, "getForegroundPackageReliably: Got from AccessibilityService: $packageName")
                    return packageName
                }
                Log.d(TAG, "getForegroundPackageReliably: AccessibilityService returned null")
                null
            } catch (e: Exception) {
                Log.e(TAG, "getForegroundPackageReliably: All methods failed: ${e.message}")
                null
            }
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
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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
            // PRIORITY: Check brick mode for ALL events
            if (brickSessionManager.isPhoneBricked()) {
                val packageName = it.packageName?.toString()

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
                
                // Use whichever block was found (priority to regular blocks)
                val activeBlock = blockedApp ?: goalBlock
                
                if (activeBlock != null) {
                    // Check if temporarily unlocked
                    if (temporaryUnlock.isTemporarilyUnlocked(packageName)) {
                        Log.d(TAG, "App $packageName is temporarily unlocked")
                        return@launch
                    }
                    
                    val isGoalBlock = goalBlock != null
                    Log.d(TAG, "Blocked app detected: $packageName (Goal: $isGoalBlock)")
                    activeBlockedApps.add(packageName)
                    blockedAppCooldown[packageName] = currentTime
                    
                    // Use appropriate challenge data
                    val challengeType = if (blockedApp != null) blockedApp.challengeType else goalBlock!!.challengeType
                    val challengeData = if (blockedApp != null) blockedApp.challengeData else goalBlock!!.challengeData
                    val name = if (blockedApp != null) blockedApp.name else goalBlock!!.name
                    
                    triggerAppBlocking(name, packageName, challengeType, challengeData, isGoalBlock)
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
        isGoalBlock: Boolean = false
    ) {
        Log.d(TAG, "Triggering app blocking for: $appName ($packageName)")
        
        // First show blocking overlay
        val intent = Intent(this, BlockingOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra("app_name", appName)
            putExtra("package_name", packageName)
            putExtra("challenge_type", challengeType.name)
            putExtra("challenge_data", challengeData)
            putExtra("is_goal_block", isGoalBlock)
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

            // Allow dialer apps for emergency calls
            val dialerPackages = listOf(
                "com.android.dialer",
                "com.google.android.dialer",
                "com.samsung.android.dialer",
                "com.android.phone"
            )
            if (dialerPackages.contains(packageName)) {
                Log.d(TAG, "Allowing dialer during brick session: $packageName")
                return
            }

            // Allow SystemUI for essential system functions
            if (packageName == "com.android.systemui") {
                Log.d(TAG, "Allowing SystemUI during brick session")
                return
            }

            // Check if this is an allowed or essential app during brick sessions
            val currentSession = brickSessionManager.getCurrentSession()
            if (currentSession == null) {
                Log.w(TAG, "No active brick session found, blocking app as precaution: $packageName")
                blockNonEssentialAppWithNotification(packageName)
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
                // Not essential and not allowed - block it with notification
                Log.d(TAG, "BLOCKING non-essential, non-allowed app during brick session: $packageName")
                blockNonEssentialAppWithNotification(packageName)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in brick enforcement", e)
            // Even on error, block the app (safer default)
            blockNonEssentialAppWithNotification(packageName)
        }
    }
    
    /**
     * Block a non-essential app during brick mode using notification
     */
    private fun blockNonEssentialAppWithNotification(packageName: String) {
        Log.d(TAG, "BLOCKING non-essential app during brick session: $packageName")
        
        try {
            // Get app name for notification
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            
            // Show notification instead of overlay
            showBrickModeViolationNotification(appName)
            
            // Close the app gently - just go back, don't force home or launcher
            performGlobalAction(GLOBAL_ACTION_BACK)
            
            // Log bypass attempt asynchronously
            serviceScope.launch {
                brickSessionManager.logBypassAttempt()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking non-essential app with notification", e)
            // Fallback - just go back
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (ignored: Exception) {}
        }
    }
    
    /**
     * Show a notification when user tries to access blocked app during brick mode
     */
    @android.annotation.SuppressLint("NotificationPermission")
    private fun showBrickModeViolationNotification(appName: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create notification channel for brick violations
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "brick_violation_channel",
                    "Focus Mode Violations",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when trying to access blocked apps during focus mode"
                    enableVibration(false) // Less intrusive
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // Get session info for better context
            val currentSession = brickSessionManager.getCurrentSession()
            val sessionName = currentSession?.name ?: "Focus Mode"
            val timeText = formatRemainingTimeShort()
            
            // Create notification
            val notification = NotificationCompat.Builder(this, "brick_violation_channel")
                .setContentTitle("🔒 $sessionName Active")
                .setContentText("$appName blocked • $timeText")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(3000) // Auto-dismiss after 3 seconds
                .build()
                
            // Show notification with unique ID based on app name (check permission for Android 13+)
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(appName.hashCode(), notification)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing brick mode violation notification", e)
        }
    }
    
    private fun formatRemainingTimeShort(): String {
        val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds() ?: 0
        val remainingMinutes = brickSessionManager.getCurrentSessionRemainingMinutes() ?: 0
        
        return when {
            remainingMinutes >= 60 -> {
                val hours = remainingMinutes / 60
                val mins = remainingMinutes % 60
                if (mins > 0) "${hours}h ${mins}m left" else "${hours}h left"
            }
            remainingMinutes > 1 -> "${remainingMinutes}m left"
            remainingMinutes == 1 -> {
                val seconds = remainingSeconds % 60
                if (seconds > 0) "${remainingMinutes}m ${seconds}s left" else "1m left"
            }
            remainingSeconds > 0 -> "${remainingSeconds}s left"
            else -> "Ending now"
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        monitoringJob?.cancel()
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "AppBlockingService destroyed")
    }
}