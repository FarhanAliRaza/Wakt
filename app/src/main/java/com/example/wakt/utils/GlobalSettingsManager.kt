package com.example.wakt.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.wakt.data.database.entity.ChallengeType
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

    private val _challengeType = MutableStateFlow(getChallengeType())
    val challengeType: StateFlow<ChallengeType> = _challengeType.asStateFlow()

    private val _waitTimeMinutes = MutableStateFlow(getWaitTimeMinutes())
    val waitTimeMinutes: StateFlow<Int> = _waitTimeMinutes.asStateFlow()

    private val _clickCount = MutableStateFlow(getClickCount())
    val clickCount: StateFlow<Int> = _clickCount.asStateFlow()

    private val _defaultAllowedApps = MutableStateFlow(getDefaultAllowedApps())
    val defaultAllowedApps: StateFlow<Set<String>> = _defaultAllowedApps.asStateFlow()

    fun getChallengeType(): ChallengeType {
        val ordinal = prefs.getInt(KEY_CHALLENGE_TYPE, ChallengeType.WAIT.ordinal)
        return ChallengeType.values().getOrElse(ordinal) { ChallengeType.WAIT }
    }

    fun setChallengeType(type: ChallengeType) {
        prefs.edit().putInt(KEY_CHALLENGE_TYPE, type.ordinal).apply()
        _challengeType.value = type
    }

    fun getWaitTimeMinutes(): Int {
        return prefs.getInt(KEY_WAIT_TIME, DEFAULT_WAIT_TIME)
    }

    fun setWaitTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_WAIT_TIME, minutes.coerceIn(MIN_WAIT_TIME, MAX_WAIT_TIME)).apply()
        _waitTimeMinutes.value = minutes.coerceIn(MIN_WAIT_TIME, MAX_WAIT_TIME)
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

    companion object {
        private const val PREFS_NAME = "wakt_global_settings"
        private const val KEY_CHALLENGE_TYPE = "challenge_type"
        private const val KEY_WAIT_TIME = "wait_time_minutes"
        private const val KEY_CLICK_COUNT = "click_count"
        private const val KEY_DEFAULT_ALLOWED_APPS = "default_allowed_apps"
        private const val DEFAULT_WAIT_TIME = 10
        private const val DEFAULT_CLICK_COUNT = 500
        const val MIN_WAIT_TIME = 5
        const val MAX_WAIT_TIME = 30
        const val MIN_CLICK_COUNT = 100
        const val MAX_CLICK_COUNT = 1000
    }
}
