package com.example.wakt.presentation.viewmodel

import android.content.Context
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.presentation.activities.viewmodel.BlockingOverlayViewModel
import com.example.wakt.utils.TimerPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for BlockingOverlayViewModel challenge logic
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class BlockingOverlayViewModelTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockTimerPersistence: TimerPersistence
    
    private lateinit var viewModel: BlockingOverlayViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BlockingOverlayViewModel(mockContext, mockTimerPersistence)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializeChallenge should set up wait timer challenge`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "15" // 15 minutes
        
        `when`(mockTimerPersistence.hasUserRequestedAccess(identifier)).thenReturn(false)

        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, challengeData)

        val uiState = viewModel.uiState.value
        assertEquals(ChallengeType.WAIT, uiState.challengeType)
        assertEquals(15, uiState.totalTimeMinutes)
        assertEquals(15 * 60, uiState.totalTimeSeconds)
        assertFalse(uiState.hasUserRequestedAccess)
        assertTrue(uiState.canRequestAccess)
    }

    @Test
    fun `initializeChallenge should handle running timer`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "15"
        val remainingSeconds = 600 // 10 minutes remaining
        
        `when`(mockTimerPersistence.hasUserRequestedAccess(identifier)).thenReturn(true)
        `when`(mockTimerPersistence.isTimerRunning(identifier)).thenReturn(true)
        `when`(mockTimerPersistence.getRemainingTimeSeconds(identifier)).thenReturn(remainingSeconds)

        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, challengeData)

        val uiState = viewModel.uiState.value
        assertTrue(uiState.hasUserRequestedAccess)
        assertEquals(remainingSeconds, uiState.remainingTimeSeconds)
        assertFalse(uiState.timerCompleted)
    }

    @Test
    fun `initializeChallenge should handle completed timer`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "15"
        
        `when`(mockTimerPersistence.hasUserRequestedAccess(identifier)).thenReturn(true)
        `when`(mockTimerPersistence.isTimerRunning(identifier)).thenReturn(false)
        `when`(mockTimerPersistence.getRemainingTimeSeconds(identifier)).thenReturn(0)

        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, challengeData)

        val uiState = viewModel.uiState.value
        assertTrue(uiState.hasUserRequestedAccess)
        assertTrue(uiState.timerCompleted)
        assertEquals(0, uiState.remainingTimeSeconds)
    }

    @Test
    fun `requestAccess should start timer`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "10"
        
        // Initialize first
        `when`(mockTimerPersistence.hasUserRequestedAccess(identifier)).thenReturn(false)
        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, challengeData)

        // Request access
        viewModel.requestAccess()

        verify(mockTimerPersistence).saveTimerState(eq(identifier), eq(10), any())
        
        val uiState = viewModel.uiState.value
        assertTrue(uiState.hasUserRequestedAccess)
        assertEquals(10 * 60, uiState.remainingTimeSeconds)
    }

    @Test
    fun `initializeChallenge should handle question challenge`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "What is 2+2?"
        
        viewModel.initializeChallenge(identifier, ChallengeType.QUESTION, challengeData)

        val uiState = viewModel.uiState.value
        assertEquals(ChallengeType.QUESTION, uiState.challengeType)
        assertEquals(challengeData, uiState.customQuestionData)
    }

    @Test
    fun `invalid challenge data should use default values`() = runTest {
        val identifier = "com.example.test"
        val invalidChallengeData = "not-a-number"
        
        `when`(mockTimerPersistence.hasUserRequestedAccess(identifier)).thenReturn(false)

        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, invalidChallengeData)

        val uiState = viewModel.uiState.value
        assertEquals(10, uiState.totalTimeMinutes) // Should default to 10 minutes
    }

    @Test
    fun `challenge data edge cases should be handled`() = runTest {
        val identifier = "com.example.test"
        
        // Test empty challenge data
        `when`(mockTimerPersistence.hasUserRequestedAccess(identifier)).thenReturn(false)
        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, "")
        assertEquals(10, viewModel.uiState.value.totalTimeMinutes)

        // Test very large number
        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, "999")
        assertEquals(60, viewModel.uiState.value.totalTimeMinutes) // Should be capped at 60

        // Test negative number
        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, "-5")
        assertEquals(10, viewModel.uiState.value.totalTimeMinutes) // Should default to 10
    }
}