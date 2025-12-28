package com.example.wakt.presentation.screens.schedule

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakt.data.database.dao.PhoneBrickSessionDao
import com.example.wakt.data.database.entity.BrickSessionType
import com.example.wakt.data.database.entity.PhoneBrickSession
import com.example.wakt.data.database.entity.ScheduleTargetType
import com.example.wakt.presentation.components.daysSetToString
import com.example.wakt.presentation.components.stringToDaysSet
import com.example.wakt.utils.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScheduleDetailUiState(
    val scheduleId: Long? = null,
    val isEditing: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 6,
    val endMinute: Int = 0,
    val repeatDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // Every day
    val reminderEnabled: Boolean = true,
    val vibrate: Boolean = true,
    val scheduleTargetType: ScheduleTargetType = ScheduleTargetType.PHONE,
    val targetPackages: String = "",
    // App selection
    val availableApps: List<ScheduleAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val appSearchQuery: String = "",
    val isLoadingApps: Boolean = false,
    // Status
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    private val phoneBrickSessionDao: PhoneBrickSessionDao,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleDetailUiState())
    val uiState: StateFlow<ScheduleDetailUiState> = _uiState.asStateFlow()

    private var originalSchedule: PhoneBrickSession? = null

    init {
        val scheduleId = savedStateHandle.get<Long>("scheduleId")
        if (scheduleId != null && scheduleId > 0) {
            loadSchedule(scheduleId)
        }
    }

    fun setIsAppSchedule(isApp: Boolean) {
        _uiState.update {
            it.copy(
                scheduleTargetType = if (isApp) ScheduleTargetType.APPS else ScheduleTargetType.PHONE
            )
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }

            val apps = withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)

                resolveInfos.mapNotNull { resolveInfo ->
                    try {
                        val appInfo = resolveInfo.activityInfo.applicationInfo
                        val packageName = appInfo.packageName

                        // Skip system packages and this app
                        if (packageName.contains("com.example.wakt")) return@mapNotNull null
                        if (packageName.startsWith("com.android.systemui")) return@mapNotNull null
                        if (packageName.startsWith("com.google.android.gms")) return@mapNotNull null

                        ScheduleAppInfo(
                            name = packageManager.getApplicationLabel(appInfo).toString(),
                            packageName = packageName
                            // Note: icon loading removed for performance - not displayed in UI
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
            }

            _uiState.update { state ->
                // If editing, parse existing target packages
                val existingPackages = if (state.targetPackages.isNotBlank()) {
                    state.targetPackages.split(",").toSet()
                } else {
                    emptySet()
                }

                state.copy(
                    availableApps = apps,
                    selectedPackages = existingPackages,
                    isLoadingApps = false
                )
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val updatedSelection = state.selectedPackages.toMutableSet()
            if (updatedSelection.contains(packageName)) {
                updatedSelection.remove(packageName)
            } else {
                updatedSelection.add(packageName)
            }
            state.copy(selectedPackages = updatedSelection)
        }
    }

    fun updateAppSearchQuery(query: String) {
        _uiState.update { it.copy(appSearchQuery = query) }
    }

    fun loadSchedule(id: Long) {
        viewModelScope.launch {
            val schedule = phoneBrickSessionDao.getSessionById(id)
            if (schedule != null) {
                originalSchedule = schedule
                _uiState.update { state ->
                    state.copy(
                        scheduleId = schedule.id,
                        isEditing = true,
                        startHour = schedule.startHour ?: 22,
                        startMinute = schedule.startMinute ?: 0,
                        endHour = schedule.endHour ?: 6,
                        endMinute = schedule.endMinute ?: 0,
                        repeatDays = stringToDaysSet(schedule.activeDaysOfWeek),
                        reminderEnabled = schedule.reminderEnabled,
                        vibrate = schedule.vibrate,
                        scheduleTargetType = schedule.scheduleTargetType,
                        targetPackages = schedule.targetPackages
                    )
                }
            }
        }
    }

    fun updateStartHour(hour: Int) {
        _uiState.update { it.copy(startHour = hour) }
    }

    fun updateStartMinute(minute: Int) {
        _uiState.update { it.copy(startMinute = minute) }
    }

    fun updateEndHour(hour: Int) {
        _uiState.update { it.copy(endHour = hour) }
    }

    fun updateEndMinute(minute: Int) {
        _uiState.update { it.copy(endMinute = minute) }
    }

    fun updateRepeatDays(days: Set<Int>) {
        _uiState.update { it.copy(repeatDays = days) }
    }

    fun updateReminderEnabled(enabled: Boolean) {
        _uiState.update { it.copy(reminderEnabled = enabled) }
    }

    fun updateVibrate(vibrate: Boolean) {
        _uiState.update { it.copy(vibrate = vibrate) }
    }

    fun updateScheduleTargetType(type: ScheduleTargetType) {
        _uiState.update { it.copy(scheduleTargetType = type) }
    }

    fun saveSchedule() {
        viewModelScope.launch {
            val state = _uiState.value

            // Check permissions first
            if (!PermissionHelper.areAllPermissionsGranted(context)) {
                val missing = PermissionHelper.getMissingPermissions(context)
                _uiState.update { it.copy(error = "Missing permissions: ${missing.joinToString(", ")}. Please grant all permissions before creating schedules.") }
                return@launch
            }

            if (state.repeatDays.isEmpty()) {
                _uiState.update { it.copy(error = "Please select at least one day") }
                return@launch
            }

            if (state.scheduleTargetType == ScheduleTargetType.APPS && state.selectedPackages.isEmpty()) {
                _uiState.update { it.copy(error = "Please select at least one app to block") }
                return@launch
            }

            // Validate schedule duration (minimum 5 minutes)
            val startMinutes = state.startHour * 60 + state.startMinute
            val endMinutes = state.endHour * 60 + state.endMinute
            val durationMinutes = if (endMinutes > startMinutes) {
                endMinutes - startMinutes
            } else {
                // Overnight schedule (e.g., 22:00 - 06:00)
                (24 * 60 - startMinutes) + endMinutes
            }

            if (durationMinutes < 5) {
                _uiState.update { it.copy(error = "Schedule must be at least 5 minutes long") }
                return@launch
            }

            if (state.startHour == state.endHour && state.startMinute == state.endMinute) {
                _uiState.update { it.copy(error = "Start and end time cannot be the same") }
                return@launch
            }

            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val targetPackagesStr = if (state.scheduleTargetType == ScheduleTargetType.APPS) {
                    state.selectedPackages.joinToString(",")
                } else {
                    ""
                }

                val session = PhoneBrickSession(
                    id = state.scheduleId ?: 0,
                    name = generateScheduleName(state),
                    sessionType = BrickSessionType.SLEEP_SCHEDULE,
                    startHour = state.startHour,
                    startMinute = state.startMinute,
                    endHour = state.endHour,
                    endMinute = state.endMinute,
                    activeDaysOfWeek = daysSetToString(state.repeatDays),
                    isActive = true,
                    scheduleTargetType = state.scheduleTargetType,
                    targetPackages = targetPackagesStr,
                    reminderEnabled = state.reminderEnabled,
                    vibrate = state.vibrate,
                    createdAt = originalSchedule?.createdAt ?: System.currentTimeMillis()
                )

                if (state.isEditing && state.scheduleId != null) {
                    phoneBrickSessionDao.updateSession(session)
                } else {
                    phoneBrickSessionDao.insertSession(session)
                }

                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }

    fun deleteSchedule() {
        viewModelScope.launch {
            val scheduleId = _uiState.value.scheduleId ?: return@launch

            _uiState.update { it.copy(isDeleting = true, error = null) }

            try {
                phoneBrickSessionDao.deleteSessionById(scheduleId)
                _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeleting = false, error = "Failed to delete: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun generateScheduleName(state: ScheduleDetailUiState): String {
        val timeStr = String.format("%02d:%02d - %02d:%02d",
            state.startHour, state.startMinute,
            state.endHour, state.endMinute
        )
        val prefix = if (state.scheduleTargetType == ScheduleTargetType.APPS) "Apps" else "Phone"
        return when {
            state.repeatDays == setOf(1, 2, 3, 4, 5, 6, 7) -> "$prefix Daily $timeStr"
            state.repeatDays == setOf(1, 2, 3, 4, 5) -> "$prefix Weekdays $timeStr"
            state.repeatDays == setOf(6, 7) -> "$prefix Weekend $timeStr"
            else -> "$prefix Schedule $timeStr"
        }
    }
}
