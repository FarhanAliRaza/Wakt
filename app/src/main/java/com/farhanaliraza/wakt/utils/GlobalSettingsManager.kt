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
        private const val DEFAULT_CLICK_COUNT = 500
        const val MIN_CLICK_COUNT = 100
        const val MAX_CLICK_COUNT = 1000
    }
}
