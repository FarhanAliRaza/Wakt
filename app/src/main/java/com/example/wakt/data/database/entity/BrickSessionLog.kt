package com.example.wakt.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brick_session_logs")
data class BrickSessionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long, // Reference to PhoneBrickSession
    val sessionStartTime: Long,
    val sessionEndTime: Long? = null, // Null if session is ongoing
    val scheduledDurationMinutes: Int,
    val actualDurationMinutes: Int? = null, // Calculated when session ends
    val completionStatus: SessionCompletionStatus,
    val emergencyOverrideUsed: Boolean = false,
    val emergencyOverrideTime: Long? = null,
    val emergencyOverrideReason: String? = null,
    val bypassAttempts: Int = 0, // Number of times user tried to bypass
    val appsAccessedDuringSession: String = "", // Comma-separated package names
    val createdAt: Long = System.currentTimeMillis()
)

enum class SessionCompletionStatus {
    COMPLETED, // Session finished normally
    EMERGENCY_OVERRIDE, // User used emergency break
    CANCELLED, // User cancelled before completion (shouldn't happen with proper enforcement)
    INTERRUPTED, // System issue (device restart, etc.)
    ONGOING // Session is currently active
}