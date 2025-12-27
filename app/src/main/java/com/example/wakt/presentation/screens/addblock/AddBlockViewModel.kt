package com.example.wakt.presentation.screens.addblock

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.BlockedItem
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.utils.GlobalSettingsManager
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
class AddBlockViewModel @Inject constructor(
    private val blockedItemDao: BlockedItemDao,
    private val globalSettingsManager: GlobalSettingsManager
) : ViewModel() {

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
                                                    iconBitmap = null
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
                        finalList
                    }

            _uiState.update { state ->
                state.copy(
                        allApps = apps,
                        filteredApps = apps.take(25),
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
        _uiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            delay(300)

            _uiState.update { state ->
                val baseApps = if (query.isBlank()) {
                    state.allApps
                } else {
                    state.allApps.filter { app ->
                        app.name.contains(query, ignoreCase = true) ||
                                app.packageName.contains(query, ignoreCase = true)
                    }
                }
                val filteredApps = baseApps.take(25)
                state.copy(
                    filteredApps = filteredApps,
                    displayedAppsCount = 25
                )
            }
        }
    }

    fun loadMoreIfNeeded() {
        _uiState.update { state ->
            if (state.filteredApps.size >= state.allApps.size) return@update state

            val baseApps = if (state.searchQuery.isBlank()) {
                state.allApps
            } else {
                state.allApps.filter { app ->
                    app.name.contains(state.searchQuery, ignoreCase = true) ||
                            app.packageName.contains(state.searchQuery, ignoreCase = true)
                }
            }

            if (state.filteredApps.size >= baseApps.size) return@update state

            val newCount = (state.displayedAppsCount + 25).coerceAtMost(baseApps.size)
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

    private fun shouldIncludePackage(packageName: String): Boolean {
        val excludedPrefixes =
                listOf(
                        "android.",
                        "com.android.systemui",
                        "com.google.android.gms",
                        "com.google.android.gsf",
                        "com.android.providers.",
                        "com.android.vending",
                        "com.android.settings"
                )

        if (packageName.contains("com.example.wakt")) return false

        return excludedPrefixes.none { packageName.startsWith(it) }
    }

    fun saveSelectedItems() {
        viewModelScope.launch {
            val state = _uiState.value

            // Get global challenge settings
            val challengeType = globalSettingsManager.getChallengeType()
            val waitTimeMinutes = globalSettingsManager.getWaitTimeMinutes()
            val clickCount = globalSettingsManager.getClickCount()

            // Determine challenge data based on type
            val challengeData = when (challengeType) {
                ChallengeType.WAIT -> waitTimeMinutes.toString()
                ChallengeType.CLICK_500 -> clickCount.toString()
                else -> ""
            }

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
                                    challengeType = challengeType,
                                    challengeData = challengeData,
                                    blockDurationDays = 0,
                                    blockStartTime = currentTime,
                                    blockEndTime = null
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
                                challengeType = challengeType,
                                challengeData = challengeData,
                                blockDurationDays = 0,
                                blockStartTime = currentTime,
                                blockEndTime = null
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
        val isLoading: Boolean = false,
        val displayedAppsCount: Int = 25
)
