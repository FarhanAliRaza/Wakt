package com.example.wakt.presentation.activities.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.presentation.activities.EmergencyStep
import com.example.wakt.utils.BrickSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmergencyOverrideViewModel @Inject constructor(
    private val brickSessionManager: BrickSessionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EmergencyOverrideUiState())
    val uiState: StateFlow<EmergencyOverrideUiState> = _uiState.asStateFlow()
    
    init {
        initializeChallenge()
    }
    
    private fun initializeChallenge() {
        val currentSession = brickSessionManager.getCurrentSession()
        if (currentSession != null) {
            _uiState.value = _uiState.value.copy(
                // For emergency override, always use CLICK challenge with 200 clicks
                challengeType = ChallengeType.CLICK_500,
                challengeTimeRemaining = currentSession.challengeData.toIntOrNull() ?: 5,
                clicksRemaining = 200,
                sessionName = currentSession.name
            )
        }
    }
    
    fun confirmEmergency() {
        _uiState.value = _uiState.value.copy(
            currentStep = EmergencyStep.CHALLENGE
        )
        
        // Start challenge countdown if it's a wait challenge
        if (_uiState.value.challengeType == ChallengeType.WAIT) {
            startChallengeCountdown()
        }
    }
    
    private fun startChallengeCountdown() {
        viewModelScope.launch {
            var remaining = _uiState.value.challengeTimeRemaining
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(challengeTimeRemaining = remaining)
            }
        }
    }
    
    fun completeChallenge() {
        _uiState.value = _uiState.value.copy(
            challengeCompleted = true
        )
        // Immediately execute emergency override after challenge is complete
        executeEmergencyOverride()
    }

    fun recordClick() {
        val clicksLeft = _uiState.value.clicksRemaining - 1
        _uiState.value = _uiState.value.copy(clicksRemaining = clicksLeft)

        // When all 200 clicks are done, complete the challenge and execute override
        if (clicksLeft <= 0) {
            completeChallenge()
        }
    }

    fun updateReason(reason: String) {
        _uiState.value = _uiState.value.copy(emergencyReason = reason)
    }
    
    fun executeEmergencyOverride() {
        viewModelScope.launch {
            try {
                val success = brickSessionManager.emergencyOverride(_uiState.value.emergencyReason)
                _uiState.value = _uiState.value.copy(
                    overrideCompleted = success,
                    error = if (success) null else "Failed to execute emergency override"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error during emergency override: ${e.message}"
                )
            }
        }
    }
}

data class EmergencyOverrideUiState(
    val currentStep: EmergencyStep = EmergencyStep.CONFIRMATION,
    val challengeType: ChallengeType = ChallengeType.WAIT,
    val challengeTimeRemaining: Int = 5,
    val clicksRemaining: Int = 200,
    val challengeCompleted: Boolean = false,
    val emergencyReason: String = "",
    val sessionName: String = "",
    val overrideCompleted: Boolean = false,
    val error: String? = null
)