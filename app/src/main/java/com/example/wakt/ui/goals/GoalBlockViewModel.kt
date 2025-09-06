package com.example.wakt.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.data.database.entity.GoalBlock
import com.example.wakt.data.repository.GoalBlockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalBlockViewModel @Inject constructor(
    private val goalBlockRepository: GoalBlockRepository
) : ViewModel() {
    
    val activeGoals: Flow<List<GoalBlock>> = goalBlockRepository.getAllActiveGoals()
    val allGoals: Flow<List<GoalBlock>> = goalBlockRepository.getAllGoals()
    
    init {
        // Check for expired goals on startup
        viewModelScope.launch {
            goalBlockRepository.checkAndCompleteExpiredGoals()
        }
    }
    
    suspend fun createGoal(
        name: String,
        packageOrUrl: String,
        type: BlockType,
        durationDays: Int,
        challengeType: ChallengeType
    ): Boolean {
        return try {
            val challengeData = when (challengeType) {
                ChallengeType.WAIT -> """{"waitMinutes": 20}"""
                ChallengeType.CLICK_500 -> """{"clicks": 500}"""
                else -> """{}"""
            }
            
            val goal = GoalBlock(
                name = name,
                type = type,
                packageNameOrUrl = packageOrUrl,
                goalDurationDays = durationDays,
                goalStartTime = System.currentTimeMillis(),
                goalEndTime = 0L, // Will be calculated in repository
                challengeType = challengeType,
                challengeData = challengeData
            )
            
            goalBlockRepository.createGoal(goal)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun tryDeleteGoal(goalId: Long): Boolean {
        return goalBlockRepository.tryDeleteGoal(goalId)
    }
    
    suspend fun getRemainingDays(goalId: Long): Int {
        return goalBlockRepository.getRemainingDays(goalId)
    }
    
    suspend fun createGoalWithMultipleItems(
        name: String,
        items: List<Pair<String, BlockType>>,
        durationDays: Int,
        challengeType: ChallengeType
    ): Boolean {
        return try {
            val challengeData = when (challengeType) {
                ChallengeType.WAIT -> """{"waitMinutes": 20}"""
                ChallengeType.CLICK_500 -> """{"clicks": 500}"""
                else -> """{}"""
            }
            
            val goal = GoalBlock(
                name = name,
                type = BlockType.APP, // This field is now deprecated for multi-item goals
                packageNameOrUrl = "", // This field is now deprecated for multi-item goals
                goalDurationDays = durationDays,
                goalStartTime = System.currentTimeMillis(),
                goalEndTime = 0L, // Will be calculated in repository
                challengeType = challengeType,
                challengeData = challengeData
            )
            
            goalBlockRepository.createGoalWithItems(goal, items)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}