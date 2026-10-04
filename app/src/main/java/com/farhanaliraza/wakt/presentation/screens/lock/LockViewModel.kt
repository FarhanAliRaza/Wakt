package com.farhanaliraza.wakt.presentation.screens.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhanaliraza.wakt.data.database.dao.BlockedItemDao
import com.farhanaliraza.wakt.data.database.dao.PhoneBrickSessionDao
import com.farhanaliraza.wakt.data.database.entity.BlockedItem
import com.farhanaliraza.wakt.data.database.entity.BrickSessionType
import com.farhanaliraza.wakt.data.database.entity.PhoneBrickSession
import com.farhanaliraza.wakt.utils.BrickSessionManager
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.PermissionHelper
import com.farhanaliraza.wakt.utils.ServiceOptimizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockUiState(
    // Phone tab state
    val selectedHours: Int = 0,
    val selectedMinutes: Int = 2,
    val currentActiveSession: PhoneBrickSession? = null,
    val currentSessionRemainingMinutes: Int? = null,
    val currentSessionRemainingSeconds: Int? = null,

    // App tab state
    val blockedItems: List<BlockedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val blockedItemDao: BlockedItemDao,
    private val phoneBrickSessionDao: PhoneBrickSessionDao,
    private val brickSessionManager: BrickSessionManager,
    private val serviceOptimizer: ServiceOptimizer,
    private val globalSettingsManager: GlobalSettingsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private var sessionMonitorJob: Job? = null

    init {
        loadBlockedItems()
        observeCurrentSession()
    }

    private fun loadBlockedItems() {
        viewModelScope.launch {
            blockedItemDao.getAllBlockedItems().collect { items ->
                _uiState.update { it.copy(blockedItems = items) }
                serviceOptimizer.optimizeServices()
            }
        }
    }

    private fun observeCurrentSession() {
        sessionMonitorJob?.cancel()
        sessionMonitorJob = viewModelScope.launch {
            var lastSession: PhoneBrickSession? = null
            var lastMinutes: Int? = null
            var lastSeconds: Int? = null

            try {
                while (true) {
                    val currentSession = brickSessionManager.getCurrentSession()
                    val remainingMinutes = brickSessionManager.getCurrentSessionRemainingMinutes()
                    val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds()

                    // Only update state if values actually changed to avoid unnecessary recomposition
                    if (currentSession != lastSession ||
                        remainingMinutes != lastMinutes ||
                        remainingSeconds != lastSeconds) {

                        _uiState.update {
                            it.copy(
                                currentActiveSession = currentSession,
                                currentSessionRemainingMinutes = remainingMinutes,
                                currentSessionRemainingSeconds = remainingSeconds
                            )
                        }

                        lastSession = currentSession
                        lastMinutes = remainingMinutes
                        lastSeconds = remainingSeconds
                    }

                    val updateInterval = if ((remainingMinutes ?: 60) < 2) 1_000L else 10_000L
                    delay(updateInterval)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error monitoring session: ${e.message}") }
            }
        }
    }

    // Phone tab functions
    fun updateHours(hours: Int) {
        _uiState.update { it.copy(selectedHours = hours) }
    }

    fun updateMinutes(minutes: Int) {
        _uiState.update { it.copy(selectedMinutes = minutes) }
    }

    fun startLockSession() {
        viewModelScope.launch {
            try {
                // Check permissions first
                if (!PermissionHelper.areAllPermissionsGranted(context)) {
                    val missing = PermissionHelper.getMissingPermissions(context)
                    _uiState.update { it.copy(error = "Missing permissions: ${missing.joinToString(", ")}. Please grant all permissions first.") }
                    return@launch
                }

                val state = _uiState.value
                val totalMinutes = (state.selectedHours * 60) + state.selectedMinutes

                if (totalMinutes <= 0) {
                    _uiState.update { it.copy(error = "Please set a lock duration") }
                    return@launch
                }

                val session = PhoneBrickSession(
                    name = "Phone Lock",
                    sessionType = BrickSessionType.FOCUS_SESSION,
                    durationMinutes = totalMinutes,
                    isActive = true
                )

                val sessionId = phoneBrickSessionDao.insertSession(session)
                brickSessionManager.startDurationSession(sessionId)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to start lock session: ${e.message}") }
            }
        }
    }

    fun emergencyOverride() {
        viewModelScope.launch {
            try {
                val success = brickSessionManager.emergencyOverride("User requested from UI")
                if (!success) {
                    _uiState.update { it.copy(error = "Emergency override failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error during emergency override: ${e.message}") }
            }
        }
    }

    fun startTryLockSession(durationSeconds: Int) {
        viewModelScope.launch {
            try {
                // Check permissions first
                if (!PermissionHelper.areAllPermissionsGranted(context)) {
                    val missing = PermissionHelper.getMissingPermissions(context)
                    _uiState.update { it.copy(error = "Missing permissions: ${missing.joinToString(", ")}. Please grant all permissions first.") }
                    return@launch
                }

                // Convert seconds to minutes (minimum 1 minute for the session)
                // But we store actual seconds in a way that session manager can handle
                val durationMinutes = maxOf(1, (durationSeconds + 59) / 60)

                val session = PhoneBrickSession(
                    name = "Try Lock (${durationSeconds}s)",
                    sessionType = BrickSessionType.FOCUS_SESSION,
                    durationMinutes = durationMinutes,
                    isActive = true
                )

                val sessionId = phoneBrickSessionDao.insertSession(session)

                // For try lock, we need to handle seconds properly
                // Start the session with the actual seconds duration
                brickSessionManager.startTryLockSession(sessionId, durationSeconds)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to start try lock session: ${e.message}") }
            }
        }
    }

    // App tab functions

    /** Re-evaluates which services should run, e.g. after VPN consent was granted. */
    fun refreshServices() {
        serviceOptimizer.optimizeServices()
    }

    fun deleteBlockedItem(item: BlockedItem) {
        viewModelScope.launch {
            // Never delete through the UI while a commitment lock is in force
            val current = blockedItemDao.getBlockedItemById(item.id) ?: return@launch
            if (current.isCommitmentLocked()) {
                _uiState.update { it.copy(error = "This block is locked until the commitment ends.") }
                return@launch
            }
            blockedItemDao.deleteBlockedItem(current)
        }
    }

    // ============== COMMITMENT LOCKS ==============

    /** Locks the given items for [durationDays]; a blank phrase means no early unlock is possible. */
    fun lockItems(items: List<BlockedItem>, durationDays: Int, commitmentPhrase: String) {
        viewModelScope.launch {
            val expiresAt = System.currentTimeMillis() + durationDays.toLong() * 24L * 60 * 60 * 1000
            val phrase = commitmentPhrase.trim().ifBlank { null }
            for (item in items) {
                blockedItemDao.lockItem(item.id, expiresAt, phrase)
            }
        }
    }

    /** Starts the cooling-off period before the phrase may be typed. */
    fun requestEarlyUnlock(item: BlockedItem) {
        viewModelScope.launch {
            blockedItemDao.setUnlockRequested(item.id, System.currentTimeMillis())
        }
    }

    fun cancelEarlyUnlock(item: BlockedItem) {
        viewModelScope.launch {
            blockedItemDao.setUnlockRequested(item.id, null)
        }
    }

    /** Completes an early unlock once the wait is over and the phrase matches. */
    suspend fun unlockWithPhrase(item: BlockedItem, typedPhrase: String): Boolean {
        val current = blockedItemDao.getBlockedItemById(item.id) ?: return false
        if (!current.allowsEarlyUnlock()) return false
        val remaining = current.unlockWaitRemainingMs() ?: return false
        if (remaining > 0) return false
        if (current.lockCommitmentPhrase != typedPhrase) return false
        blockedItemDao.unlockItem(current.id)
        return true
    }

    // ============== PRIVATE SITE LIST ==============

    private val _sitesRevealed = MutableStateFlow(false)
    /** Website entries are shown in full only while this is true (or no PIN is set). */
    val sitesRevealed: StateFlow<Boolean> = _sitesRevealed.asStateFlow()
    val sitesPinSet: StateFlow<Boolean> = globalSettingsManager.sitesPinSet

    fun revealSites(pin: String): Boolean {
        val ok = globalSettingsManager.verifySitesPin(pin)
        if (ok) _sitesRevealed.value = true
        return ok
    }

    fun hideSites() {
        _sitesRevealed.value = false
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        sessionMonitorJob?.cancel()
    }
}
