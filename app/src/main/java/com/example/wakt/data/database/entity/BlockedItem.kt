package com.example.wakt.data.database.entity

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
    val blockEndTime: Long? = null // Calculated end time, null for permanent blocks
)

enum class BlockType {
    APP,
    WEBSITE
}

enum class ChallengeType {
    WAIT,
    QUESTION,
    CLICK_500 // Requires 500 clicks to unlock
}