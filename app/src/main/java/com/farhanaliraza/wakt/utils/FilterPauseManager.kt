package com.farhanaliraza.wakt.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.farhanaliraza.wakt.data.database.dao.BlockedItemDao
import com.farhanaliraza.wakt.data.database.dao.PhoneBrickSessionDao
import com.farhanaliraza.wakt.data.database.entity.BlockType
import com.farhanaliraza.wakt.data.database.entity.BrickSessionType
import com.farhanaliraza.wakt.data.database.entity.PhoneBrickSession
import com.farhanaliraza.wakt.receivers.VpnResumeReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporarily turns the DNS website filter off for one app without opening an
 * escape hatch: the pause is tied to a Phone lock in which only that app can be
 * used, lasts at most [MAX_LOCK_MINUTES], and the filter restarts the moment the lock
 * ends, whether by timer or by an early exit ([BrickSessionManager.exitBrickMode]).
 *
 * Meant for banking apps that refuse to work while any VPN is active. Browsers
 * and blocked apps can never be the target.
 */
@Singleton
class FilterPauseManager @Inject constructor(
    private val context: Context,
    private val phoneBrickSessionDao: PhoneBrickSessionDao,
    private val blockedItemDao: BlockedItemDao,
    private val brickSessionManager: BrickSessionManager,
    private val serviceOptimizer: ServiceOptimizer,
    private val globalSettingsManager: GlobalSettingsManager
) {
    companion object {
        private const val TAG = "FilterPauseManager"
        /** Durations offered for a pause that locks the phone to one app. */
        val LOCK_DURATION_OPTIONS = listOf(1, 2, 5, 10, 15, 30)
        const val MAX_LOCK_MINUTES = 30
        /** Durations offered for a plain pause (no lock): short on purpose. */
        val PLAIN_DURATION_OPTIONS = listOf(1, 2, 3, 5, 10)
        const val MAX_PLAIN_MINUTES = 10
    }

    /** App that can be chosen as the target of a pause. */
    data class PausableApp(val label: String, val packageName: String)

    /** Launchable apps minus browsers, blocked apps and Wakt itself, sorted by name. */
    suspend fun pausableApps(): List<PausableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val blockedPackages = blockedItemDao.getAllBlockedItemsList()
            .filter { it.type == BlockType.APP }
            .map { it.packageNameOrUrl }
            .toSet()
        val browsers = browserPackages()
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { it.packageName !in browsers && it.packageName !in blockedPackages }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .mapNotNull { info ->
                try {
                    PausableApp(info.loadLabel(pm).toString(), info.packageName)
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Apps that handle arbitrary http links, i.e. web browsers (apps with host-specific links do not match). */
    private fun browserPackages(): Set<String> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://wakt-browser-probe.invalid/"))
        return try {
            context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Could not query browsers", e)
            emptySet()
        }
    }

    sealed class PauseResult {
        object Started : PauseResult()
        data class Refused(val reason: String) : PauseResult()
    }

    /**
     * Stops the filter for [minutes] (at most [MAX_PLAIN_MINUTES]) without any
     * lock. The short cap is the friction: long enough to log in to a bank app,
     * too short to be worth using as a way around the blocks.
     */
    suspend fun pausePlain(minutes: Int): PauseResult {
        val duration = minutes.coerceIn(1, MAX_PLAIN_MINUTES)
        if (globalSettingsManager.getVpnPause() != null) {
            return PauseResult.Refused("The filter is already paused.")
        }
        val until = System.currentTimeMillis() + duration * 60_000L
        globalSettingsManager.setVpnPause(
            GlobalSettingsManager.VpnPause(until = until, targetPackage = "", targetLabel = "", sessionId = 0L)
        )
        globalSettingsManager.setLastPauseChoice(
            globalSettingsManager.getLastPauseChoice().copy(lockMode = false, plainMinutes = duration)
        )
        VpnResumeReceiver.schedule(context, until)
        serviceOptimizer.optimizeServices()
        Log.i(TAG, "Website filter paused for $duration min (no lock)")
        return PauseResult.Started
    }

    /**
     * Stops the filter and locks the phone to [packageName] for [minutes]. The
     * lock session is what keeps the pause honest, so the pause is refused when
     * a session cannot be started.
     */
    suspend fun pauseForApp(packageName: String, minutes: Int): PauseResult {
        val duration = minutes.coerceIn(1, MAX_LOCK_MINUTES)
        if (brickSessionManager.isPhoneBricked()) {
            return PauseResult.Refused("A lock is already running. Wait for it to end first.")
        }
        if (globalSettingsManager.getVpnPause() != null) {
            return PauseResult.Refused("The filter is already paused.")
        }
        if (!PermissionHelper.areAllPermissionsGranted(context)) {
            return PauseResult.Refused("Grant the blocking permissions first; the pause needs the phone lock.")
        }
        if (packageName == context.packageName || packageName in browserPackages()) {
            return PauseResult.Refused("Browsers can't be chosen for a pause.")
        }
        val blocked = withContext(Dispatchers.IO) { blockedItemDao.getActiveBlockedItem(packageName) }
        if (blocked != null) {
            return PauseResult.Refused("Blocked apps can't be chosen for a pause.")
        }

        val label = try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: Exception) {
            packageName
        }

        val session = PhoneBrickSession(
            name = "Filter pause: $label",
            sessionType = BrickSessionType.FOCUS_SESSION,
            durationMinutes = duration,
            isActive = true,
            allowedApps = packageName
        )
        val sessionId = withContext(Dispatchers.IO) { phoneBrickSessionDao.insertSession(session) }
        val until = System.currentTimeMillis() + duration * 60_000L

        // Record the pause before the lock starts so nothing restarts the VPN in between.
        globalSettingsManager.setVpnPause(
            GlobalSettingsManager.VpnPause(until = until, targetPackage = packageName, targetLabel = label, sessionId = sessionId)
        )
        val started = brickSessionManager.startDurationSession(sessionId)
        if (!started) {
            globalSettingsManager.clearVpnPause()
            withContext(Dispatchers.IO) { phoneBrickSessionDao.deleteSessionById(sessionId) }
            return PauseResult.Refused("Could not start the lock, so the filter stays on.")
        }
        globalSettingsManager.setLastPauseChoice(
            globalSettingsManager.getLastPauseChoice().copy(lockMode = true, targetPackage = packageName, lockMinutes = duration)
        )
        VpnResumeReceiver.schedule(context, until)
        serviceOptimizer.optimizeServices()
        Log.i(TAG, "Website filter paused for $label ($packageName) for $duration min")
        return PauseResult.Started
    }

    /** Ends the pause early: finishes its lock session (which restarts the filter) or just restarts the filter. */
    suspend fun resumeNow() {
        val pause = globalSettingsManager.getVpnPause()
        val current = brickSessionManager.getCurrentSession()
        if (pause != null && pause.locked && current != null && current.id == pause.sessionId) {
            // exitBrickMode clears the pause and re-optimizes services
            brickSessionManager.completeCurrentSession()
        } else {
            globalSettingsManager.clearVpnPause()
            VpnResumeReceiver.cancel(context)
            serviceOptimizer.optimizeServices()
        }
        Log.i(TAG, "Website filter pause ended by user")
    }
}
