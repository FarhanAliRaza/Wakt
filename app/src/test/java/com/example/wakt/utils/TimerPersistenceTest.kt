package com.example.wakt.utils

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for TimerPersistence utility class
 */
@RunWith(MockitoJUnitRunner::class)
class TimerPersistenceTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var timerPersistence: TimerPersistence

    @Before
    fun setup() {
        `when`(mockContext.getSharedPreferences("wakt_timer_prefs", Context.MODE_PRIVATE))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(any(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(any(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(any(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.remove(any())).thenReturn(mockEditor)

        timerPersistence = TimerPersistence(mockContext)
    }

    @Test
    fun `saveTimerState should store timer information`() {
        val packageName = "com.example.test"
        val startTime = System.currentTimeMillis()
        val durationMinutes = 15

        timerPersistence.saveTimerState(packageName, startTime, durationMinutes)

        verify(mockEditor).putString("${packageName}_package", packageName)
        verify(mockEditor).putLong("${packageName}_start", startTime)
        verify(mockEditor).putInt("${packageName}_duration", durationMinutes)
        verify(mockEditor).putBoolean("${packageName}_active", true)
        verify(mockEditor).apply()
    }

    @Test
    fun `getTimerState should return null when no timer exists`() {
        val packageName = "com.example.nonexistent"

        `when`(mockSharedPreferences.getString("${packageName}_package", null)).thenReturn(null)

        val result = timerPersistence.getTimerState(packageName)

        assertNull(result)
    }

    @Test
    fun `getTimerState should return valid timer state when exists`() {
        val packageName = "com.example.test"
        val startTime = System.currentTimeMillis()
        val duration = 15

        `when`(mockSharedPreferences.getString("${packageName}_package", null)).thenReturn(packageName)
        `when`(mockSharedPreferences.getLong("${packageName}_start", 0L)).thenReturn(startTime)
        `when`(mockSharedPreferences.getInt("${packageName}_duration", 0)).thenReturn(duration)
        `when`(mockSharedPreferences.getBoolean("${packageName}_active", false)).thenReturn(true)

        val result = timerPersistence.getTimerState(packageName)

        assertNotNull(result)
        assertEquals(packageName, result!!.packageName)
        assertEquals(startTime, result.startTimeMillis)
        assertEquals(duration, result.totalDurationMinutes)
        assertTrue(result.isActive)
    }

    @Test
    fun `isTimerRunning should return true when timer is active and not expired`() {
        val packageName = "com.example.test"
        val startTime = System.currentTimeMillis() - (5 * 60 * 1000) // 5 minutes ago
        val duration = 15 // 15 minute timer

        `when`(mockSharedPreferences.getString("${packageName}_package", null)).thenReturn(packageName)
        `when`(mockSharedPreferences.getLong("${packageName}_start", 0L)).thenReturn(startTime)
        `when`(mockSharedPreferences.getInt("${packageName}_duration", 0)).thenReturn(duration)
        `when`(mockSharedPreferences.getBoolean("${packageName}_active", false)).thenReturn(true)

        val result = timerPersistence.isTimerRunning(packageName)

        assertTrue(result)
    }

    @Test
    fun `isTimerRunning should return false when timer has expired`() {
        val packageName = "com.example.test"
        val startTime = System.currentTimeMillis() - (20 * 60 * 1000) // 20 minutes ago
        val duration = 15 // 15 minute timer (expired)

        `when`(mockSharedPreferences.getString("${packageName}_package", null)).thenReturn(packageName)
        `when`(mockSharedPreferences.getLong("${packageName}_start", 0L)).thenReturn(startTime)
        `when`(mockSharedPreferences.getInt("${packageName}_duration", 0)).thenReturn(duration)
        `when`(mockSharedPreferences.getBoolean("${packageName}_active", false)).thenReturn(true)

        val result = timerPersistence.isTimerRunning(packageName)

        assertFalse(result)
    }

    @Test
    fun `getRemainingTimeSeconds should return correct remaining time`() {
        val packageName = "com.example.test"
        val startTime = System.currentTimeMillis() - (5 * 60 * 1000) // 5 minutes ago
        val duration = 15 // 15 minute timer, so 10 minutes remaining

        `when`(mockSharedPreferences.getString("${packageName}_package", null)).thenReturn(packageName)
        `when`(mockSharedPreferences.getLong("${packageName}_start", 0L)).thenReturn(startTime)
        `when`(mockSharedPreferences.getInt("${packageName}_duration", 0)).thenReturn(duration)
        `when`(mockSharedPreferences.getBoolean("${packageName}_active", false)).thenReturn(true)

        val result = timerPersistence.getRemainingTimeSeconds(packageName)

        // Should be approximately 600 seconds (10 minutes), allow some tolerance
        assertNotNull(result)
        assertTrue(result!! in 595..605)
    }

    @Test
    fun `hasUserRequestedAccess should return correct value`() {
        val packageName = "com.example.test"
        val startTime = System.currentTimeMillis()
        val duration = 15

        `when`(mockSharedPreferences.getString("${packageName}_package", null)).thenReturn(packageName)
        `when`(mockSharedPreferences.getLong("${packageName}_start", 0L)).thenReturn(startTime)
        `when`(mockSharedPreferences.getInt("${packageName}_duration", 0)).thenReturn(duration)
        `when`(mockSharedPreferences.getBoolean("${packageName}_active", false)).thenReturn(true)

        val result = timerPersistence.hasUserRequestedAccess(packageName)

        assertTrue(result)
    }

    @Test
    fun `clearTimerState should remove all timer data`() {
        val packageName = "com.example.test"

        timerPersistence.clearTimerState(packageName)

        verify(mockEditor).remove("${packageName}_package")
        verify(mockEditor).remove("${packageName}_start")
        verify(mockEditor).remove("${packageName}_duration")
        verify(mockEditor).remove("${packageName}_active")
        verify(mockEditor).apply()
    }
}
