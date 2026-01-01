package com.farhanaliraza.wakt.data.database.dao

import androidx.room.*
import com.farhanaliraza.wakt.data.database.entity.BrickSessionLog
import com.farhanaliraza.wakt.data.database.entity.SessionCompletionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BrickSessionLogDao {
    
    @Query("SELECT * FROM brick_session_logs ORDER BY sessionStartTime DESC LIMIT 50")
    fun getRecentSessionLogs(): Flow<List<BrickSessionLog>>
    
    @Query("SELECT * FROM brick_session_logs WHERE sessionId = :sessionId ORDER BY sessionStartTime DESC")
    suspend fun getSessionLogsBySessionId(sessionId: Long): List<BrickSessionLog>
    
    @Query("SELECT * FROM brick_session_logs WHERE completionStatus = 'ONGOING' LIMIT 1")
    suspend fun getCurrentOngoingSessionLog(): BrickSessionLog?
    
    @Query("SELECT * FROM brick_session_logs WHERE id = :id")
    suspend fun getSessionLogById(id: Long): BrickSessionLog?
    
    // Analytics queries
    @Query("""
        SELECT COUNT(*) FROM brick_session_logs 
        WHERE completionStatus = :status 
        AND sessionStartTime >= :fromTime
    """)
    suspend fun getSessionCountByStatus(status: SessionCompletionStatus, fromTime: Long): Int
    
    @Query("""
        SELECT AVG(actualDurationMinutes) FROM brick_session_logs 
        WHERE completionStatus = 'COMPLETED' 
        AND actualDurationMinutes IS NOT NULL
        AND sessionStartTime >= :fromTime
    """)
    suspend fun getAverageCompletedSessionDuration(fromTime: Long): Double?
    
    @Query("""
        SELECT SUM(actualDurationMinutes) FROM brick_session_logs 
        WHERE completionStatus = 'COMPLETED' 
        AND actualDurationMinutes IS NOT NULL
        AND sessionStartTime >= :fromTime
    """)
    suspend fun getTotalFocusTimeMinutes(fromTime: Long): Int?
    
    @Query("""
        SELECT COUNT(*) FROM brick_session_logs 
        WHERE emergencyOverrideUsed = 1 
        AND sessionStartTime >= :fromTime
    """)
    suspend fun getEmergencyOverrideCount(fromTime: Long): Int
    
    @Insert
    suspend fun insertSessionLog(log: BrickSessionLog): Long
    
    @Update
    suspend fun updateSessionLog(log: BrickSessionLog)
    
    @Delete
    suspend fun deleteSessionLog(log: BrickSessionLog)
    
    // Complete an ongoing session
    @Query("""
        UPDATE brick_session_logs 
        SET sessionEndTime = :endTime,
            actualDurationMinutes = :actualDurationMinutes,
            completionStatus = :status
        WHERE id = :id
    """)
    suspend fun completeSessionLog(
        id: Long, 
        endTime: Long, 
        actualDurationMinutes: Int, 
        status: SessionCompletionStatus
    )
    
    // Log emergency override
    @Query("""
        UPDATE brick_session_logs 
        SET emergencyOverrideUsed = 1,
            emergencyOverrideTime = :overrideTime,
            emergencyOverrideReason = :reason,
            completionStatus = 'EMERGENCY_OVERRIDE'
        WHERE id = :id
    """)
    suspend fun logEmergencyOverride(id: Long, overrideTime: Long, reason: String?)
    
    // Increment bypass attempts
    @Query("UPDATE brick_session_logs SET bypassAttempts = bypassAttempts + 1 WHERE id = :id")
    suspend fun incrementBypassAttempts(id: Long)
    
    // Add accessed app to session log
    @Query("""
        UPDATE brick_session_logs 
        SET appsAccessedDuringSession = 
            CASE 
                WHEN appsAccessedDuringSession = '' THEN :packageName
                WHEN appsAccessedDuringSession NOT LIKE '%' || :packageName || '%' 
                    THEN appsAccessedDuringSession || ',' || :packageName
                ELSE appsAccessedDuringSession
            END
        WHERE id = :id
    """)
    suspend fun logAppAccessed(id: Long, packageName: String)
    
    @Query("DELETE FROM brick_session_logs WHERE sessionStartTime < :cutoffTime")
    suspend fun deleteOldLogs(cutoffTime: Long)
}