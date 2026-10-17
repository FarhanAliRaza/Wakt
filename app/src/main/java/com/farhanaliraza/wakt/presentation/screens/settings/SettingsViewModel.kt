package com.farhanaliraza.wakt.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhanaliraza.wakt.data.database.dao.BlockedItemDao
import com.farhanaliraza.wakt.utils.BrickSessionManager
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.ServiceOptimizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val globalSettingsManager: GlobalSettingsManager,
    private val serviceOptimizer: ServiceOptimizer,
    private val blockedItemDao: BlockedItemDao,
    private val brickSessionManager: BrickSessionManager
) : ViewModel() {

    /** Re-evaluates background services, e.g. after a permission was granted. */
    fun refreshServices() {
        serviceOptimizer.optimizeServices()
    }

    /**
     * Something is in force that the user promised not to undo: a commitment-locked
     * block, a running lock session, or a locked filter pause. While true, the
     * uninstall and force-stop protection must not be removable from inside Wakt.
     */
    private val _commitmentActive = MutableStateFlow(false)
    val commitmentActive: StateFlow<Boolean> = _commitmentActive.asStateFlow()

    init {
        refreshCommitmentState()
    }

    fun refreshCommitmentState() {
        viewModelScope.launch {
            val lockedItem = blockedItemDao.getAllBlockedItemsList().any { it.isCommitmentLocked() }
            _commitmentActive.value = lockedItem ||
                brickSessionManager.isPhoneBricked() ||
                globalSettingsManager.getVpnPause()?.locked == true
        }
    }

    val clickCount: StateFlow<Int> = globalSettingsManager.clickCount
    val defaultAllowedApps: StateFlow<Set<String>> = globalSettingsManager.defaultAllowedApps
    val emergencyExitEnabled: StateFlow<Boolean> = globalSettingsManager.emergencyExitEnabled
    val vpnExcludedApps: StateFlow<Set<String>> = globalSettingsManager.vpnExcludedApps
    val sitesPinSet: StateFlow<Boolean> = globalSettingsManager.sitesPinSet

    fun setSitesPin(pin: String) = globalSettingsManager.setSitesPin(pin)
    fun verifySitesPin(pin: String): Boolean = globalSettingsManager.verifySitesPin(pin)
    fun clearSitesPin() = globalSettingsManager.clearSitesPin()

    /** Shows the first-run intro again on next composition. */
    fun replayOnboarding() = globalSettingsManager.setOnboardingCompleted(false)

    fun setVpnExcludedApps(apps: Set<String>) {
        globalSettingsManager.setVpnExcludedApps(apps)
        serviceOptimizer.optimizeServices()
    }

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
