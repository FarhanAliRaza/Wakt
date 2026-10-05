package com.farhanaliraza.wakt.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhanaliraza.wakt.data.database.dao.DailyGoalDao
import com.farhanaliraza.wakt.data.database.entity.DailyGoal
import com.farhanaliraza.wakt.data.database.entity.GoalCheckIn
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.GoalStreaks
import com.farhanaliraza.wakt.utils.GoalWallpaperUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalRow(
    val goal: DailyGoal,
    val currentStreak: Int,
    val bestStreak: Int,
    val todayState: Boolean?,
    val totalSuccessDays: Int
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val dailyGoalDao: DailyGoalDao,
    private val wallpaperUpdater: GoalWallpaperUpdater,
    private val globalSettingsManager: GlobalSettingsManager
) : ViewModel() {

    val rows: StateFlow<List<GoalRow>> =
        combine(dailyGoalDao.getActiveGoals(), dailyGoalDao.getAllCheckIns()) { goals, checkIns ->
            val today = GoalStreaks.today()
            val byGoal = checkIns.groupBy { it.goalId }
            goals.map { goal ->
                val map = byGoal[goal.id]?.associate { it.day to it.success } ?: emptyMap()
                GoalRow(
                    goal = goal,
                    currentStreak = GoalStreaks.currentStreak(map, today),
                    bestStreak = GoalStreaks.bestStreak(map),
                    todayState = map[today],
                    totalSuccessDays = map.count { it.value }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallpaperEnabled: StateFlow<Boolean> = globalSettingsManager.goalWallpaperEnabled
    val wallpaperTarget: StateFlow<String> = globalSettingsManager.goalWallpaperTarget

    fun addGoal(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dailyGoalDao.insertGoal(DailyGoal(title = trimmed))
            wallpaperUpdater.refresh()
        }
    }

    fun deleteGoal(goal: DailyGoal) {
        viewModelScope.launch {
            dailyGoalDao.deleteCheckInsForGoal(goal.id)
            dailyGoalDao.deleteGoal(goal.id)
            wallpaperUpdater.refresh()
        }
    }

    fun checkIn(goal: DailyGoal, success: Boolean) {
        viewModelScope.launch {
            dailyGoalDao.upsertCheckIn(GoalCheckIn(goalId = goal.id, day = GoalStreaks.today(), success = success))
            wallpaperUpdater.refresh()
        }
    }

    fun clearToday(goal: DailyGoal) {
        viewModelScope.launch {
            dailyGoalDao.deleteCheckIn(goal.id, GoalStreaks.today())
            wallpaperUpdater.refresh()
        }
    }

    fun setWallpaperEnabled(enabled: Boolean) {
        globalSettingsManager.setGoalWallpaperEnabled(enabled)
        if (enabled) {
            wallpaperUpdater.refresh()
            wallpaperUpdater.scheduleMidnightRefresh()
        }
    }

    fun setWallpaperTarget(target: String) {
        globalSettingsManager.setGoalWallpaperTarget(target)
        wallpaperUpdater.refresh()
    }
}
