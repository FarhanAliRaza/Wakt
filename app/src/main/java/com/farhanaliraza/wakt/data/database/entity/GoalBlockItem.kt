package com.farhanaliraza.wakt.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_block_items",
    foreignKeys = [
        ForeignKey(
            entity = GoalBlock::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GoalBlockItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalId: Long,
    val itemName: String, // Display name (app name or website domain)
    val itemType: BlockType, // APP or WEBSITE
    val packageOrUrl: String, // Package name for apps, URL for websites
    val addedAt: Long = System.currentTimeMillis()
)