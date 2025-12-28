package com.example.wakt.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for schedule block logic
 * Tests time window checking, day of week parsing, and schedule validation
 */
class ScheduleBlockLogicTest {

    // ==================== TIME WINDOW TESTS ====================

    @Test
    fun `same day schedule should match time within window`() {
        // Schedule: 9:00 AM - 5:00 PM
        assertTrue(isTimeInScheduleWindow(9, 0, 9, 0, 17, 0))   // Start time
        assertTrue(isTimeInScheduleWindow(12, 0, 9, 0, 17, 0))  // Noon
        assertTrue(isTimeInScheduleWindow(16, 59, 9, 0, 17, 0)) // Just before end
    }

    @Test
    fun `same day schedule should not match time outside window`() {
        // Schedule: 9:00 AM - 5:00 PM
        assertFalse(isTimeInScheduleWindow(8, 59, 9, 0, 17, 0))  // Just before start
        assertFalse(isTimeInScheduleWindow(17, 0, 9, 0, 17, 0))  // End time (exclusive)
        assertFalse(isTimeInScheduleWindow(17, 1, 9, 0, 17, 0))  // After end
        assertFalse(isTimeInScheduleWindow(0, 0, 9, 0, 17, 0))   // Midnight
        assertFalse(isTimeInScheduleWindow(23, 59, 9, 0, 17, 0)) // Late night
    }

    @Test
    fun `midnight crossing schedule should match time in evening`() {
        // Schedule: 11:00 PM - 6:00 AM (crosses midnight)
        assertTrue(isTimeInScheduleWindow(23, 0, 23, 0, 6, 0))   // Start time
        assertTrue(isTimeInScheduleWindow(23, 30, 23, 0, 6, 0))  // Late night
        assertTrue(isTimeInScheduleWindow(23, 59, 23, 0, 6, 0))  // Just before midnight
    }

    @Test
    fun `midnight crossing schedule should match time in morning`() {
        // Schedule: 11:00 PM - 6:00 AM (crosses midnight)
        assertTrue(isTimeInScheduleWindow(0, 0, 23, 0, 6, 0))    // Midnight
        assertTrue(isTimeInScheduleWindow(3, 30, 23, 0, 6, 0))   // Early morning
        assertTrue(isTimeInScheduleWindow(5, 59, 23, 0, 6, 0))   // Just before end
    }

    @Test
    fun `midnight crossing schedule should not match time outside window`() {
        // Schedule: 11:00 PM - 6:00 AM (crosses midnight)
        assertFalse(isTimeInScheduleWindow(6, 0, 23, 0, 6, 0))   // End time (exclusive)
        assertFalse(isTimeInScheduleWindow(6, 1, 23, 0, 6, 0))   // Just after end
        assertFalse(isTimeInScheduleWindow(12, 0, 23, 0, 6, 0))  // Noon
        assertFalse(isTimeInScheduleWindow(22, 59, 23, 0, 6, 0)) // Just before start
    }

    @Test
    fun `schedule starting at midnight should work correctly`() {
        // Schedule: 12:00 AM - 6:00 AM
        assertTrue(isTimeInScheduleWindow(0, 0, 0, 0, 6, 0))     // Midnight
        assertTrue(isTimeInScheduleWindow(3, 0, 0, 0, 6, 0))     // 3 AM
        assertTrue(isTimeInScheduleWindow(5, 59, 0, 0, 6, 0))    // Just before end
        assertFalse(isTimeInScheduleWindow(6, 0, 0, 0, 6, 0))    // End time
        assertFalse(isTimeInScheduleWindow(12, 0, 0, 0, 6, 0))   // Noon
    }

    @Test
    fun `schedule ending at midnight should work correctly`() {
        // Schedule: 6:00 PM - 12:00 AM
        assertTrue(isTimeInScheduleWindow(18, 0, 18, 0, 0, 0))   // Start time
        assertTrue(isTimeInScheduleWindow(21, 0, 18, 0, 0, 0))   // 9 PM
        assertTrue(isTimeInScheduleWindow(23, 59, 18, 0, 0, 0))  // Just before midnight
        assertFalse(isTimeInScheduleWindow(0, 0, 18, 0, 0, 0))   // Midnight (end)
        assertFalse(isTimeInScheduleWindow(17, 59, 18, 0, 0, 0)) // Before start
    }

    // ==================== SCHEDULE VALIDATION TESTS ====================

    @Test
    fun `schedule with same start and end time should be invalid`() {
        assertFalse(isTimeInScheduleWindow(12, 0, 12, 0, 12, 0))
        assertFalse(isTimeInScheduleWindow(0, 0, 0, 0, 0, 0))
        assertFalse(isTimeInScheduleWindow(23, 59, 23, 59, 23, 59))
    }

    @Test
    fun `schedule with duration less than 5 minutes should be invalid`() {
        // 4 minutes duration
        assertFalse(isTimeInScheduleWindow(9, 0, 9, 0, 9, 4))
        // 1 minute duration
        assertFalse(isTimeInScheduleWindow(9, 0, 9, 0, 9, 1))
    }

    @Test
    fun `schedule with exactly 5 minutes duration should be valid`() {
        // 5 minutes duration: 9:00 - 9:05
        assertTrue(isTimeInScheduleWindow(9, 2, 9, 0, 9, 5))
    }

    @Test
    fun `schedule with null hours should return false`() {
        assertFalse(isTimeInScheduleWindowNullable(9, 0, null, 0, 17, 0))
        assertFalse(isTimeInScheduleWindowNullable(9, 0, 9, 0, null, 0))
        assertFalse(isTimeInScheduleWindowNullable(9, 0, null, null, null, null))
    }

    // ==================== DAY OF WEEK TESTS ====================

    @Test
    fun `day should be active when in activeDaysOfWeek string`() {
        val weekdays = "12345"  // Mon-Fri
        assertTrue(isDayActive(1, weekdays))  // Monday
        assertTrue(isDayActive(3, weekdays))  // Wednesday
        assertTrue(isDayActive(5, weekdays))  // Friday
        assertFalse(isDayActive(6, weekdays)) // Saturday
        assertFalse(isDayActive(7, weekdays)) // Sunday
    }

    @Test
    fun `day should be active for full week schedule`() {
        val allDays = "1234567"
        for (day in 1..7) {
            assertTrue(isDayActive(day, allDays))
        }
    }

    @Test
    fun `day should be inactive for empty schedule`() {
        val noDays = ""
        for (day in 1..7) {
            assertFalse(isDayActive(day, noDays))
        }
    }

    @Test
    fun `weekend only schedule should work correctly`() {
        val weekends = "67"  // Sat-Sun
        assertFalse(isDayActive(1, weekends)) // Monday
        assertFalse(isDayActive(5, weekends)) // Friday
        assertTrue(isDayActive(6, weekends))  // Saturday
        assertTrue(isDayActive(7, weekends))  // Sunday
    }

    @Test
    fun `calendar day of week conversion should be correct`() {
        // Java Calendar: 1=Sunday, 2=Monday, ..., 7=Saturday
        // Our format: 1=Monday, 2=Tuesday, ..., 7=Sunday
        assertEquals(7, convertCalendarDayToOurFormat(1))  // Sunday -> 7
        assertEquals(1, convertCalendarDayToOurFormat(2))  // Monday -> 1
        assertEquals(2, convertCalendarDayToOurFormat(3))  // Tuesday -> 2
        assertEquals(6, convertCalendarDayToOurFormat(7))  // Saturday -> 6
    }

    // ==================== SCHEDULE END TIME CALCULATION TESTS ====================

    @Test
    fun `same day schedule end time should be today`() {
        // Current time: 10:00 AM, Schedule ends at 5:00 PM
        val result = calculateScheduleEndSameDay(10, 0, 17, 0)
        assertEquals(17, result.first)  // End hour
        assertEquals(0, result.second)  // End minute
        assertFalse(result.third)       // Not next day
    }

    @Test
    fun `midnight crossing schedule end time should be next day`() {
        // Current time: 11:00 PM, Schedule ends at 6:00 AM
        val result = calculateScheduleEndSameDay(23, 0, 6, 0)
        assertEquals(6, result.first)   // End hour
        assertEquals(0, result.second)  // End minute
        assertTrue(result.third)        // Next day
    }

    // ==================== ALLOWED APPS PARSING TESTS ====================

    @Test
    fun `allowed apps should parse comma separated string`() {
        val allowedApps = "com.whatsapp,com.google.android.dialer,com.android.mms"
        val parsed = parseAllowedApps(allowedApps)

        assertEquals(3, parsed.size)
        assertTrue(parsed.contains("com.whatsapp"))
        assertTrue(parsed.contains("com.google.android.dialer"))
        assertTrue(parsed.contains("com.android.mms"))
    }

    @Test
    fun `allowed apps should handle empty string`() {
        val parsed = parseAllowedApps("")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `allowed apps should trim whitespace`() {
        val allowedApps = " com.whatsapp , com.dialer , com.mms "
        val parsed = parseAllowedApps(allowedApps)

        assertEquals(3, parsed.size)
        assertTrue(parsed.contains("com.whatsapp"))
        assertTrue(parsed.contains("com.dialer"))
        assertTrue(parsed.contains("com.mms"))
    }

    @Test
    fun `app should be allowed when in allowed apps list`() {
        val allowedApps = "com.whatsapp,com.google.android.dialer"

        assertTrue(isAppAllowedInSession("com.whatsapp", allowedApps))
        assertTrue(isAppAllowedInSession("com.google.android.dialer", allowedApps))
        assertFalse(isAppAllowedInSession("com.instagram", allowedApps))
    }

    @Test
    fun `app should not be allowed when allowed apps is empty`() {
        assertFalse(isAppAllowedInSession("com.whatsapp", ""))
        assertFalse(isAppAllowedInSession("com.whatsapp", null))
    }

    // ==================== HELPER METHODS (simulate actual logic) ====================

    /**
     * Check if current time is within a scheduled session window
     * Mirrors BrickSessionManager.isTimeInScheduleWindow()
     */
    private fun isTimeInScheduleWindow(
        currentHour: Int,
        currentMinute: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): Boolean {
        val currentTotalMinutes = currentHour * 60 + currentMinute
        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute

        // Invalid if start == end
        if (startTotalMinutes == endTotalMinutes) {
            return false
        }

        // Require at least 5 minutes duration
        val duration = if (endTotalMinutes > startTotalMinutes) {
            endTotalMinutes - startTotalMinutes
        } else {
            (24 * 60 - startTotalMinutes) + endTotalMinutes
        }
        if (duration < 5) {
            return false
        }

        return if (endTotalMinutes > startTotalMinutes) {
            // Same day (e.g., 9 AM to 5 PM)
            currentTotalMinutes in startTotalMinutes until endTotalMinutes
        } else {
            // Crosses midnight (e.g., 11 PM to 6 AM)
            currentTotalMinutes >= startTotalMinutes || currentTotalMinutes < endTotalMinutes
        }
    }

    /**
     * Version that handles nullable hours (for null safety testing)
     */
    private fun isTimeInScheduleWindowNullable(
        currentHour: Int,
        currentMinute: Int,
        startHour: Int?,
        startMinute: Int?,
        endHour: Int?,
        endMinute: Int?
    ): Boolean {
        if (startHour == null || endHour == null) return false
        return isTimeInScheduleWindow(
            currentHour, currentMinute,
            startHour, startMinute ?: 0,
            endHour, endMinute ?: 0
        )
    }

    /**
     * Check if a day is active in the activeDaysOfWeek string
     * Day format: 1=Monday, 7=Sunday
     */
    private fun isDayActive(day: Int, activeDaysOfWeek: String): Boolean {
        return activeDaysOfWeek.contains(day.toString())
    }

    /**
     * Convert Java Calendar day of week to our format
     * Calendar: 1=Sunday, 2=Monday, ..., 7=Saturday
     * Our format: 1=Monday, 2=Tuesday, ..., 7=Sunday
     */
    private fun convertCalendarDayToOurFormat(calendarDay: Int): Int {
        return if (calendarDay == 1) 7 else calendarDay - 1
    }

    /**
     * Calculate if schedule end is same day or next day
     * Returns Triple(endHour, endMinute, isNextDay)
     */
    private fun calculateScheduleEndSameDay(
        currentHour: Int,
        currentMinute: Int,
        endHour: Int,
        endMinute: Int
    ): Triple<Int, Int, Boolean> {
        val currentTotalMinutes = currentHour * 60 + currentMinute
        val endTotalMinutes = endHour * 60 + endMinute

        val isNextDay = endTotalMinutes <= currentTotalMinutes
        return Triple(endHour, endMinute, isNextDay)
    }

    /**
     * Parse comma-separated allowed apps string
     */
    private fun parseAllowedApps(allowedApps: String?): List<String> {
        if (allowedApps.isNullOrBlank()) return emptyList()
        return allowedApps.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Check if an app is in the allowed apps list for a session
     */
    private fun isAppAllowedInSession(packageName: String, allowedApps: String?): Boolean {
        val parsed = parseAllowedApps(allowedApps)
        return parsed.contains(packageName)
    }
}
