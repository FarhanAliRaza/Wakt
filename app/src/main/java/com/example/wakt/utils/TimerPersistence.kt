package com.example.wakt.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerPersistence @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "wakt_timer_prefs"
        private const val TAG = "TimerPersistence"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    data class TimerState(
        val packageName: String,
        val startTimeMillis: Long,
        val totalDurationMinutes: Int,
        val isActive: Boolean = true
    )
    
    fun saveTimerState(packageName: String, startTimeMillis: Long, totalDurationMinutes: Int) {
        Log.d(TAG, "Saving timer state for $packageName: start=$startTimeMillis, duration=$totalDurationMinutes")
        prefs.edit()
            .putString("${packageName}_package", packageName)
            .putLong("${packageName}_start", startTimeMillis)
            .putInt("${packageName}_duration", totalDurationMinutes)
            .putBoolean("${packageName}_active", true)
            .apply()
    }
    
    fun getTimerState(packageName: String): TimerState? {
        val savedPackageName = prefs.getString("${packageName}_package", null)
        if (savedPackageName != packageName) return null
        
        val startTime = prefs.getLong("${packageName}_start", 0L)
        val duration = prefs.getInt("${packageName}_duration", 0)
        val isActive = prefs.getBoolean("${packageName}_active", false)
        
        return if (startTime > 0 && duration > 0 && isActive) {
            Log.d(TAG, "Found timer state for $packageName: start=$startTime, duration=$duration, active=$isActive")
            TimerState(packageName, startTime, duration, isActive)
        } else {
            Log.d(TAG, "No active timer state found for $packageName")
            null
        }
    }
    
    fun getRemainingTimeSeconds(packageName: String): Int? {
        val timerState = getTimerState(packageName) ?: return null
        
        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - timerState.startTimeMillis
        val totalDurationMillis = timerState.totalDurationMinutes * 60 * 1000L
        val remainingMillis = totalDurationMillis - elapsedMillis
        
        return if (remainingMillis > 0) {
            val remainingSeconds = (remainingMillis / 1000).toInt()
            Log.d(TAG, "Remaining time for $packageName: ${remainingSeconds}s")
            remainingSeconds
        } else {
            Log.d(TAG, "Timer expired for $packageName")
            clearTimerState(packageName)
            0
        }
    }
    
    fun clearTimerState(packageName: String) {
        Log.d(TAG, "Clearing timer state for $packageName")
        prefs.edit()
            .remove("${packageName}_package")
            .remove("${packageName}_start")
            .remove("${packageName}_duration")
            .remove("${packageName}_active")
            .apply()
    }
    
    fun isTimerRunning(packageName: String): Boolean {
        val remainingTime = getRemainingTimeSeconds(packageName)
        return remainingTime != null && remainingTime > 0
    }
    
    fun hasUserRequestedAccess(packageName: String): Boolean {
        return getTimerState(packageName) != null
    }
}