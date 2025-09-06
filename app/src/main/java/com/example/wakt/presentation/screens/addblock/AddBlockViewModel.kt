package com.example.wakt.presentation.screens.addblock

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.BlockedItem
import com.example.wakt.data.database.entity.ChallengeType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AddBlockViewModel @Inject constructor(private val blockedItemDao: BlockedItemDao) :
        ViewModel() {

    private val _uiState = MutableStateFlow(AddBlockUiState())
    val uiState: StateFlow<AddBlockUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val apps =
                    withContext(Dispatchers.IO) {
                        val packageManager = context.packageManager

                        // Use map to avoid duplicates based on package name
                        val allApps = mutableMapOf<String, AppInfo>()

                        // Method 1: Query apps with MAIN/LAUNCHER intent (most reliable for user
                        // apps)
                        try {
                            val mainIntent =
                                    android.content.Intent(android.content.Intent.ACTION_MAIN, null)
                            mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)

                            resolveInfos.forEach { resolveInfo ->
                                try {
                                    val appInfo = resolveInfo.activityInfo.applicationInfo
                                    val packageName = appInfo.packageName

                                    // Skip system packages that don't make sense to block
                                    if (!shouldIncludePackage(packageName)) return@forEach

                                    val drawable = packageManager.getApplicationIcon(appInfo)
                                    val app =
                                            AppInfo(
                                                    name =
                                                            packageManager
                                                                    .getApplicationLabel(appInfo)
                                                                    .toString(),
                                                    packageName = packageName,
                                                    icon = drawable,
                                                    iconBitmap =
                                                            null // Decode lazily per visible row to
                                                    // avoid jank
                                                    )
                                    allApps[packageName] = app
                                } catch (e: Exception) {
                                    // Ignore apps that can't be loaded
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AddBlockViewModel", "Error loading launcher apps", e)
                        }

                        val finalList = allApps.values.toList().sortedBy { it.name.lowercase() }
                        Log.d("AddBlockViewModel", "Found ${finalList.size} unique apps")
                        finalList.take(10).forEach { app ->
                            Log.d("AddBlockViewModel", "App: ${app.name} (${app.packageName})")
                        }
                        finalList
                    }

            _uiState.update { state ->
                state.copy(
                        allApps = apps,
                        filteredApps = apps.take(25), // Start with smaller initial load
                        isLoading = false,
                        displayedAppsCount = 25
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }

    fun updateSearchQuery(query: String) {
        // Update UI immediately with the search query
        _uiState.update { it.copy(searchQuery = query) }
        
        // Cancel previous search job
        searchJob?.cancel()
        
        // Debounce the actual filtering
        searchJob = viewModelScope.launch {
            delay(300) // 300ms debounce
            
            _uiState.update { state ->
                val baseApps = if (query.isBlank()) {
                    state.allApps
                } else {
                    // Use more efficient filtering
                    state.allApps.filter { app ->
                        app.name.contains(query, ignoreCase = true) ||
                                app.packageName.contains(query, ignoreCase = true)
                    }
                }
                val filteredApps = baseApps.take(25) // Reset to initial pagination
                state.copy(
                    filteredApps = filteredApps,
                    displayedAppsCount = 25 // Reset pagination on new search
                )
            }
        }
    }

    fun loadMoreIfNeeded() {
        _uiState.update { state ->
            // Check if we already have all items to avoid unnecessary work
            if (state.filteredApps.size >= state.allApps.size) return@update state
            
            // Respect current search filter while paginating
            val baseApps = if (state.searchQuery.isBlank()) {
                state.allApps
            } else {
                // Cache filtered results to avoid re-filtering
                state.allApps.filter { app ->
                    app.name.contains(state.searchQuery, ignoreCase = true) ||
                            app.packageName.contains(state.searchQuery, ignoreCase = true)
                }
            }

            if (state.filteredApps.size >= baseApps.size) return@update state

            val newCount = (state.displayedAppsCount + 25).coerceAtMost(baseApps.size) // Reduced chunk size
            state.copy(displayedAppsCount = newCount, filteredApps = baseApps.take(newCount))
        }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val updatedSelection = state.selectedApps.toMutableSet()
            if (updatedSelection.contains(packageName)) {
                updatedSelection.remove(packageName)
            } else {
                updatedSelection.add(packageName)
            }
            state.copy(selectedApps = updatedSelection)
        }
    }

    fun updateWebsiteUrl(url: String) {
        _uiState.update { it.copy(websiteUrl = url) }
    }

    fun updateChallengeType(type: ChallengeType) {
        _uiState.update { it.copy(challengeType = type) }
    }

    fun updateWaitTime(minutes: Int) {
        _uiState.update { it.copy(waitTimeMinutes = minutes) }
    }


    private fun shouldIncludePackage(packageName: String): Boolean {
        // Exclude common system packages that don't make sense to block
        val excludedPrefixes =
                listOf(
                        "android.",
                        "com.android.systemui",
                        "com.google.android.gms",
                        "com.google.android.gsf",
                        "com.android.providers.",
                        "com.android.vending", // Play Store might be needed for updates
                        "com.android.settings" // Users might need access to settings
                )

        // Also exclude our own app
        if (packageName.contains("com.example.wakt")) return false

        return excludedPrefixes.none { packageName.startsWith(it) }
    }

    fun saveSelectedItems() {
        viewModelScope.launch {
            val state = _uiState.value

            // Save selected apps
            state.selectedApps.forEach { packageName ->
                val app = state.allApps.find { it.packageName == packageName }
                app?.let {
                    val currentTime = System.currentTimeMillis()
                    
                    val blockedItem =
                            BlockedItem(
                                    name = it.name,
                                    type = BlockType.APP,
                                    packageNameOrUrl = it.packageName,
                                    challengeType = state.challengeType,
                                    challengeData =
                                            if (state.challengeType == ChallengeType.WAIT) {
                                                state.waitTimeMinutes.toString()
                                            } else "",
                                    blockDurationDays = 0, // Always permanent
                                    blockStartTime = currentTime,
                                    blockEndTime = null // Always permanent
                            )
                    blockedItemDao.insertBlockedItem(blockedItem)
                }
            }

            // Save website if entered
            if (state.websiteUrl.isNotBlank()) {
                val currentTime = System.currentTimeMillis()
                
                val blockedItem =
                        BlockedItem(
                                name = state.websiteUrl,
                                type = BlockType.WEBSITE,
                                packageNameOrUrl = state.websiteUrl,
                                challengeType = state.challengeType,
                                challengeData =
                                        if (state.challengeType == ChallengeType.WAIT) {
                                            state.waitTimeMinutes.toString()
                                        } else "",
                                blockDurationDays = 0, // Always permanent
                                blockStartTime = currentTime,
                                blockEndTime = null // Always permanent
                        )
                blockedItemDao.insertBlockedItem(blockedItem)
            }
        }
    }
}

data class AddBlockUiState(
        val selectedTabIndex: Int = 0,
        val allApps: List<AppInfo> = emptyList(),
        val filteredApps: List<AppInfo> = emptyList(),
        val selectedApps: Set<String> = emptySet(),
        val searchQuery: String = "",
        val websiteUrl: String = "",
        val challengeType: ChallengeType = ChallengeType.WAIT,
        val waitTimeMinutes: Int = 10,
        val isLoading: Boolean = false,
        val displayedAppsCount: Int = 25 // Reduced initial count
)
