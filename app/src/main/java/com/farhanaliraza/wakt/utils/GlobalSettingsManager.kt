package com.farhanaliraza.wakt.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalSettingsManager @Inject constructor(
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _clickCount = MutableStateFlow(getClickCount())
    val clickCount: StateFlow<Int> = _clickCount.asStateFlow()

    private val _defaultAllowedApps = MutableStateFlow(getDefaultAllowedApps())
    val defaultAllowedApps: StateFlow<Set<String>> = _defaultAllowedApps.asStateFlow()

    private val _emergencyExitEnabled = MutableStateFlow(isEmergencyExitEnabled())
    val emergencyExitEnabled: StateFlow<Boolean> = _emergencyExitEnabled.asStateFlow()

    private val _vpnExcludedApps = MutableStateFlow(getVpnExcludedApps())
    /** Apps kept off the DNS website filter, e.g. banking apps that refuse to run with a VPN. */
    val vpnExcludedApps: StateFlow<Set<String>> = _vpnExcludedApps.asStateFlow()

    // ============== PRIVATE SITE LIST (PIN) ==============

    private val _sitesPinSet = MutableStateFlow(isSitesPinSet())
    /** True when the website list is hidden behind a PIN. */
    val sitesPinSet: StateFlow<Boolean> = _sitesPinSet.asStateFlow()

    fun isSitesPinSet(): Boolean = !prefs.getString(KEY_SITES_PIN_HASH, null).isNullOrBlank()

    fun setSitesPin(pin: String) {
        prefs.edit().putString(KEY_SITES_PIN_HASH, hashPin(pin)).apply()
        _sitesPinSet.value = true
    }

    fun clearSitesPin() {
        prefs.edit().remove(KEY_SITES_PIN_HASH).apply()
        _sitesPinSet.value = false
    }

    fun verifySitesPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_SITES_PIN_HASH, null) ?: return true
        return stored == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("wakt-sites-pin:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getLastUpstreamDns(): List<String> {
        val raw = prefs.getString(KEY_LAST_UPSTREAM_DNS, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun setLastUpstreamDns(servers: List<String>) {
        prefs.edit().putString(KEY_LAST_UPSTREAM_DNS, servers.joinToString(",")).apply()
    }

    fun getVpnExcludedApps(): Set<String> {
        val apps = prefs.getString(KEY_VPN_EXCLUDED_APPS, "") ?: ""
        return if (apps.isBlank()) emptySet() else apps.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setVpnExcludedApps(apps: Set<String>) {
        prefs.edit().putString(KEY_VPN_EXCLUDED_APPS, apps.joinToString(",")).apply()
        _vpnExcludedApps.value = apps
    }

    fun getClickCount(): Int {
        return prefs.getInt(KEY_CLICK_COUNT, DEFAULT_CLICK_COUNT)
    }

    fun setClickCount(count: Int) {
        prefs.edit().putInt(KEY_CLICK_COUNT, count.coerceIn(MIN_CLICK_COUNT, MAX_CLICK_COUNT)).apply()
        _clickCount.value = count.coerceIn(MIN_CLICK_COUNT, MAX_CLICK_COUNT)
    }

    fun getDefaultAllowedApps(): Set<String> {
        val apps = prefs.getString(KEY_DEFAULT_ALLOWED_APPS, "") ?: ""
        return if (apps.isBlank()) emptySet() else apps.split(",").toSet()
    }

    fun setDefaultAllowedApps(apps: Set<String>) {
        prefs.edit().putString(KEY_DEFAULT_ALLOWED_APPS, apps.joinToString(",")).apply()
        _defaultAllowedApps.value = apps
    }

    fun isEmergencyExitEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMERGENCY_EXIT_ENABLED, true)
    }

    fun setEmergencyExitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMERGENCY_EXIT_ENABLED, enabled).apply()
        _emergencyExitEnabled.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "wakt_global_settings"
        private const val KEY_CLICK_COUNT = "click_count"
        private const val KEY_DEFAULT_ALLOWED_APPS = "default_allowed_apps"
        private const val KEY_EMERGENCY_EXIT_ENABLED = "emergency_exit_enabled"
        private const val KEY_VPN_EXCLUDED_APPS = "vpn_excluded_apps"
        private const val KEY_SITES_PIN_HASH = "sites_pin_hash"
        private const val KEY_LAST_UPSTREAM_DNS = "last_upstream_dns"
        private const val DEFAULT_CLICK_COUNT = 500
        const val MIN_CLICK_COUNT = 100
        const val MAX_CLICK_COUNT = 1000
    }
}
