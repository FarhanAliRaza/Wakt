package com.farhanaliraza.wakt.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A promise the user checks in on once a day ("No reels", "No porn", "Bed by 11").
 * Streaks are derived from [GoalCheckIn] rows, nothing is stored redundantly.
 */
@Entity(tableName = "daily_goals")
data class DailyGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val showOnWallpaper: Boolean = true,
    val sortOrder: Int = 0
)

/** One answer per goal per calendar day. [day] is yyyy-MM-dd in the device time zone. */
@Entity(tableName = "goal_check_ins", primaryKeys = ["goalId", "day"])
data class GoalCheckIn(
    val goalId: Long,
    val day: String,
    val success: Boolean,
    val checkedAt: Long = System.currentTimeMillis()
)
