package com.example.wakt.presentation.activities.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.entity.EssentialApp
import com.example.wakt.utils.BrickSessionManager
import com.example.wakt.utils.EssentialAppsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrickLauncherViewModel @Inject constructor(
    private val brickSessionManager: BrickSessionManager,
    private val essentialAppsManager: EssentialAppsManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BrickLauncherUiState())
    val uiState: StateFlow<BrickLauncherUiState> = _uiState.asStateFlow()
    
    init {
        loadEssentialApps()
    }
    
    private fun loadEssentialApps() {
        viewModelScope.launch {
            try {
                val currentSession = brickSessionManager.getCurrentSession()
                if (currentSession != null) {
                    val essentialApps = essentialAppsManager.getEssentialAppsForSessionType(
                        currentSession.sessionType
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        essentialApps = essentialApps,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        essentialApps = emptyList(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    essentialApps = emptyList(),
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun refreshEssentialApps() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadEssentialApps()
    }
}

data class BrickLauncherUiState(
    val essentialApps: List<EssentialApp> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)