package com.farhanaliraza.wakt.data.repository

import com.farhanaliraza.wakt.data.database.dao.GoalBlockDao
import com.farhanaliraza.wakt.data.database.dao.GoalBlockItemDao
import com.farhanaliraza.wakt.data.database.entity.GoalBlock
import com.farhanaliraza.wakt.data.database.entity.GoalBlockItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalBlockRepository @Inject constructor(
    private val goalBlockDao: GoalBlockDao,
    private val goalBlockItemDao: GoalBlockItemDao
) {
    fun getAllActiveGoals(): Flow<List<GoalBlock>> = goalBlockDao.getAllActiveGoals()
    
    fun getAllGoals(): Flow<List<GoalBlock>> = goalBlockDao.getAllGoals()
    
    suspend fun createGoal(goal: GoalBlock): Long {
        // Check if there's already an active goal for this package/URL
        val existingGoal = goalBlockDao.getActiveGoalByPackageOrUrl(goal.packageNameOrUrl)
        if (existingGoal != null) {
            throw IllegalStateException("An active goal already exists for ${goal.name}")
        }
        
        // Calculate end time
        val endTime = goal.goalStartTime + (goal.goalDurationDays * 24 * 60 * 60 * 1000L)
        val goalWithEndTime = goal.copy(goalEndTime = endTime)
        
        return goalBlockDao.insertGoal(goalWithEndTime)
    }
    
    suspend fun getActiveGoalByPackageOrUrl(packageNameOrUrl: String): GoalBlock? {
        return goalBlockDao.getActiveGoalByPackageOrUrl(packageNameOrUrl)
    }
    
    suspend fun hasActiveGoal(packageNameOrUrl: String): Boolean {
        return goalBlockDao.hasActiveGoalForPackageOrUrl(packageNameOrUrl) > 0
    }
    
    suspend fun checkAndCompleteExpiredGoals() {
        goalBlockDao.markExpiredGoalsAsCompleted()
    }
    
    suspend fun tryDeleteGoal(goalId: Long): Boolean {
        // Can only delete completed goals
        val goal = goalBlockDao.getGoalById(goalId) ?: return false
        
        if (goal.isActive) {
            val now = System.currentTimeMillis()
            if (now < goal.goalEndTime) {
                // Goal is still active and not expired, cannot delete
                return false
            } else {
                // Goal has expired, mark as completed first
                goalBlockDao.markGoalAsCompleted(goalId, now)
            }
        }
        
        // Now try to delete (will only work if goal is not active)
        return goalBlockDao.deleteCompletedGoal(goalId) > 0
    }
    
    suspend fun getRemainingDays(goalId: Long): Int {
        val goal = goalBlockDao.getGoalById(goalId) ?: return 0
        if (!goal.isActive) return 0
        
        val now = System.currentTimeMillis()
        val remainingMillis = goal.goalEndTime - now
        if (remainingMillis <= 0) return 0
        
        return (remainingMillis / (24 * 60 * 60 * 1000)).toInt() + 1 // Round up
    }
    
    suspend fun isGoalActive(packageNameOrUrl: String): Boolean {
        // Check both old single-item goals and new multi-item goals
        val oldGoal = goalBlockDao.getActiveGoalBlock(packageNameOrUrl)
        val newGoal = goalBlockItemDao.getActiveGoalItemForPackageOrUrl(packageNameOrUrl)
        return oldGoal != null || newGoal != null
    }
    
    // Goal Item Management Methods
    fun getItemsForGoal(goalId: Long): Flow<List<GoalBlockItem>> {
        return goalBlockItemDao.getItemsForGoal(goalId)
    }
    
    suspend fun addItemToGoal(goalId: Long, itemName: String, itemType: com.farhanaliraza.wakt.data.database.entity.BlockType, packageOrUrl: String): Long {
        // Check if goal exists and is active
        val goal = goalBlockDao.getGoalById(goalId) ?: throw IllegalStateException("Goal not found")
        if (!goal.isActive || goal.goalEndTime <= System.currentTimeMillis()) {
            throw IllegalStateException("Cannot add items to inactive or expired goals")
        }
        
        // Check if item already exists in this goal
        val existingItems = goalBlockItemDao.getItemsForGoalList(goalId)
        if (existingItems.any { it.packageOrUrl == packageOrUrl }) {
            throw IllegalStateException("This item is already in the goal")
        }
        
        val item = GoalBlockItem(
            goalId = goalId,
            itemName = itemName,
            itemType = itemType,
            packageOrUrl = packageOrUrl
        )
        return goalBlockItemDao.insertItem(item)
    }
    
    suspend fun removeItemFromGoal(goalId: Long, itemId: Long): Boolean {
        // Long-term goals cannot have items removed - this violates the commitment principle
        throw IllegalStateException("Items cannot be removed from long-term goals. Goals are designed to be immutable commitments that can only be made stricter by adding more items.")
    }
    
    suspend fun getItemCountForGoal(goalId: Long): Int {
        return goalBlockItemDao.getItemCountForGoal(goalId)
    }
    
    suspend fun createGoalWithItems(
        goal: GoalBlock,
        items: List<Pair<String, com.farhanaliraza.wakt.data.database.entity.BlockType>>
    ): Long {
        if (items.isEmpty()) {
            throw IllegalStateException("Goal must have at least one item")
        }
        
        // Calculate end time
        val endTime = goal.goalStartTime + (goal.goalDurationDays * 24 * 60 * 60 * 1000L)
        val goalWithEndTime = goal.copy(goalEndTime = endTime, packageNameOrUrl = "")
        
        val goalId = goalBlockDao.insertGoal(goalWithEndTime)
        
        // Add all items to the goal
        val goalItems = items.map { (packageOrUrl, type) ->
            val itemName = if (type == com.farhanaliraza.wakt.data.database.entity.BlockType.WEBSITE) {
                packageOrUrl
            } else {
                packageOrUrl // For apps, we could resolve the app name, but for now use package name
            }
            
            GoalBlockItem(
                goalId = goalId,
                itemName = itemName,
                itemType = type,
                packageOrUrl = packageOrUrl
            )
        }
        
        goalBlockItemDao.insertItems(goalItems)
        return goalId
    }
}