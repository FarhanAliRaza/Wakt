package com.example.wakt.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.example.wakt.utils.GlobalSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val globalSettingsManager: GlobalSettingsManager
) : ViewModel() {

    val clickCount: StateFlow<Int> = globalSettingsManager.clickCount
    val defaultAllowedApps: StateFlow<Set<String>> = globalSettingsManager.defaultAllowedApps
    val emergencyExitEnabled: StateFlow<Boolean> = globalSettingsManager.emergencyExitEnabled

    fun setClickCount(count: Int) {
        globalSettingsManager.setClickCount(count)
    }

    fun setDefaultAllowedApps(apps: Set<String>) {
        globalSettingsManager.setDefaultAllowedApps(apps)
    }

    fun setEmergencyExitEnabled(enabled: Boolean) {
        globalSettingsManager.setEmergencyExitEnabled(enabled)
    }
}
