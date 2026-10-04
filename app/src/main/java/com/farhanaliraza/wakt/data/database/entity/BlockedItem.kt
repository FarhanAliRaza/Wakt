package com.farhanaliraza.wakt.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_items")
data class BlockedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: BlockType,
    val packageNameOrUrl: String,
    val challengeType: ChallengeType,
    val challengeData: String, // JSON for flexibility (wait minutes or question/answer)
    val blockDurationDays: Int = 0, // 0 means permanent block
    val blockStartTime: Long = System.currentTimeMillis(), // When block was created
    val blockEndTime: Long? = null, // Calculated end time, null for permanent blocks

    // Commitment lock: while lockExpiresAt is in the future the item cannot be
    // deleted and its challenge is disabled. Early unlock needs the commitment
    // phrase (null/blank = no early unlock at all) after a cooling-off period
    // that starts at unlockRequestedAt.
    val lockExpiresAt: Long? = null,
    val lockCommitmentPhrase: String? = null,
    val unlockRequestedAt: Long? = null
) {
    /** Lock is in force right now. */
    fun isCommitmentLocked(now: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = lockExpiresAt ?: return false
        return expiresAt > now
    }

    /** Early unlock is possible at all (a phrase was set when locking). */
    fun allowsEarlyUnlock(): Boolean = !lockCommitmentPhrase.isNullOrBlank()

    companion object {
        const val UNLOCK_COOLING_OFF_MS = 24L * 60 * 60 * 1000
    }

    /** Millis still to wait before the phrase may be typed, 0 when the wait is over, null if not requested. */
    fun unlockWaitRemainingMs(now: Long = System.currentTimeMillis()): Long? {
        val requestedAt = unlockRequestedAt ?: return null
        return (requestedAt + UNLOCK_COOLING_OFF_MS - now).coerceAtLeast(0L)
    }
}

enum class BlockType {
    APP,
    WEBSITE
}

enum class ChallengeType {
    WAIT,
    QUESTION,
    CLICK_500 // Requires 500 clicks to unlock
}
