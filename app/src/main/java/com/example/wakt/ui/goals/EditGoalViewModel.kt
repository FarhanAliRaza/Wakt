package com.example.wakt.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.GoalBlockItem
import com.example.wakt.data.repository.GoalBlockRepository
import com.example.wakt.presentation.screens.addblock.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditGoalUiState(
    val isLoading: Boolean = false,
    val currentItems: List<GoalBlockItem> = emptyList(),
    val showAppSelector: Boolean = false,
    val showWebsiteInput: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class EditGoalViewModel @Inject constructor(
    private val goalBlockRepository: GoalBlockRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EditGoalUiState())
    val uiState: StateFlow<EditGoalUiState> = _uiState.asStateFlow()
    
    private var currentGoalId: Long = 0
    
    fun loadGoal(goalId: Long) {
        currentGoalId = goalId
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        
        viewModelScope.launch {
            goalBlockRepository.getItemsForGoal(goalId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load goal items: ${e.message}"
                    )
                }
                .collect { items ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentItems = items,
                        errorMessage = null
                    )
                }
        }
    }
    
    fun showAppSelector() {
        _uiState.value = _uiState.value.copy(showAppSelector = true)
    }
    
    fun hideAppSelector() {
        _uiState.value = _uiState.value.copy(showAppSelector = false)
    }
    
    fun showWebsiteInput() {
        _uiState.value = _uiState.value.copy(showWebsiteInput = true)
    }
    
    fun hideWebsiteInput() {
        _uiState.value = _uiState.value.copy(showWebsiteInput = false)
    }
    
    fun addAppsToGoal(apps: List<AppInfo>) {
        viewModelScope.launch {
            try {
                apps.forEach { app ->
                    goalBlockRepository.addItemToGoal(
                        goalId = currentGoalId,
                        itemName = app.name,
                        itemType = BlockType.APP,
                        packageOrUrl = app.packageName
                    )
                }
                clearError()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add apps: ${e.message}"
                )
            }
        }
    }
    
    fun addWebsiteToGoal(url: String) {
        viewModelScope.launch {
            try {
                goalBlockRepository.addItemToGoal(
                    goalId = currentGoalId,
                    itemName = url,
                    itemType = BlockType.WEBSITE,
                    packageOrUrl = url
                )
                clearError()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add website: ${e.message}"
                )
            }
        }
    }
    
    // Items cannot be removed from long-term goals to maintain commitment integrity
    // This method is intentionally removed to prevent any UI from attempting item removal
    
    private fun clearError() {
        if (_uiState.value.errorMessage != null) {
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }
}