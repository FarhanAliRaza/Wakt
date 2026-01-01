package com.farhanaliraza.wakt.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporaryUnlock @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "wakt_temporary_unlock_prefs"
        private const val TAG = "TemporaryUnlock"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    data class UnlockState(
        val identifier: String, // packageName or website URL
        val unlockTimeMillis: Long,
        val durationMinutes: Int,
        val isActive: Boolean = true
    )
    
    fun createTemporaryUnlock(identifier: String, durationMinutes: Int) {
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "Creating temporary unlock for $identifier: duration=${durationMinutes}min")
        
        prefs.edit()
            .putString("${identifier}_identifier", identifier)
            .putLong("${identifier}_unlock_time", currentTime)
            .putInt("${identifier}_duration", durationMinutes)
            .putBoolean("${identifier}_active", true)
            .apply()
    }
    
    fun getUnlockState(identifier: String): UnlockState? {
        val savedIdentifier = prefs.getString("${identifier}_identifier", null)
        if (savedIdentifier != identifier) return null
        
        val unlockTime = prefs.getLong("${identifier}_unlock_time", 0L)
        val duration = prefs.getInt("${identifier}_duration", 0)
        val isActive = prefs.getBoolean("${identifier}_active", false)
        
        return if (unlockTime > 0 && duration > 0 && isActive) {
            Log.d(TAG, "Found unlock state for $identifier: unlockTime=$unlockTime, duration=$duration, active=$isActive")
            UnlockState(identifier, unlockTime, duration, isActive)
        } else {
            Log.d(TAG, "No active unlock state found for $identifier")
            null
        }
    }
    
    fun isTemporarilyUnlocked(identifier: String): Boolean {
        val unlockState = getUnlockState(identifier) ?: return false
        
        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - unlockState.unlockTimeMillis
        val unlockDurationMillis = unlockState.durationMinutes * 60 * 1000L
        
        return if (elapsedMillis < unlockDurationMillis) {
            val remainingMinutes = ((unlockDurationMillis - elapsedMillis) / (60 * 1000)).toInt()
            Log.d(TAG, "Item $identifier is temporarily unlocked for ${remainingMinutes}min more")
            true
        } else {
            Log.d(TAG, "Temporary unlock expired for $identifier")
            clearUnlockState(identifier)
            false
        }
    }
    
    fun getRemainingUnlockMinutes(identifier: String): Int? {
        val unlockState = getUnlockState(identifier) ?: return null
        
        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - unlockState.unlockTimeMillis
        val unlockDurationMillis = unlockState.durationMinutes * 60 * 1000L
        val remainingMillis = unlockDurationMillis - elapsedMillis
        
        return if (remainingMillis > 0) {
            val remainingMinutes = (remainingMillis / (60 * 1000)).toInt()
            Log.d(TAG, "Remaining unlock time for $identifier: ${remainingMinutes}min")
            remainingMinutes
        } else {
            Log.d(TAG, "Unlock expired for $identifier")
            clearUnlockState(identifier)
            0
        }
    }
    
    fun clearUnlockState(identifier: String) {
        Log.d(TAG, "Clearing unlock state for $identifier")
        prefs.edit()
            .remove("${identifier}_identifier")
            .remove("${identifier}_unlock_time")
            .remove("${identifier}_duration")
            .remove("${identifier}_active")
            .apply()
    }
    
    fun clearAllUnlocks() {
        Log.d(TAG, "Clearing all temporary unlocks")
        prefs.edit().clear().apply()
    }
    
    fun extendUnlock(identifier: String, additionalMinutes: Int) {
        val unlockState = getUnlockState(identifier)
        if (unlockState != null) {
            val newDuration = unlockState.durationMinutes + additionalMinutes
            Log.d(TAG, "Extending unlock for $identifier by ${additionalMinutes}min (total: ${newDuration}min)")
            
            prefs.edit()
                .putInt("${identifier}_duration", newDuration)
                .apply()
        }
    }
    
    fun getAllActiveUnlocks(): List<UnlockState> {
        val allEntries = prefs.all
        val unlocks = mutableListOf<UnlockState>()
        
        // Group entries by identifier
        val identifierMap = mutableMapOf<String, MutableMap<String, Any>>()
        
        for ((key, value) in allEntries) {
            if (key.endsWith("_identifier") && value is String) {
                val identifier = value
                identifierMap[identifier] = identifierMap[identifier] ?: mutableMapOf()
                identifierMap[identifier]?.set("identifier", identifier)
            }
        }
        
        // For each identifier, get its unlock state if active
        for ((identifier, _) in identifierMap) {
            val unlockState = getUnlockState(identifier)
            if (unlockState != null && isTemporarilyUnlocked(identifier)) {
                unlocks.add(unlockState)
            }
        }
        
        return unlocks
    }
}