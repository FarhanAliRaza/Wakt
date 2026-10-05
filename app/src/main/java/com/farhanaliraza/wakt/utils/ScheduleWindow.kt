package com.farhanaliraza.wakt.utils

import com.farhanaliraza.wakt.data.database.entity.PhoneBrickSession
import java.util.Calendar

/** Time-window helpers for schedules, shared by every enforcement path. */
object ScheduleWindow {

    /** Whether the current wall-clock time falls inside the schedule's window today. */
    fun isNowInWindow(schedule: PhoneBrickSession): Boolean {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val calendarDay = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat

        // Schedules store days as 1=Mon ... 7=Sun
        val dayOfWeek = if (calendarDay == 1) 7 else calendarDay - 1
        if (!schedule.activeDaysOfWeek.contains(dayOfWeek.toString())) return false

        val startHour = schedule.startHour ?: return false
        val startMinute = schedule.startMinute ?: return false
        val endHour = schedule.endHour ?: return false
        val endMinute = schedule.endMinute ?: return false

        val currentMinutes = currentHour * 60 + currentMinute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes >= startMinutes && currentMinutes < endMinutes
        } else {
            // Overnight schedule (e.g. 22:00 - 06:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    /** Epoch millis at which the schedule's current window ends, or 0 if it has no end time. */
    fun endTimeMillis(schedule: PhoneBrickSession): Long {
        val endHour = schedule.endHour ?: return 0L
        val endMinute = schedule.endMinute ?: return 0L
        val now = Calendar.getInstance()
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return end.timeInMillis
    }
}
