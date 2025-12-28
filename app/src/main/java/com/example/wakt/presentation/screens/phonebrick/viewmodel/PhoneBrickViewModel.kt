package com.example.wakt.presentation.screens.phonebrick.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.dao.PhoneBrickSessionDao
import com.example.wakt.data.database.entity.BrickSessionType
import com.example.wakt.data.database.entity.EssentialApp
import com.example.wakt.data.database.entity.PhoneBrickSession
import com.example.wakt.presentation.screens.phonebrick.SessionConfig
import com.example.wakt.utils.BrickSessionManager
import com.example.wakt.utils.EssentialAppsManager
import com.example.wakt.utils.GlobalSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class PhoneBrickViewModel @Inject constructor(
    private val phoneBrickSessionDao: PhoneBrickSessionDao,
    private val brickSessionManager: BrickSessionManager,
    private val essentialAppsManager: EssentialAppsManager,
    private val globalSettingsManager: GlobalSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneBrickUiState())
    val uiState: StateFlow<PhoneBrickUiState> = _uiState.asStateFlow()

    val defaultAllowedApps: StateFlow<Set<String>> = globalSettingsManager.defaultAllowedApps
    
    private var sessionMonitorJob: Job? = null
    
    init {
        loadSessions()
        loadEssentialApps()
        observeCurrentSession()
    }
    
    private fun loadSessions() {
        viewModelScope.launch {
            phoneBrickSessionDao.getAllActiveSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    isLoading = false
                )
            }
        }
    }
    
    private fun loadEssentialApps() {
        viewModelScope.launch {
            essentialAppsManager.getAllEssentialApps().collect { apps ->
                _uiState.value = _uiState.value.copy(
                    essentialApps = apps
                )
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
                // Monitor current session state with proper cancellation
                while (true) {
                    val currentSession = brickSessionManager.getCurrentSession()
                    val remainingMinutes = brickSessionManager.getCurrentSessionRemainingMinutes()
                    val remainingSeconds = brickSessionManager.getCurrentSessionRemainingSeconds()

                    // Only update state if values actually changed to avoid unnecessary recomposition
                    if (currentSession != lastSession ||
                        remainingMinutes != lastMinutes ||
                        remainingSeconds != lastSeconds) {

                        _uiState.value = _uiState.value.copy(
                            currentActiveSession = currentSession,
                            currentSessionRemainingMinutes = remainingMinutes,
                            currentSessionRemainingSeconds = remainingSeconds
                        )

                        lastSession = currentSession
                        lastMinutes = remainingMinutes
                        lastSeconds = remainingSeconds
                    }

                    // Update more frequently when less than 2 minutes remaining
                    val updateInterval = if ((remainingMinutes ?: 60) < 2) 1_000L else 10_000L
                    delay(updateInterval)
                }
            } catch (e: Exception) {
                // Handle any exceptions to prevent crashes
                _uiState.value = _uiState.value.copy(
                    error = "Error monitoring session: ${e.message}"
                )
            }
        }
    }
    
    fun createSession(sessionType: BrickSessionType, name: String, config: SessionConfig) {
        viewModelScope.launch {
            try {
                val session = PhoneBrickSession(
                    name = name,
                    sessionType = sessionType,
                    durationMinutes = config.durationMinutes,
                    startHour = config.startHour,
                    startMinute = config.startMinute,
                    endHour = config.endHour,
                    endMinute = config.endMinute,
                    activeDaysOfWeek = config.activeDaysOfWeek,
                    allowedApps = config.selectedEssentialApps.joinToString(",") // Save session-specific allowed apps
                    // allowEmergencyOverride is always true by default for safety
                )

                phoneBrickSessionDao.insertSession(session)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create session: ${e.message}"
                )
            }
        }
    }
    
    fun startSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                val success = brickSessionManager.startDurationSession(sessionId)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to start session"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error starting session: ${e.message}"
                )
            }
        }
    }
    
    fun startQuickFocusSession(durationMinutes: Int) {
        viewModelScope.launch {
            try {
                // Create a temporary focus session
                val session = PhoneBrickSession(
                    name = "Quick Focus ($durationMinutes min)",
                    sessionType = BrickSessionType.FOCUS_SESSION,
                    durationMinutes = durationMinutes,
                    isActive = true
                    // allowEmergencyOverride is always true by default for safety
                )
                
                val sessionId = phoneBrickSessionDao.insertSession(session)
                brickSessionManager.startDurationSession(sessionId)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to start quick focus session: ${e.message}"
                )
            }
        }
    }
    
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                phoneBrickSessionDao.deleteSessionById(sessionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete session: ${e.message}"
                )
            }
        }
    }
    
    fun toggleSessionActive(sessionId: Long) {
        viewModelScope.launch {
            try {
                val session = phoneBrickSessionDao.getSessionById(sessionId)
                if (session != null) {
                    if (session.isActive) {
                        phoneBrickSessionDao.deactivateSession(sessionId)
                    } else {
                        phoneBrickSessionDao.activateSession(sessionId)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to toggle session: ${e.message}"
                )
            }
        }
    }
    
    fun emergencyOverride() {
        viewModelScope.launch {
            try {
                val success = brickSessionManager.emergencyOverride("User requested from UI")
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        error = "Emergency override failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error during emergency override: ${e.message}"
                )
            }
        }
    }
    
    fun addEssentialApp(appName: String, packageName: String) {
        viewModelScope.launch {
            try {
                val success = essentialAppsManager.addEssentialApp(appName, packageName)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to add essential app"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error adding essential app: ${e.message}"
                )
            }
        }
    }
    
    fun removeEssentialApp(packageName: String) {
        viewModelScope.launch {
            try {
                val success = essentialAppsManager.removeEssentialApp(packageName)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to remove essential app"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error removing essential app: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        sessionMonitorJob?.cancel()
    }
}

data class PhoneBrickUiState(
    val sessions: List<PhoneBrickSession> = emptyList(),
    val essentialApps: List<EssentialApp> = emptyList(),
    val currentActiveSession: PhoneBrickSession? = null,
    val currentSessionRemainingMinutes: Int? = null,
    val currentSessionRemainingSeconds: Int? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)