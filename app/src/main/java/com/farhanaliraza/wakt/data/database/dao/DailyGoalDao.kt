package com.farhanaliraza.wakt.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.farhanaliraza.wakt.data.database.entity.DailyGoal
import com.farhanaliraza.wakt.data.database.entity.GoalCheckIn
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyGoalDao {

    @Query("SELECT * FROM daily_goals WHERE isActive = 1 ORDER BY sortOrder, createdAt")
    fun getActiveGoals(): Flow<List<DailyGoal>>

    @Query("SELECT * FROM daily_goals WHERE isActive = 1 ORDER BY sortOrder, createdAt")
    suspend fun getActiveGoalsList(): List<DailyGoal>

    @Query("SELECT * FROM daily_goals WHERE id = :id")
    suspend fun getGoalById(id: Long): DailyGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: DailyGoal): Long

    @Update
    suspend fun updateGoal(goal: DailyGoal)

    @Query("DELETE FROM daily_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM goal_check_ins")
    fun getAllCheckIns(): Flow<List<GoalCheckIn>>

    @Query("SELECT * FROM goal_check_ins")
    suspend fun getAllCheckInsList(): List<GoalCheckIn>

    @Query("SELECT * FROM goal_check_ins WHERE goalId = :goalId AND day = :day")
    suspend fun getCheckIn(goalId: Long, day: String): GoalCheckIn?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckIn(checkIn: GoalCheckIn)

    @Query("DELETE FROM goal_check_ins WHERE goalId = :goalId AND day = :day")
    suspend fun deleteCheckIn(goalId: Long, day: String)

    @Query("DELETE FROM goal_check_ins WHERE goalId = :goalId")
    suspend fun deleteCheckInsForGoal(goalId: Long)
}
