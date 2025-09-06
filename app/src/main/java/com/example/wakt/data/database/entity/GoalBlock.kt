package com.example.wakt.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal_blocks")
data class GoalBlock(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: BlockType,
    val packageNameOrUrl: String,
    val goalDurationDays: Int, // 1 to 90 days
    val goalStartTime: Long = System.currentTimeMillis(),
    val goalEndTime: Long, // Calculated from start + duration
    val challengeType: ChallengeType,
    val challengeData: String, // JSON for flexibility
    val isActive: Boolean = true,
    val completedAt: Long? = null, // When goal was completed (reached end time)
    val createdAt: Long = System.currentTimeMillis()
)