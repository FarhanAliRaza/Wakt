package com.example.wakt.data.database.dao

import androidx.room.*
import com.example.wakt.data.database.entity.GoalBlock
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalBlockDao {
    
    @Query("SELECT * FROM goal_blocks WHERE isActive = 1")
    fun getAllActiveGoals(): Flow<List<GoalBlock>>
    
    @Query("SELECT * FROM goal_blocks")
    fun getAllGoals(): Flow<List<GoalBlock>>
    
    @Query("SELECT * FROM goal_blocks WHERE isActive = 1")
    suspend fun getAllActiveGoalsList(): List<GoalBlock>
    
    @Query("SELECT * FROM goal_blocks WHERE id = :id")
    suspend fun getGoalById(id: Long): GoalBlock?
    
    @Query("SELECT * FROM goal_blocks WHERE packageNameOrUrl = :packageNameOrUrl AND isActive = 1")
    suspend fun getActiveGoalByPackageOrUrl(packageNameOrUrl: String): GoalBlock?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalBlock): Long
    
    @Update
    suspend fun updateGoal(goal: GoalBlock)
    
    @Query("UPDATE goal_blocks SET isActive = 0, completedAt = :completedAt WHERE id = :id")
    suspend fun markGoalAsCompleted(id: Long, completedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE goal_blocks SET isActive = 0, completedAt = :currentTime WHERE goalEndTime <= :currentTime AND isActive = 1")
    suspend fun markExpiredGoalsAsCompleted(currentTime: Long = System.currentTimeMillis()): Int
    
    @Query("SELECT * FROM goal_blocks WHERE goalEndTime > :currentTime AND isActive = 1")
    suspend fun getActiveGoalsNotExpired(currentTime: Long = System.currentTimeMillis()): List<GoalBlock>
    
    @Query("SELECT COUNT(*) FROM goal_blocks WHERE packageNameOrUrl = :packageNameOrUrl AND isActive = 1")
    suspend fun hasActiveGoalForPackageOrUrl(packageNameOrUrl: String): Int
    
    // Goals cannot be deleted while active, only after completion
    @Query("DELETE FROM goal_blocks WHERE id = :id AND isActive = 0")
    suspend fun deleteCompletedGoal(id: Long): Int
    
    @Query("SELECT * FROM goal_blocks WHERE packageNameOrUrl = :packageNameOrUrl AND isActive = 1 AND goalEndTime > :currentTime")
    suspend fun getActiveGoalBlock(packageNameOrUrl: String, currentTime: Long = System.currentTimeMillis()): GoalBlock?
    
    // Check if a goal has items in the new goal_block_items table
    @Query("""
        SELECT gb.* FROM goal_blocks gb 
        INNER JOIN goal_block_items gbi ON gb.id = gbi.goalId 
        WHERE gbi.packageOrUrl = :packageOrUrl 
        AND gb.isActive = 1 
        AND gb.goalEndTime > :currentTime
        LIMIT 1
    """)
    suspend fun getActiveGoalByItem(packageOrUrl: String, currentTime: Long = System.currentTimeMillis()): GoalBlock?
}