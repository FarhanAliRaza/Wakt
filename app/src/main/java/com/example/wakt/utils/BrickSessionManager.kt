package com.example.wakt.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.wakt.data.database.dao.PhoneBrickSessionDao
import com.example.wakt.data.database.dao.BrickSessionLogDao
import com.example.wakt.data.database.entity.*
import com.example.wakt.presentation.activities.BrickLauncherActivity
import com.example.wakt.services.BrickEnforcementService
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrickSessionManager @Inject constructor(
    private val context: Context,
    private val phoneBrickSessionDao: PhoneBrickSessionDao,
    private val brickSessionLogDao: BrickSessionLogDao,
    private val essentialAppsManager: EssentialAppsManager
) {
    companion object {
        private const val TAG = "BrickSessionManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentSessionMonitorJob: Job? = null
    private var scheduleCheckJob: Job? = null
    
    // Current session state
    private var currentBrickSession: PhoneBrickSession? = null
    private var currentSessionLog: BrickSessionLog? = null
    
    init {
        startScheduleMonitoring()
        checkForOngoingSession()
    }
    
    /**
     * Start a duration-based session (Focus Session or Digital Detox)
     */
    suspend fun startDurationSession(sessionId: Long): Boolean {
        try {
            // Fetch session first (on IO thread)
            val session = withContext(Dispatchers.IO) {
                phoneBrickSessionDao.getSessionById(sessionId)
            } ?: return false

            if (session.durationMinutes == null) {
                Log.e(TAG, "Cannot start duration session without duration: ${session.name}")
                return false
            }

            val currentTime = System.currentTimeMillis()
            val endTime = currentTime + (session.durationMinutes * 60 * 1000L)

            // Perform all DB operations on IO thread
            val logId = withContext(Dispatchers.IO) {
                // Start the session in database
                phoneBrickSessionDao.startSession(sessionId, currentTime, endTime)

                // Create session log
                val sessionLog = BrickSessionLog(
                    sessionId = sessionId,
                    sessionStartTime = currentTime,
                    scheduledDurationMinutes = session.durationMinutes,
                    completionStatus = SessionCompletionStatus.ONGOING
                )
                brickSessionLogDao.insertSessionLog(sessionLog)
            }

            // Update current state on Main thread
            currentBrickSession = session.copy(
                isCurrentlyBricked = true,
                currentSessionStartTime = currentTime,
                currentSessionEndTime = endTime
            )
            currentSessionLog = BrickSessionLog(
                id = logId,
                sessionId = sessionId,
                sessionStartTime = currentTime,
                scheduledDurationMinutes = session.durationMinutes,
                completionStatus = SessionCompletionStatus.ONGOING
            )

            // Don't launch brick screen immediately - let user stay on current screen
            // The AccessibilityService will handle blocking when needed

            // Start monitoring and enforcement services (non-blocking)
            startSessionMonitoring()
            startEnforcementServices()

            Log.i(TAG, "Started duration session: ${session.name} for ${session.durationMinutes} minutes")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error starting duration session", e)
            return false
        }
    }
    
    /**
     * Check if any scheduled sessions should be active now
     */
    private fun startScheduleMonitoring() {
        scheduleCheckJob?.cancel()
        scheduleCheckJob = scope.launch {
            while (isActive) {
                checkScheduledSessions()
                delay(60_000) // Check every minute
            }
        }
    }
    
    /**
     * Check for scheduled sessions that should be active now
     */
    private suspend fun checkScheduledSessions() {
        try {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK).toString() // 1=Sunday, 2=Monday, etc.
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            
            val sessions = phoneBrickSessionDao.getSessionsForDay(dayOfWeek)
            
            for (session in sessions) {
                if (session.sessionType == BrickSessionType.SLEEP_SCHEDULE && 
                    !session.isCurrentlyBricked &&
                    session.startHour != null && 
                    session.endHour != null) {
                    
                    if (isTimeInScheduleWindow(currentHour, currentMinute, session)) {
                        startScheduledSession(session)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking scheduled sessions", e)
        }
    }
    
    /**
     * Check if current time is within a scheduled session window
     */
    private fun isTimeInScheduleWindow(
        currentHour: Int, 
        currentMinute: Int, 
        session: PhoneBrickSession
    ): Boolean {
        val startHour = session.startHour!!
        val startMinute = session.startMinute!!
        val endHour = session.endHour!!
        val endMinute = session.endMinute!!
        
        val currentTotalMinutes = currentHour * 60 + currentMinute
        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute
        
        return if (endTotalMinutes > startTotalMinutes) {
            // Same day (e.g., 9 AM to 5 PM)
            currentTotalMinutes in startTotalMinutes..endTotalMinutes
        } else {
            // Crosses midnight (e.g., 11 PM to 6 AM)
            currentTotalMinutes >= startTotalMinutes || currentTotalMinutes <= endTotalMinutes
        }
    }
    
    /**
     * Start a scheduled session (like sleep schedule)
     */
    private suspend fun startScheduledSession(session: PhoneBrickSession) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Calculate end time based on schedule
            val calendar = Calendar.getInstance()
            if (session.endHour!! < session.startHour!!) {
                // Next day
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            calendar.set(Calendar.HOUR_OF_DAY, session.endHour!!)
            calendar.set(Calendar.MINUTE, session.endMinute!!)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val endTime = calendar.timeInMillis
            val durationMinutes = ((endTime - currentTime) / (60 * 1000)).toInt()
            
            // Start session in database
            phoneBrickSessionDao.startSession(session.id, currentTime, endTime)
            
            // Create session log
            val sessionLog = BrickSessionLog(
                sessionId = session.id,
                sessionStartTime = currentTime,
                scheduledDurationMinutes = durationMinutes,
                completionStatus = SessionCompletionStatus.ONGOING
            )
            val logId = brickSessionLogDao.insertSessionLog(sessionLog)
            
            // Update current state
            currentBrickSession = session.copy(
                isCurrentlyBricked = true,
                currentSessionStartTime = currentTime,
                currentSessionEndTime = endTime
            )
            currentSessionLog = sessionLog.copy(id = logId)
            
            // Launch brick screen
            launchBrickScreen()
            
            // Start monitoring
            startSessionMonitoring()
            
            Log.i(TAG, "Started scheduled session: ${session.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scheduled session", e)
        }
    }
    
    /**
     * Monitor current session and handle completion
     */
    private fun startSessionMonitoring() {
        currentSessionMonitorJob?.cancel()
        currentSessionMonitorJob = scope.launch {
            while (isActive && currentBrickSession?.isCurrentlyBricked == true) {
                delay(10_000) // Check every 10 seconds
                
                val session = currentBrickSession ?: break
                val endTime = session.currentSessionEndTime ?: break
                
                if (System.currentTimeMillis() >= endTime) {
                    completeCurrentSession()
                    break
                }
            }
        }
    }
    
    /**
     * Complete the current session normally
     */
    suspend fun completeCurrentSession() {
        val session = currentBrickSession ?: return
        val sessionLog = currentSessionLog ?: return

        try {
            val currentTime = System.currentTimeMillis()
            val actualDurationMinutes = if (sessionLog.sessionStartTime > 0) {
                ((currentTime - sessionLog.sessionStartTime) / (60 * 1000)).toInt()
            } else 0

            // Perform all DB operations on IO thread
            withContext(Dispatchers.IO) {
                // Update session in database
                phoneBrickSessionDao.completeSession(session.id, currentTime)

                // Complete session log
                brickSessionLogDao.completeSessionLog(
                    id = sessionLog.id,
                    endTime = currentTime,
                    actualDurationMinutes = actualDurationMinutes,
                    status = SessionCompletionStatus.COMPLETED
                )
            }

            // Clear current state
            currentBrickSession = null
            currentSessionLog = null
            currentSessionMonitorJob?.cancel()

            // Exit brick mode
            exitBrickMode()

            Log.i(TAG, "Session completed: ${session.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Error completing session", e)
        }
    }
    
    /**
     * Emergency override - break current session
     */
    suspend fun emergencyOverride(reason: String? = null): Boolean {
        val session = currentBrickSession ?: return false
        val sessionLog = currentSessionLog ?: return false

        if (!session.allowEmergencyOverride) {
            Log.w(TAG, "Emergency override not allowed for session: ${session.name}")
            return false
        }

        try {
            val currentTime = System.currentTimeMillis()
            val actualDurationMinutes = ((currentTime - sessionLog.sessionStartTime) / (60 * 1000)).toInt()

            // Perform all DB operations on IO thread
            withContext(Dispatchers.IO) {
                // Update session as broken
                phoneBrickSessionDao.emergencyBreakSession(session.id)

                // Log emergency override
                brickSessionLogDao.logEmergencyOverride(sessionLog.id, currentTime, reason)

                // Complete session log with emergency status
                brickSessionLogDao.completeSessionLog(
                    id = sessionLog.id,
                    endTime = currentTime,
                    actualDurationMinutes = actualDurationMinutes,
                    status = SessionCompletionStatus.EMERGENCY_OVERRIDE
                )
            }

            // Clear current state
            currentBrickSession = null
            currentSessionLog = null
            currentSessionMonitorJob?.cancel()

            // Exit brick mode
            exitBrickMode()

            Log.w(TAG, "Emergency override used for session: ${session.name}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error during emergency override", e)
            return false
        }
    }
    
    /**
     * Check if there's an ongoing session when manager starts
     */
    private fun checkForOngoingSession() {
        scope.launch {
            try {
                val ongoingSession = phoneBrickSessionDao.getCurrentlyActiveBrickSession()
                val ongoingLog = brickSessionLogDao.getCurrentOngoingSessionLog()
                
                if (ongoingSession != null && ongoingLog != null) {
                    currentBrickSession = ongoingSession
                    currentSessionLog = ongoingLog
                    
                    // Check if session should still be active
                    val endTime = ongoingSession.currentSessionEndTime ?: 0
                    if (System.currentTimeMillis() < endTime) {
                        // Session is still valid, resume brick mode
                        launchBrickScreen()
                        startSessionMonitoring()
                        Log.i(TAG, "Resumed ongoing session: ${ongoingSession.name}")
                    } else {
                        // Session expired, complete it
                        completeCurrentSession()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for ongoing session", e)
            }
        }
    }
    
    /**
     * Start enforcement services (BrickEnforcementService)
     */
    private fun startEnforcementServices() {
        try {
            // Check and request SYSTEM_ALERT_WINDOW permission if needed
            ensureOverlayPermission()

            // Start the BrickEnforcementService for notifications and monitoring
            com.example.wakt.services.BrickEnforcementService.start(context)
            Log.d(TAG, "Enforcement services started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting enforcement services", e)
        }
    }

    /**
     * Ensure SYSTEM_ALERT_WINDOW permission is granted for overlay
     */
    private fun ensureOverlayPermission() {
        // For Android 6+, SYSTEM_ALERT_WINDOW requires explicit runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                // Permission not granted - try to open the permission settings
                Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted - requesting...")
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open overlay permission settings", e)
                }
            }
        }
    }
    
    /**
     * Launch the brick overlay screen
     */
    private fun launchBrickScreen() {
        try {
            // Start enforcement service which manages the overlay
            BrickEnforcementService.start(context)
            Log.d(TAG, "Brick enforcement service started with overlay")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching brick screen", e)
        }
    }
    
    /**
     * Exit brick mode and return to normal home screen
     */
    private fun exitBrickMode() {
        try {
            // Stop enforcement service first
            BrickEnforcementService.stop(context)
            Log.d(TAG, "Brick enforcement service stopped")
            
            // Send user back to normal home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            Log.d(TAG, "Exited brick mode")
        } catch (e: Exception) {
            Log.e(TAG, "Error exiting brick mode", e)
        }
    }
    
    /**
     * Check if phone is currently bricked
     */
    fun isPhoneBricked(): Boolean {
        return currentBrickSession?.isCurrentlyBricked == true
    }
    
    /**
     * Get current session if active
     */
    fun getCurrentSession(): PhoneBrickSession? {
        return if (isPhoneBricked()) currentBrickSession else null
    }
    
    /**
     * Get remaining time for current session in minutes
     */
    fun getCurrentSessionRemainingMinutes(): Int? {
        val session = currentBrickSession ?: return null
        val endTime = session.currentSessionEndTime ?: return null
        
        val remainingMillis = endTime - System.currentTimeMillis()
        return if (remainingMillis > 0) {
            (remainingMillis / (60 * 1000)).toInt()
        } else 0
    }
    
    /**
     * Get remaining time for current session in seconds
     */
    fun getCurrentSessionRemainingSeconds(): Int? {
        val session = currentBrickSession ?: return null
        val endTime = session.currentSessionEndTime ?: return null
        
        val remainingMillis = endTime - System.currentTimeMillis()
        return if (remainingMillis > 0) {
            (remainingMillis / 1000).toInt()
        } else 0
    }
    
    /**
     * Log that user tried to bypass the system
     */
    suspend fun logBypassAttempt() {
        currentSessionLog?.let { log ->
            brickSessionLogDao.incrementBypassAttempts(log.id)
        }
    }
    
    /**
     * Log that user accessed an essential app
     */
    suspend fun logEssentialAppAccess(packageName: String) {
        currentSessionLog?.let { log ->
            brickSessionLogDao.logAppAccessed(log.id, packageName)
        }
    }
    
    fun destroy() {
        currentSessionMonitorJob?.cancel()
        scheduleCheckJob?.cancel()
        scope.cancel()
    }
}