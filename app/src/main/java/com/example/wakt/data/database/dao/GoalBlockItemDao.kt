package com.example.wakt.data.database.dao

import androidx.room.*
import com.example.wakt.data.database.entity.GoalBlockItem
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalBlockItemDao {
    
    @Query("SELECT * FROM goal_block_items WHERE goalId = :goalId ORDER BY addedAt ASC")
    fun getItemsForGoal(goalId: Long): Flow<List<GoalBlockItem>>
    
    @Query("SELECT * FROM goal_block_items WHERE goalId = :goalId ORDER BY addedAt ASC")
    suspend fun getItemsForGoalList(goalId: Long): List<GoalBlockItem>
    
    @Query("SELECT * FROM goal_block_items WHERE id = :id")
    suspend fun getItemById(id: Long): GoalBlockItem?
    
    @Query("SELECT * FROM goal_block_items WHERE packageOrUrl = :packageOrUrl")
    suspend fun getItemByPackageOrUrl(packageOrUrl: String): List<GoalBlockItem>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: GoalBlockItem): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<GoalBlockItem>)
    
    @Delete
    suspend fun deleteItem(item: GoalBlockItem)
    
    @Query("DELETE FROM goal_block_items WHERE id = :id")
    suspend fun deleteItemById(id: Long): Int
    
    @Query("DELETE FROM goal_block_items WHERE goalId = :goalId")
    suspend fun deleteAllItemsForGoal(goalId: Long): Int
    
    @Query("SELECT COUNT(*) FROM goal_block_items WHERE goalId = :goalId")
    suspend fun getItemCountForGoal(goalId: Long): Int
    
    // Check if a specific package/URL is blocked by any active goal
    @Query("""
        SELECT gbi.* FROM goal_block_items gbi 
        INNER JOIN goal_blocks gb ON gbi.goalId = gb.id 
        WHERE gbi.packageOrUrl = :packageOrUrl 
        AND gb.isActive = 1 
        AND gb.goalEndTime > :currentTime
    """)
    suspend fun getActiveGoalItemForPackageOrUrl(
        packageOrUrl: String, 
        currentTime: Long = System.currentTimeMillis()
    ): GoalBlockItem?
    
    // Get all items from active goals
    @Query("""
        SELECT gbi.* FROM goal_block_items gbi 
        INNER JOIN goal_blocks gb ON gbi.goalId = gb.id 
        WHERE gb.isActive = 1 
        AND gb.goalEndTime > :currentTime
    """)
    suspend fun getAllActiveGoalItems(currentTime: Long = System.currentTimeMillis()): List<GoalBlockItem>
}