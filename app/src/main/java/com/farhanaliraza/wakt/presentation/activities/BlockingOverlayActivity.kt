package com.farhanaliraza.wakt.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.farhanaliraza.wakt.utils.TemporaryUnlock
import com.farhanaliraza.wakt.data.database.entity.ChallengeType
import com.farhanaliraza.wakt.presentation.activities.viewmodel.BlockingOverlayViewModel
import com.farhanaliraza.wakt.presentation.activities.viewmodel.BlockingOverlayUiState
import com.farhanaliraza.wakt.presentation.ui.theme.WaktTheme
import com.farhanaliraza.wakt.presentation.ui.theme.WaktGradient
import com.farhanaliraza.wakt.services.AppBlockingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class BlockingOverlayActivity : ComponentActivity() {

    private var isWebsiteBlock = false
    private var websiteUrl: String? = null
    private var packageName: String? = null
    private var hasTriggeredDismissal = false  // Track if we've already handled dismissal

    @Inject
    lateinit var temporaryUnlock: TemporaryUnlock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra("app_name") ?: "Unknown App"
        packageName = intent.getStringExtra("package_name") ?: ""
        websiteUrl = intent.getStringExtra("website_url")
        isWebsiteBlock = intent.getBooleanExtra("is_website_block", false)
        val isGoalBlock = intent.getBooleanExtra("is_goal_block", false)
        val isScheduledBlock = intent.getBooleanExtra("is_scheduled_block", false)
        val scheduleEndTime = intent.getLongExtra("schedule_end_time", 0L)
        val challengeTypeString = intent.getStringExtra("challenge_type") ?: "WAIT"
        val challengeData = intent.getStringExtra("challenge_data") ?: "10"

        val challengeType = try {
            ChallengeType.valueOf(challengeTypeString)
        } catch (e: Exception) {
            ChallengeType.WAIT
        }

        // Handle back button press with modern approach
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleDismissal()
                finish()
            }
        })

        setContent {
            WaktTheme {
                BlockingOverlayScreen(
                    appName = appName,
                    packageName = packageName ?: "",
                    websiteUrl = websiteUrl,
                    isWebsiteBlock = isWebsiteBlock,
                    isGoalBlock = isGoalBlock,
                    isScheduledBlock = isScheduledBlock,
                    scheduleEndTime = scheduleEndTime,
                    challengeType = challengeType,
                    challengeData = challengeData,
                    onUnblockComplete = { minutes ->
                        val identifier = if (isWebsiteBlock) websiteUrl ?: packageName!! else packageName!!
                        temporaryUnlock.createTemporaryUnlock(identifier, minutes)
                        handleDismissal()
                        finish()
                    },
                    onCancel = {
                        handleDismissal()
                        finish()
                    },
                    onScheduleEnded = {
                        // Auto-dismiss when schedule ends
                        handleDismissal()
                        finish()
                    }
                )
            }
        }
    }
    
    private fun handleDismissal() {
        if (!hasTriggeredDismissal && isWebsiteBlock) {
            hasTriggeredDismissal = true
            AppBlockingService.onOverlayDismissed(websiteUrl, packageName)
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Handle dismissal when activity goes to background (home button, app switch)
        handleDismissal()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Final cleanup in case onStop wasn't called
        handleDismissal()
    }
}

@Composable
fun BlockingOverlayScreen(
    appName: String,
    packageName: String,
    websiteUrl: String? = null,
    isWebsiteBlock: Boolean = false,
    isGoalBlock: Boolean = false,
    isScheduledBlock: Boolean = false,
    scheduleEndTime: Long = 0L,
    challengeType: ChallengeType,
    challengeData: String,
    onUnblockComplete: (Int) -> Unit,
    onCancel: () -> Unit,
    onScheduleEnded: () -> Unit = {},
    viewModel: BlockingOverlayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(challengeType, challengeData, packageName, websiteUrl, isScheduledBlock, scheduleEndTime) {
        val identifier = if (isWebsiteBlock) websiteUrl ?: packageName else packageName
        viewModel.initializeChallenge(identifier, challengeType, challengeData, isScheduledBlock, scheduleEndTime)
    }

    // Auto-dismiss when schedule ends
    LaunchedEffect(uiState.scheduleEnded) {
        if (uiState.scheduleEnded) {
            onScheduleEnded()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Text(
                    text = when {
                        isScheduledBlock -> "Scheduled Block"
                        isWebsiteBlock -> "Website Blocked"
                        else -> "App Blocked"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = when {
                        isScheduledBlock -> "This app is blocked by your schedule"
                        isWebsiteBlock -> "You've blocked access to this website:\n${websiteUrl ?: appName}"
                        else -> "You've blocked access to $appName"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show schedule info for scheduled blocks
                if (isScheduledBlock && scheduleEndTime > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Blocked until ${uiState.scheduleEndTimeFormatted}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.scheduleRemainingSeconds > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${formatScheduleTime(uiState.scheduleRemainingSeconds)} remaining",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                if (isGoalBlock) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LONG-TERM GOAL BLOCK",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "This is part of your commitment goal and cannot be removed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when (challengeType) {
                    ChallengeType.WAIT -> {
                        WaitTimerChallenge(
                            uiState = uiState,
                            onTimeComplete = { onUnblockComplete(5) }, // Default 5 minutes
                            onRequestAccess = { viewModel.requestAccess() },
                            onUnlockWithTime = onUnblockComplete,
                            isGoalBlock = isGoalBlock
                        )
                    }
                    ChallengeType.CLICK_500 -> {
                        Click500Challenge(
                            onChallengeComplete = { onUnblockComplete(5) }, // Default 5 minutes
                            onUnlockWithTime = onUnblockComplete,
                            viewModel = viewModel,
                            isGoalBlock = isGoalBlock
                        )
                    }

                    ChallengeType.QUESTION -> TODO()
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Cancel")
                    }

                    // Remove the old continue button - now handled in the challenge components
                }
            }
        }
    }
}

private fun formatScheduleTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%dh %02dm", hours, minutes)
        minutes > 0 -> String.format("%dm %02ds", minutes, secs)
        else -> String.format("%ds", secs)
    }
}

@Composable
private fun WaitTimerChallenge(
    uiState: BlockingOverlayUiState,
    onTimeComplete: () -> Unit,
    onRequestAccess: () -> Unit,
    onUnlockWithTime: (Int) -> Unit,
    isGoalBlock: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Wait Timer Challenge",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        
        when {
            !uiState.hasUserRequestedAccess && uiState.canRequestAccess -> {
                // User hasn't requested access yet, show button
                Text(
                    text = "You need to wait ${uiState.totalTimeMinutes} minutes before accessing this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onRequestAccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = WaktGradient,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text(
                        "Request Access (Start ${uiState.totalTimeMinutes}min Timer)",
                        color = Color.White
                    )
                }
                
                Text(
                    text = "Tip: The timer will continue running even if you close the app.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            uiState.hasUserRequestedAccess && uiState.remainingTimeSeconds > 0 -> {
                // Timer is running
                Text(
                    text = "Please wait before accessing this app",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(60.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatTime(uiState.remainingTimeSeconds),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                val progress = if (uiState.totalTimeSeconds > 0) {
                    1f - (uiState.remainingTimeSeconds.toFloat() / uiState.totalTimeSeconds.toFloat())
                } else 0f
                
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { progress }
                )
            }
            
            uiState.timerCompleted -> {
                // Timer completed - show time selection
                ChallengeCompletedScreen(
                    onUnlockWithTime = onUnlockWithTime,
                    isGoalBlock = isGoalBlock
                )
            }
        }
    }
}


@Composable
private fun Click500Challenge(
    onChallengeComplete: () -> Unit,
    onUnlockWithTime: (Int) -> Unit,
    viewModel: BlockingOverlayViewModel,
    isGoalBlock: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "500 Click Challenge",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Click the button below 500 times to unlock access.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Clicks: ${uiState.clickCount}/500",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        val progress = uiState.clickCount / 500f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        
        Button(
            onClick = { 
                viewModel.incrementClickCount()
                if (uiState.clickCount >= 500) {
                    onChallengeComplete()
                }
            },
            modifier = Modifier
                .size(120.dp)
                .padding(8.dp)
                .background(
                    brush = WaktGradient,
                    shape = CircleShape
                ),
            shape = RoundedCornerShape(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Text(
                text = "CLICK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = Color.White
            )
        }
        
        if (uiState.clickCount >= 500) {
            ChallengeCompletedScreen(
                onUnlockWithTime = onUnlockWithTime,
                isGoalBlock = isGoalBlock
            )
        } else {
            val remaining = 500 - uiState.clickCount
            Text(
                text = "$remaining clicks remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChallengeCompletedScreen(
    onUnlockWithTime: (Int) -> Unit,
    isGoalBlock: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Challenge Completed! 🎉",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = if (isGoalBlock) {
                "Select how long you'd like temporary access:"
            } else {
                "Great job! Select how long you'd like access:"
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Time selection buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimeSelectionButton(
                text = "5 Minutes",
                subtitle = "Quick access",
                onClick = { onUnlockWithTime(5) },
                isPrimary = true
            )
            
            TimeSelectionButton(
                text = "10 Minutes",
                subtitle = "Moderate access",
                onClick = { onUnlockWithTime(10) }
            )
            
            TimeSelectionButton(
                text = "20 Minutes",
                subtitle = if (isGoalBlock) "Extended access" else "Extended access",
                onClick = { onUnlockWithTime(20) }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isGoalBlock) {
                "⚠️ Remember: This is temporary access for your long-term goal"
            } else {
                "💡 Use this time wisely - blocking will resume afterward"
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeSelectionButton(
    text: String,
    subtitle: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
        colors = if (isPrimary) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isPrimary) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPrimary) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}