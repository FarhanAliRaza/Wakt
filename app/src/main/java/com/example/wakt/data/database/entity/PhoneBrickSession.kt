package com.example.wakt.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phone_brick_sessions")
data class PhoneBrickSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sessionType: BrickSessionType,
    val durationMinutes: Int? = null, // For duration-based sessions
    val startHour: Int? = null, // For time-window sessions (0-23)
    val startMinute: Int? = null, // For time-window sessions (0-59)
    val endHour: Int? = null, // For time-window sessions (0-23)
    val endMinute: Int? = null, // For time-window sessions (0-59)
    val activeDaysOfWeek: String = "1234567", // "1234567" = Mon-Sun, "23456" = Tue-Sat
    val isActive: Boolean = true,
    val isCurrentlyBricked: Boolean = false,
    val currentSessionStartTime: Long? = null, // When current session started
    val currentSessionEndTime: Long? = null, // When current session should end
    val totalSessionsCompleted: Int = 0,
    val totalSessionsBroken: Int = 0, // Emergency overrides
    val lastCompletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val challengeType: ChallengeType = ChallengeType.WAIT,
    val challengeData: String = "5", // Minutes to wait for emergency override
    val allowEmergencyOverride: Boolean = true, // Always enabled for safety
    val allowedApps: String = "", // Session-specific allowed apps (comma-separated package names)
    // Schedule-specific fields
    val scheduleTargetType: ScheduleTargetType = ScheduleTargetType.PHONE,
    val targetPackages: String = "", // For APP type schedules (comma-separated)
    val reminderEnabled: Boolean = false, // Show notification before lock starts
    val reminderMinutesBefore: Int = 15, // Minutes before lock to remind
    val vibrate: Boolean = true, // Vibrate on lock start
    val canceledUntil: Long? = null, // Timestamp until which auto-start is blocked (after emergency cancel)
    // Lock feature - prevents editing/deleting for a set duration
    val isLocked: Boolean = false, // Whether session is currently locked from editing
    val lockExpiresAt: Long? = null, // Timestamp when lock expires
    val lockCommitmentPhrase: String? = null // Phrase user must type to unlock early
)

enum class BrickSessionType {
    FOCUS_SESSION, // Duration-based (10 min - 4 hours)
    SLEEP_SCHEDULE, // Time-window based (e.g., 11 PM - 6 AM daily)
    DIGITAL_DETOX // Extended duration-based (4-24 hours)
}

enum class ScheduleTargetType {
    PHONE, // Lock entire phone
    APPS   // Block specific apps
}