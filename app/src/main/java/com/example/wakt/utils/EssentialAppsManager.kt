package com.example.wakt.utils

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.wakt.data.database.dao.EssentialAppDao
import com.example.wakt.data.database.entity.EssentialApp
import com.example.wakt.data.database.entity.DefaultEssentialApps
import com.example.wakt.data.database.entity.BrickSessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EssentialAppsManager @Inject constructor(
    private val context: Context,
    private val essentialAppDao: EssentialAppDao
) {
    companion object {
        private const val TAG = "EssentialAppsManager"
    }

    // In-memory cache for essential apps to avoid DB lookups during blocking
    private var essentialAppsCache: List<EssentialApp>? = null
    private var cacheLoadedTime: Long = 0
    private val cacheTtlMs = 5 * 60 * 1000L // 5-minute TTL

    init {
        initializeSystemEssentialApps()
        loadEssentialAppsCache()
    }

    /**
     * Load essential apps into memory cache
     */
    private fun loadEssentialAppsCache() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                essentialAppsCache = essentialAppDao.getAllEssentialAppsList()
                cacheLoadedTime = System.currentTimeMillis()
                Log.d(TAG, "Loaded ${essentialAppsCache?.size ?: 0} essential apps into cache")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading essential apps cache", e)
                essentialAppsCache = emptyList()
            }
        }
    }

    /**
     * Refresh cache if expired
     */
    private suspend fun refreshCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - cacheLoadedTime > cacheTtlMs) {
            CoroutineScope(Dispatchers.IO).launch {
                loadEssentialAppsCache()
            }
        }
    }
    
    /**
     * Initialize system essential apps - adds any missing essential apps
     */
    private fun initializeSystemEssentialApps() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingApps = essentialAppDao.getAllEssentialAppsList()
                val existingPackages = existingApps.map { it.packageName }.toSet()

                // Find installed system apps that aren't in the database yet
                val missingApps = DefaultEssentialApps.SYSTEM_ESSENTIALS
                    .filter { isPackageInstalled(it.packageName) }
                    .filter { it.packageName !in existingPackages }
                    .map { it.copy(id = 0) } // Reset ID for insertion

                if (missingApps.isNotEmpty()) {
                    essentialAppDao.insertEssentialApps(missingApps)
                    Log.i(TAG, "Added ${missingApps.size} missing system essential apps: ${missingApps.map { it.packageName }}")
                    // Refresh cache after adding new apps
                    loadEssentialAppsCache()
                } else {
                    Log.d(TAG, "All system essential apps already initialized (${existingApps.size} total)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing system essential apps", e)
            }
        }
    }
    
    /**
     * Get all essential apps
     */
    fun getAllEssentialApps(): Flow<List<EssentialApp>> {
        return essentialAppDao.getAllEssentialApps()
    }
    
    /**
     * Get essential apps allowed for a specific session type
     */
    suspend fun getEssentialAppsForSessionType(sessionType: BrickSessionType): List<EssentialApp> {
        return essentialAppDao.getEssentialAppsForSessionType(sessionType.name)
    }
    
    /**
     * Check if an app is essential for a specific session type - uses in-memory cache for speed
     */
    suspend fun isAppEssentialForSessionType(packageName: String, sessionType: BrickSessionType): Boolean {
        // Refresh cache if needed
        refreshCacheIfNeeded()

        // Check cache first (fast path)
        val cache = essentialAppsCache ?: return essentialAppDao.isAppEssentialForSessionType(packageName, sessionType.name)

        return cache.any { app ->
            app.packageName == packageName &&
            app.isActive &&
            app.allowedSessionTypes.contains(sessionType.name)
        }
    }
    
    /**
     * Check if an app is essential (for any session type)
     */
    suspend fun isAppEssential(packageName: String): Boolean {
        val app = essentialAppDao.getEssentialAppByPackage(packageName)
        val isEssential = app != null
        Log.d(TAG, "isAppEssential($packageName): found=$isEssential, app=${app?.appName}")
        return isEssential
    }
    
    /**
     * Add a user-defined essential app
     */
    suspend fun addEssentialApp(
        appName: String,
        packageName: String,
        allowedSessionTypes: List<BrickSessionType> = BrickSessionType.values().toList()
    ): Boolean {
        try {
            // Check if app is already essential
            val existing = essentialAppDao.getEssentialAppByPackage(packageName)
            if (existing != null) {
                Log.w(TAG, "App $packageName is already marked as essential")
                return false
            }

            // Verify package is installed
            if (!isPackageInstalled(packageName)) {
                Log.w(TAG, "Package $packageName is not installed")
                return false
            }

            val essentialApp = EssentialApp(
                appName = appName,
                packageName = packageName,
                isSystemEssential = false,
                isUserAdded = true,
                allowedSessionTypes = allowedSessionTypes.joinToString(",") { it.name },
                addedAt = System.currentTimeMillis()
            )

            essentialAppDao.insertEssentialApp(essentialApp)
            Log.i(TAG, "Added essential app: $appName ($packageName)")

            // Refresh cache after modification
            loadEssentialAppsCache()

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error adding essential app", e)
            return false
        }
    }
    
    /**
     * Remove a user-defined essential app
     */
    suspend fun removeEssentialApp(packageName: String): Boolean {
        try {
            val app = essentialAppDao.getEssentialAppByPackage(packageName)
            if (app == null) {
                Log.w(TAG, "App $packageName is not marked as essential")
                return false
            }

            if (app.isSystemEssential) {
                Log.w(TAG, "Cannot remove system essential app: $packageName")
                return false
            }

            essentialAppDao.deleteEssentialAppByPackage(packageName)
            Log.i(TAG, "Removed essential app: ${app.appName} ($packageName)")

            // Refresh cache after modification
            loadEssentialAppsCache()

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error removing essential app", e)
            return false
        }
    }
    
    /**
     * Update session types allowed for an essential app
     */
    suspend fun updateAppSessionTypes(
        packageName: String, 
        allowedSessionTypes: List<BrickSessionType>
    ): Boolean {
        try {
            val app = essentialAppDao.getEssentialAppByPackage(packageName) ?: return false
            
            val updatedApp = app.copy(
                allowedSessionTypes = allowedSessionTypes.joinToString(",") { it.name }
            )
            
            essentialAppDao.updateEssentialApp(updatedApp)
            Log.i(TAG, "Updated session types for ${app.appName}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating app session types", e)
            return false
        }
    }
    
    /**
     * Get apps that are allowed during brick sessions (essential apps)
     */
    suspend fun getAllowedAppsForBricking(sessionType: BrickSessionType): List<String> {
        return try {
            getEssentialAppsForSessionType(sessionType).map { it.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting allowed apps for bricking", e)
            emptyList()
        }
    }
    
    /**
     * Get system essential apps (phone, emergency, etc.)
     */
    suspend fun getSystemEssentialApps(): List<EssentialApp> {
        return essentialAppDao.getSystemEssentialApps()
    }
    
    /**
     * Get user-added essential apps
     */
    suspend fun getUserEssentialApps(): List<EssentialApp> {
        return essentialAppDao.getUserEssentialApps()
    }
    
    /**
     * Check if a package is installed on the device
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Get installed app name from package name
     */
    fun getAppName(packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            val appInfo = packageInfo.applicationInfo
            if (appInfo != null) {
                context.packageManager.getApplicationLabel(appInfo).toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validate and clean up essential apps (remove uninstalled apps)
     */
    suspend fun cleanupEssentialApps(): Int {
        try {
            val allApps = essentialAppDao.getAllEssentialAppsList()
            var removedCount = 0
            
            for (app in allApps) {
                if (!isPackageInstalled(app.packageName)) {
                    essentialAppDao.deleteEssentialAppByPackage(app.packageName)
                    removedCount++
                    Log.i(TAG, "Removed uninstalled essential app: ${app.appName}")
                }
            }
            
            return removedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up essential apps", e)
            return 0
        }
    }
    
    /**
     * Get default dialer package dynamically
     */
    fun getDefaultDialerPackage(): String? {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            telecomManager?.defaultDialerPackage
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default dialer", e)
            null
        }
    }

    /**
     * Get default SMS package dynamically
     */
    fun getDefaultSmsPackage(): String? {
        return try {
            android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default SMS app", e)
            null
        }
    }

    /**
     * Get emergency contact packages that should always be allowed (dynamic detection)
     */
    fun getEmergencyContactPackages(): List<String> {
        val packages = mutableListOf<String>()

        // Add default dialer
        getDefaultDialerPackage()?.let { packages.add(it) }

        // Add default SMS app
        getDefaultSmsPackage()?.let { packages.add(it) }

        // Always include these system packages
        packages.addAll(listOf(
            "com.android.phone",      // Phone service (handles calls)
            "com.android.emergency",  // Emergency SOS
            "com.android.incallui"    // In-call UI
        ))

        return packages.distinct()
    }

    /**
     * Check if package is an emergency-related app
     */
    fun isEmergencyApp(packageName: String): Boolean {
        // Check against dynamically detected packages
        val emergencyPackages = getEmergencyContactPackages()
        if (emergencyPackages.contains(packageName)) return true

        // Also check for emergency keywords
        return packageName.contains("emergency") || packageName.contains("911")
    }
}