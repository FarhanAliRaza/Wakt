package com.farhanaliraza.wakt.presentation.viewmodel

import com.farhanaliraza.wakt.data.database.entity.ChallengeType
import com.farhanaliraza.wakt.presentation.activities.viewmodel.BlockingOverlayViewModel
import com.farhanaliraza.wakt.utils.TimerPersistence
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
    private lateinit var mockTimerPersistence: TimerPersistence

    private lateinit var viewModel: BlockingOverlayViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BlockingOverlayViewModel(mockTimerPersistence)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializeChallenge should set up wait timer challenge`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "15" // 15 minutes

        `when`(mockTimerPersistence.getRemainingTimeSeconds(identifier)).thenReturn(null)

        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, challengeData)

        val uiState = viewModel.uiState.value
        assertEquals(ChallengeType.WAIT, uiState.challengeType)
        assertEquals(15, uiState.totalTimeMinutes)
        assertFalse(uiState.hasUserRequestedAccess)
        assertTrue(uiState.canRequestAccess)
    }

    @Test
    fun `initializeChallenge should handle running timer`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "15"
        val remainingSeconds = 600 // 10 minutes remaining

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

        `when`(mockTimerPersistence.getRemainingTimeSeconds(identifier)).thenReturn(0)

        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, challengeData)

        val uiState = viewModel.uiState.value
        // When remaining is 0, it shows the request access button
        assertFalse(uiState.hasUserRequestedAccess)
        assertTrue(uiState.canRequestAccess)
    }

    @Test
    fun `requestAccess should start timer`() = runTest {
        val identifier = "com.example.test"
        val challengeData = "10"

        // Initialize first
        `when`(mockTimerPersistence.getRemainingTimeSeconds(identifier)).thenReturn(null)
        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, challengeData)

        // Request access
        viewModel.requestAccess()

        // Verify saveTimerState was called (using any() matchers to avoid null issues)
        verify(mockTimerPersistence).saveTimerState(anyString(), anyLong(), anyInt())

        val uiState = viewModel.uiState.value
        assertTrue(uiState.hasUserRequestedAccess)
        assertEquals(10 * 60, uiState.remainingTimeSeconds)
    }

    @Test
    fun `invalid challenge data should use default values`() = runTest {
        val identifier = "com.example.test"
        val invalidChallengeData = "not-a-number"

        `when`(mockTimerPersistence.getRemainingTimeSeconds(identifier)).thenReturn(null)

        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, invalidChallengeData)

        val uiState = viewModel.uiState.value
        assertEquals(10, uiState.totalTimeMinutes) // Should default to 10 minutes
    }

    @Test
    fun `challenge data edge cases should be handled`() = runTest {
        val identifier = "com.example.test"

        // Test empty challenge data
        `when`(mockTimerPersistence.getRemainingTimeSeconds(identifier)).thenReturn(null)
        viewModel.initializeChallenge(identifier, ChallengeType.WAIT, "")
        assertEquals(10, viewModel.uiState.value.totalTimeMinutes)
    }
}
