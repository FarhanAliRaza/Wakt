package com.example.wakt.presentation.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.dao.PhoneBrickSessionDao
import com.example.wakt.data.database.entity.BrickSessionType
import com.example.wakt.data.database.entity.PhoneBrickSession
import com.example.wakt.data.database.entity.ScheduleTargetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ScheduleUiState(
    val selectedTabIndex: Int = 0, // 0 = Phone, 1 = Apps
    val phoneSchedules: List<PhoneBrickSession> = emptyList(),
    val appSchedules: List<PhoneBrickSession> = emptyList(),
    val nextScheduledLock: PhoneBrickSession? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val phoneBrickSessionDao: PhoneBrickSessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedules()
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            phoneBrickSessionDao.getAllActiveSessions().collect { sessions ->
                // Filter for SLEEP_SCHEDULE type (time-window based schedules)
                val schedules = sessions.filter { it.sessionType == BrickSessionType.SLEEP_SCHEDULE }

                val phoneSchedules = schedules.filter {
                    it.scheduleTargetType == ScheduleTargetType.PHONE
                }
                val appSchedules = schedules.filter {
                    it.scheduleTargetType == ScheduleTargetType.APPS
                }

                val nextLock = calculateNextScheduledLock(schedules)

                _uiState.update { state ->
                    state.copy(
                        phoneSchedules = phoneSchedules,
                        appSchedules = appSchedules,
                        nextScheduledLock = nextLock,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }

    fun toggleScheduleActive(scheduleId: Long, active: Boolean) {
        viewModelScope.launch {
            if (active) {
                phoneBrickSessionDao.activateSession(scheduleId)
            } else {
                phoneBrickSessionDao.deactivateSession(scheduleId)
            }
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            phoneBrickSessionDao.deleteSessionById(scheduleId)
        }
    }

    private fun calculateNextScheduledLock(schedules: List<PhoneBrickSession>): PhoneBrickSession? {
        if (schedules.isEmpty()) return null

        val now = Calendar.getInstance()
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        // Convert to our format: 1 = Monday, 7 = Sunday
        val todayIndex = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        var nextSchedule: PhoneBrickSession? = null
        var minMinutesUntilStart = Int.MAX_VALUE

        for (schedule in schedules) {
            if (!schedule.isActive) continue
            val startHour = schedule.startHour ?: continue
            val startMinute = schedule.startMinute ?: continue

            // Check if schedule runs today
            if (schedule.activeDaysOfWeek.contains(todayIndex.toString())) {
                val scheduleMinutesFromMidnight = startHour * 60 + startMinute
                val currentMinutesFromMidnight = currentHour * 60 + currentMinute

                if (scheduleMinutesFromMidnight > currentMinutesFromMidnight) {
                    val minutesUntilStart = scheduleMinutesFromMidnight - currentMinutesFromMidnight
                    if (minutesUntilStart < minMinutesUntilStart) {
                        minMinutesUntilStart = minutesUntilStart
                        nextSchedule = schedule
                    }
                }
            }

            // Check tomorrow and following days
            for (daysAhead in 1..7) {
                val checkDay = ((todayIndex - 1 + daysAhead) % 7) + 1
                if (schedule.activeDaysOfWeek.contains(checkDay.toString())) {
                    val minutesUntilStart = daysAhead * 24 * 60 +
                        (startHour * 60 + startMinute) -
                        (currentHour * 60 + currentMinute)
                    if (minutesUntilStart > 0 && minutesUntilStart < minMinutesUntilStart) {
                        minMinutesUntilStart = minutesUntilStart
                        nextSchedule = schedule
                    }
                    break
                }
            }
        }

        return nextSchedule
    }
}
