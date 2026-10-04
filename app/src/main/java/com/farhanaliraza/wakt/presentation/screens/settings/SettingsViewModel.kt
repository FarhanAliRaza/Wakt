package com.farhanaliraza.wakt.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.ServiceOptimizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val globalSettingsManager: GlobalSettingsManager,
    private val serviceOptimizer: ServiceOptimizer
) : ViewModel() {

    /** Re-evaluates background services, e.g. after a permission was granted. */
    fun refreshServices() {
        serviceOptimizer.optimizeServices()
    }

    val clickCount: StateFlow<Int> = globalSettingsManager.clickCount
    val defaultAllowedApps: StateFlow<Set<String>> = globalSettingsManager.defaultAllowedApps
    val emergencyExitEnabled: StateFlow<Boolean> = globalSettingsManager.emergencyExitEnabled
    val vpnExcludedApps: StateFlow<Set<String>> = globalSettingsManager.vpnExcludedApps
    val sitesPinSet: StateFlow<Boolean> = globalSettingsManager.sitesPinSet

    fun setSitesPin(pin: String) = globalSettingsManager.setSitesPin(pin)
    fun verifySitesPin(pin: String): Boolean = globalSettingsManager.verifySitesPin(pin)
    fun clearSitesPin() = globalSettingsManager.clearSitesPin()

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
