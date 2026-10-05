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

    // ============== ONBOARDING ==============

    private val _onboardingCompleted = MutableStateFlow(isOnboardingCompleted())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _onboardingCompleted.value = completed
    }

    // ============== GOAL WALLPAPER ==============

    private val _goalWallpaperEnabled = MutableStateFlow(isGoalWallpaperEnabled())
    val goalWallpaperEnabled: StateFlow<Boolean> = _goalWallpaperEnabled.asStateFlow()
    private val _goalWallpaperTarget = MutableStateFlow(getGoalWallpaperTarget())
    val goalWallpaperTarget: StateFlow<String> = _goalWallpaperTarget.asStateFlow()

    fun isGoalWallpaperEnabled(): Boolean = prefs.getBoolean(KEY_GOAL_WALLPAPER_ENABLED, false)

    fun setGoalWallpaperEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GOAL_WALLPAPER_ENABLED, enabled).apply()
        _goalWallpaperEnabled.value = enabled
    }

    private val _goalWallpaperShowTitles = MutableStateFlow(isGoalWallpaperShowTitles())
    /** Whether goal names are drawn on the wallpaper (off = just the numbers and grid). */
    val goalWallpaperShowTitles: StateFlow<Boolean> = _goalWallpaperShowTitles.asStateFlow()

    fun isGoalWallpaperShowTitles(): Boolean = prefs.getBoolean(KEY_GOAL_WALLPAPER_TITLES, true)

    fun setGoalWallpaperShowTitles(show: Boolean) {
        prefs.edit().putBoolean(KEY_GOAL_WALLPAPER_TITLES, show).apply()
        _goalWallpaperShowTitles.value = show
    }

    /** "lock", "home" or "both". */
    fun getGoalWallpaperTarget(): String = prefs.getString(KEY_GOAL_WALLPAPER_TARGET, "lock") ?: "lock"

    fun setGoalWallpaperTarget(target: String) {
        prefs.edit().putString(KEY_GOAL_WALLPAPER_TARGET, target).apply()
        _goalWallpaperTarget.value = target
    }

    // ============== WEBSITE FILTER PAUSE ==============

    /**
     * A temporary pause of the DNS website filter so a banking app that refuses
     * to run with a VPN can be used. Either a short plain pause ([sessionId] is
     * 0) or one tied to brick session [sessionId] that locks the phone to
     * [targetPackage] for its duration.
     */
    data class VpnPause(val until: Long, val targetPackage: String, val targetLabel: String, val sessionId: Long) {
        val locked: Boolean get() = sessionId != 0L
    }

    private val _vpnPause = MutableStateFlow(getVpnPause())
    val vpnPause: StateFlow<VpnPause?> = _vpnPause.asStateFlow()

    /** The current pause, or null when none is set or it has already expired. */
    fun getVpnPause(now: Long = System.currentTimeMillis()): VpnPause? {
        val until = prefs.getLong(KEY_VPN_PAUSE_UNTIL, 0L)
        if (until <= now) return null
        val target = prefs.getString(KEY_VPN_PAUSE_TARGET, "") ?: ""
        return VpnPause(
            until = until,
            targetPackage = target,
            targetLabel = prefs.getString(KEY_VPN_PAUSE_LABEL, target) ?: target,
            sessionId = prefs.getLong(KEY_VPN_PAUSE_SESSION, 0L)
        )
    }

    fun setVpnPause(pause: VpnPause) {
        prefs.edit()
            .putLong(KEY_VPN_PAUSE_UNTIL, pause.until)
            .putString(KEY_VPN_PAUSE_TARGET, pause.targetPackage)
            .putString(KEY_VPN_PAUSE_LABEL, pause.targetLabel)
            .putLong(KEY_VPN_PAUSE_SESSION, pause.sessionId)
            .apply()
        _vpnPause.value = pause
    }

    /** What the user picked last time, so the pause dialog opens pre-filled. */
    data class PauseChoice(val lockMode: Boolean, val targetPackage: String?, val plainMinutes: Int, val lockMinutes: Int)

    fun getLastPauseChoice(): PauseChoice = PauseChoice(
        lockMode = prefs.getBoolean(KEY_PAUSE_LAST_LOCK_MODE, false),
        targetPackage = prefs.getString(KEY_PAUSE_LAST_TARGET, null)?.ifBlank { null },
        plainMinutes = prefs.getInt(KEY_PAUSE_LAST_PLAIN_MIN, 2),
        lockMinutes = prefs.getInt(KEY_PAUSE_LAST_LOCK_MIN, 5)
    )

    fun setLastPauseChoice(choice: PauseChoice) {
        prefs.edit()
            .putBoolean(KEY_PAUSE_LAST_LOCK_MODE, choice.lockMode)
            .putString(KEY_PAUSE_LAST_TARGET, choice.targetPackage ?: "")
            .putInt(KEY_PAUSE_LAST_PLAIN_MIN, choice.plainMinutes)
            .putInt(KEY_PAUSE_LAST_LOCK_MIN, choice.lockMinutes)
            .apply()
    }

    fun clearVpnPause() {
        prefs.edit()
            .remove(KEY_VPN_PAUSE_UNTIL)
            .remove(KEY_VPN_PAUSE_TARGET)
            .remove(KEY_VPN_PAUSE_LABEL)
            .remove(KEY_VPN_PAUSE_SESSION)
            .apply()
        _vpnPause.value = null
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
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_GOAL_WALLPAPER_ENABLED = "goal_wallpaper_enabled"
        private const val KEY_GOAL_WALLPAPER_TARGET = "goal_wallpaper_target"
        private const val KEY_GOAL_WALLPAPER_TITLES = "goal_wallpaper_show_titles"
        private const val KEY_VPN_PAUSE_UNTIL = "vpn_pause_until"
        private const val KEY_VPN_PAUSE_TARGET = "vpn_pause_target"
        private const val KEY_VPN_PAUSE_LABEL = "vpn_pause_label"
        private const val KEY_VPN_PAUSE_SESSION = "vpn_pause_session"
        private const val KEY_PAUSE_LAST_LOCK_MODE = "pause_last_lock_mode"
        private const val KEY_PAUSE_LAST_TARGET = "pause_last_target"
        private const val KEY_PAUSE_LAST_PLAIN_MIN = "pause_last_plain_minutes"
        private const val KEY_PAUSE_LAST_LOCK_MIN = "pause_last_lock_minutes"
        private const val DEFAULT_CLICK_COUNT = 500
        const val MIN_CLICK_COUNT = 100
        const val MAX_CLICK_COUNT = 1000
    }
}
