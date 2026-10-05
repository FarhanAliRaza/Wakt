package com.farhanaliraza.wakt.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Pure streak arithmetic over yyyy-MM-dd day keys in the device time zone. */
object GoalStreaks {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun dayKey(timeMillis: Long = System.currentTimeMillis()): String =
        dayFormat.format(Calendar.getInstance().apply { this.timeInMillis = timeMillis }.time)

    fun today(): String = dayKey()

    fun previousDay(day: String): String {
        val cal = Calendar.getInstance()
        cal.time = dayFormat.parse(day) ?: return day
        cal.add(Calendar.DAY_OF_MONTH, -1)
        return dayFormat.format(cal.time)
    }

    /**
     * Consecutive successful days ending today (if today is already a success)
     * or yesterday (if today is still unanswered). A failed day ends the streak.
     */
    fun currentStreak(checkIns: Map<String, Boolean>, today: String = today()): Int {
        var day = when (checkIns[today]) {
            true -> today
            false -> return 0
            null -> previousDay(today)
        }
        var streak = 0
        while (checkIns[day] == true) {
            streak++
            day = previousDay(day)
        }
        return streak
    }

    fun bestStreak(checkIns: Map<String, Boolean>): Int {
        if (checkIns.isEmpty()) return 0
        var best = 0
        for ((day, success) in checkIns) {
            if (!success) continue
            // Count only from the start of a run (the previous day is not a success)
            if (checkIns[previousDay(day)] == true) continue
            var run = 0
            var d = day
            while (checkIns[d] == true) {
                run++
                d = nextDay(d)
            }
            if (run > best) best = run
        }
        return best
    }

    private fun nextDay(day: String): String {
        val cal = Calendar.getInstance()
        cal.time = dayFormat.parse(day) ?: return day
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return dayFormat.format(cal.time)
    }

    /** Millis until one minute past the next local midnight. */
    fun millisUntilNextMidnight(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - now).coerceAtLeast(60_000L)
    }
}
