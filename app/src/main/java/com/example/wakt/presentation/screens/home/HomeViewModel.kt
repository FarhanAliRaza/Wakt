package com.example.wakt.presentation.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.entity.BlockedItem
import com.example.wakt.utils.PermissionHelper
import com.example.wakt.utils.ServiceOptimizer
import com.example.wakt.utils.DeviceAdminManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val blockedItems: List<BlockedItem> = emptyList(),
    val isBlockingEnabled: Boolean = true,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val blockedItemDao: BlockedItemDao,
    private val serviceOptimizer: ServiceOptimizer,
    val deviceAdminManager: DeviceAdminManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadBlockedItems()
    }
    
    private fun loadBlockedItems() {
        viewModelScope.launch {
            blockedItemDao.getAllBlockedItems().collect { items ->
                _uiState.update { it.copy(blockedItems = items) }
                // Battery optimization: Optimize services when blocked items change
                serviceOptimizer.optimizeServices()
            }
        }
    }
    
    fun toggleBlocking(enabled: Boolean) {
        _uiState.update { it.copy(isBlockingEnabled = enabled) }
        
        // VPN service start/stop removed for battery optimization
        // Only accessibility service is now used for blocking
        /*
        if (enabled) {
            // Start VPN service for website blocking
            if (PermissionHelper.isVpnPermissionGranted(context)) {
                PermissionHelper.startVpnService(context)
            }
        } else {
            // Stop VPN service
            PermissionHelper.stopVpnService(context)
        }
        */
    }
    
    fun deleteBlockedItem(item: BlockedItem) {
        viewModelScope.launch {
            blockedItemDao.deleteBlockedItem(item)
        }
    }
}