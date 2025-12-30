package com.example.wakt.data.database.dao

import androidx.room.*
import com.example.wakt.data.database.entity.PhoneBrickSession
import com.example.wakt.data.database.entity.BrickSessionType
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneBrickSessionDao {
    
    @Query("SELECT * FROM phone_brick_sessions WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllActiveSessions(): Flow<List<PhoneBrickSession>>

    @Query("SELECT * FROM phone_brick_sessions WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getAllActiveSessionsList(): List<PhoneBrickSession>

    // Get ALL sessions (including inactive) - for schedule list UI where user can toggle on/off
    @Query("SELECT * FROM phone_brick_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<PhoneBrickSession>>
    
    @Query("SELECT * FROM phone_brick_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): PhoneBrickSession?
    
    @Query("SELECT * FROM phone_brick_sessions WHERE isCurrentlyBricked = 1 LIMIT 1")
    suspend fun getCurrentlyActiveBrickSession(): PhoneBrickSession?
    
    @Query("SELECT * FROM phone_brick_sessions WHERE sessionType = :type AND isActive = 1")
    suspend fun getSessionsByType(type: BrickSessionType): List<PhoneBrickSession>
    
    @Query("SELECT * FROM phone_brick_sessions WHERE sessionType = 'SLEEP_SCHEDULE' AND isActive = 1")
    suspend fun getActiveSleepSchedules(): List<PhoneBrickSession>
    
    @Insert
    suspend fun insertSession(session: PhoneBrickSession): Long
    
    @Update
    suspend fun updateSession(session: PhoneBrickSession)
    
    @Delete
    suspend fun deleteSession(session: PhoneBrickSession)
    
    @Query("DELETE FROM phone_brick_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)
    
    // Start a session (mark as currently bricked)
    @Query("""
        UPDATE phone_brick_sessions 
        SET isCurrentlyBricked = 1, 
            currentSessionStartTime = :startTime, 
            currentSessionEndTime = :endTime 
        WHERE id = :id
    """)
    suspend fun startSession(id: Long, startTime: Long, endTime: Long)
    
    // End current session
    @Query("""
        UPDATE phone_brick_sessions 
        SET isCurrentlyBricked = 0, 
            currentSessionStartTime = NULL, 
            currentSessionEndTime = NULL,
            totalSessionsCompleted = totalSessionsCompleted + 1,
            lastCompletedAt = :completedAt
        WHERE id = :id
    """)
    suspend fun completeSession(id: Long, completedAt: Long)
    
    // Emergency override - mark session as broken
    @Query("""
        UPDATE phone_brick_sessions 
        SET isCurrentlyBricked = 0, 
            currentSessionStartTime = NULL, 
            currentSessionEndTime = NULL,
            totalSessionsBroken = totalSessionsBroken + 1
        WHERE id = :id
    """)
    suspend fun emergencyBreakSession(id: Long)
    
    // Check if any session should be active right now based on schedule
    @Query("""
        SELECT * FROM phone_brick_sessions 
        WHERE isActive = 1 
        AND (
            (sessionType = 'SLEEP_SCHEDULE' AND activeDaysOfWeek LIKE '%' || :dayOfWeek || '%')
            OR sessionType != 'SLEEP_SCHEDULE'
        )
    """)
    suspend fun getSessionsForDay(dayOfWeek: String): List<PhoneBrickSession>
    
    @Query("UPDATE phone_brick_sessions SET isActive = 0 WHERE id = :id")
    suspend fun deactivateSession(id: Long)
    
    @Query("UPDATE phone_brick_sessions SET isActive = 1 WHERE id = :id")
    suspend fun activateSession(id: Long)

    @Query("UPDATE phone_brick_sessions SET canceledUntil = :canceledUntil WHERE id = :id")
    suspend fun setCanceledUntil(id: Long, canceledUntil: Long?)

    // Get active app schedules that include a specific package
    @Query("""
        SELECT * FROM phone_brick_sessions
        WHERE isActive = 1
        AND sessionType = 'SLEEP_SCHEDULE'
        AND scheduleTargetType = 'APPS'
        AND targetPackages LIKE '%' || :packageName || '%'
    """)
    suspend fun getActiveAppSchedulesForPackage(packageName: String): List<PhoneBrickSession>

    // Lock a session to prevent editing/deleting
    @Query("""
        UPDATE phone_brick_sessions
        SET isLocked = 1,
            lockExpiresAt = :expiresAt,
            lockCommitmentPhrase = :phrase
        WHERE id = :sessionId
    """)
    suspend fun lockSession(sessionId: Long, expiresAt: Long, phrase: String)

    // Unlock a session (early unlock after typing phrase)
    @Query("""
        UPDATE phone_brick_sessions
        SET isLocked = 0,
            lockExpiresAt = NULL,
            lockCommitmentPhrase = NULL
        WHERE id = :sessionId
    """)
    suspend fun unlockSession(sessionId: Long)

    // Auto-expire old locks on app startup
    @Query("""
        UPDATE phone_brick_sessions
        SET isLocked = 0,
            lockExpiresAt = NULL,
            lockCommitmentPhrase = NULL
        WHERE isLocked = 1 AND lockExpiresAt IS NOT NULL AND lockExpiresAt < :currentTime
    """)
    suspend fun expireOldLocks(currentTime: Long)
}