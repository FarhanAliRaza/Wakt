package com.example.wakt.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.utils.GlobalSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val globalSettingsManager: GlobalSettingsManager
) : ViewModel() {

    val challengeType: StateFlow<ChallengeType> = globalSettingsManager.challengeType
    val waitTimeMinutes: StateFlow<Int> = globalSettingsManager.waitTimeMinutes
    val clickCount: StateFlow<Int> = globalSettingsManager.clickCount
    val defaultAllowedApps: StateFlow<Set<String>> = globalSettingsManager.defaultAllowedApps

    fun setChallengeType(type: ChallengeType) {
        globalSettingsManager.setChallengeType(type)
    }

    fun setWaitTimeMinutes(minutes: Int) {
        globalSettingsManager.setWaitTimeMinutes(minutes)
    }

    fun setClickCount(count: Int) {
        globalSettingsManager.setClickCount(count)
    }

    fun setDefaultAllowedApps(apps: Set<String>) {
        globalSettingsManager.setDefaultAllowedApps(apps)
    }
}
