package com.example.wakt.presentation.activities.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.utils.TimerPersistence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockingOverlayViewModel @Inject constructor(
    private val timerPersistence: TimerPersistence
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BlockingOverlayUiState())
    val uiState: StateFlow<BlockingOverlayUiState> = _uiState.asStateFlow()
    
    private var timerJob: Job? = null
    private var currentPackageName: String = ""
    
    fun initializeChallenge(packageName: String, challengeType: ChallengeType, challengeData: String) {
        currentPackageName = packageName
        
        when (challengeType) {
            ChallengeType.WAIT -> {
                val waitTimeMinutes = challengeData.toIntOrNull() ?: 10
                checkExistingTimerOrShowButton(packageName, waitTimeMinutes)
            }
            ChallengeType.CLICK_500 -> {
                // Initialize click challenge
                _uiState.value = _uiState.value.copy(
                    challengeType = challengeType,
                    clickCount = 0,
                    hasUserRequestedAccess = true
                )
            }

            ChallengeType.QUESTION -> TODO()
        }
    }
    
    private fun checkExistingTimerOrShowButton(packageName: String, waitTimeMinutes: Int) {
        val remainingSeconds = timerPersistence.getRemainingTimeSeconds(packageName)
        
        if (remainingSeconds != null && remainingSeconds > 0) {
            // Timer is already running, resume it
            resumeExistingTimer(remainingSeconds, waitTimeMinutes)
        } else {
            // No active timer, show "Request Access" button
            _uiState.value = _uiState.value.copy(
                challengeType = ChallengeType.WAIT,
                totalTimeMinutes = waitTimeMinutes,
                remainingTimeSeconds = 0,
                hasUserRequestedAccess = false,
                canRequestAccess = true
            )
        }
    }
    
    fun requestAccess() {
        val waitTimeMinutes = _uiState.value.totalTimeMinutes
        if (waitTimeMinutes > 0) {
            startNewTimer(currentPackageName, waitTimeMinutes)
        }
    }
    
    private fun startNewTimer(packageName: String, waitTimeMinutes: Int) {
        val totalSeconds = waitTimeMinutes * 60
        val currentTime = System.currentTimeMillis()
        
        // Save timer state to persistent storage
        timerPersistence.saveTimerState(packageName, currentTime, waitTimeMinutes)
        
        _uiState.value = _uiState.value.copy(
            hasUserRequestedAccess = true,
            canRequestAccess = false,
            totalTimeSeconds = totalSeconds,
            remainingTimeSeconds = totalSeconds
        )
        
        startTimerCountdown(totalSeconds)
    }
    
    private fun resumeExistingTimer(remainingSeconds: Int, totalMinutes: Int) {
        _uiState.value = _uiState.value.copy(
            challengeType = ChallengeType.WAIT,
            hasUserRequestedAccess = true,
            canRequestAccess = false,
            totalTimeMinutes = totalMinutes,
            totalTimeSeconds = totalMinutes * 60,
            remainingTimeSeconds = remainingSeconds
        )
        
        startTimerCountdown(remainingSeconds)
    }
    
    private fun startTimerCountdown(initialSeconds: Int) {
        timerJob?.cancel()
        
        timerJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1000) // Wait 1 second
                remaining--
                _uiState.value = _uiState.value.copy(remainingTimeSeconds = remaining)
                
                // Check if timer was cleared externally (shouldn't happen, but safety check)
                if (!timerPersistence.isTimerRunning(currentPackageName)) {
                    break
                }
            }
            
            // Timer completed
            if (remaining <= 0) {
                timerPersistence.clearTimerState(currentPackageName)
                _uiState.value = _uiState.value.copy(
                    remainingTimeSeconds = 0,
                    timerCompleted = true
                )
            }
        }
    }
    
    fun stopTimer() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(remainingTimeSeconds = 0)
    }
    
    fun incrementClickCount() {
        val currentCount = _uiState.value.clickCount
        _uiState.value = _uiState.value.copy(clickCount = currentCount + 1)
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

data class BlockingOverlayUiState(
    val challengeType: ChallengeType = ChallengeType.WAIT,
    val totalTimeMinutes: Int = 0,
    val totalTimeSeconds: Int = 0,
    val remainingTimeSeconds: Int = 0,
    val customQuestionData: String = "",
    val isAnswerCorrect: Boolean = false,
    val hasUserRequestedAccess: Boolean = false,
    val canRequestAccess: Boolean = false,
    val timerCompleted: Boolean = false,
    val clickCount: Int = 0
)