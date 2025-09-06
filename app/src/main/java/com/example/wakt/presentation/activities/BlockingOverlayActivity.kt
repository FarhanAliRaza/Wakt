package com.example.wakt.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
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
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.presentation.activities.viewmodel.BlockingOverlayViewModel
import com.example.wakt.presentation.activities.viewmodel.BlockingOverlayUiState
import com.example.wakt.presentation.ui.theme.WaktTheme
import com.example.wakt.presentation.ui.theme.WaktGradient
import com.example.wakt.services.AppBlockingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class BlockingOverlayActivity : ComponentActivity() {
    
    private var isWebsiteBlock = false
    private var websiteUrl: String? = null
    private var packageName: String? = null
    private var hasTriggeredDismissal = false  // Track if we've already handled dismissal
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appName = intent.getStringExtra("app_name") ?: "Unknown App"
        packageName = intent.getStringExtra("package_name") ?: ""
        websiteUrl = intent.getStringExtra("website_url")
        isWebsiteBlock = intent.getBooleanExtra("is_website_block", false)
        val isGoalBlock = intent.getBooleanExtra("is_goal_block", false)
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
                    challengeType = challengeType,
                    challengeData = challengeData,
                    onUnblockComplete = { 
                        handleDismissal()
                        finish() 
                    },
                    onCancel = { 
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
    challengeType: ChallengeType,
    challengeData: String,
    onUnblockComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: BlockingOverlayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(challengeType, challengeData, packageName, websiteUrl) {
        val identifier = if (isWebsiteBlock) websiteUrl ?: packageName else packageName
        viewModel.initializeChallenge(identifier, challengeType, challengeData)
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
                    text = if (isWebsiteBlock) "Website Blocked" else "App Blocked",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = if (isWebsiteBlock) {
                        "You've blocked access to this website:\n${websiteUrl ?: appName}"
                    } else {
                        "You've blocked access to $appName"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
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
                                "🎯 LONG-TERM GOAL BLOCK",
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
                            onTimeComplete = onUnblockComplete,
                            onRequestAccess = { viewModel.requestAccess() }
                        )
                    }
                    ChallengeType.CLICK_500 -> {
                        Click500Challenge(
                            onChallengeComplete = onUnblockComplete,
                            viewModel = viewModel
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    if (challengeType == ChallengeType.WAIT && uiState.timerCompleted) {
                        Button(
                            onClick = onUnblockComplete,
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    brush = WaktGradient,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Text("Continue", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitTimerChallenge(
    uiState: BlockingOverlayUiState,
    onTimeComplete: () -> Unit,
    onRequestAccess: () -> Unit
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
                // Timer completed
                Text(
                    text = "Time's up! You can now access the app.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
private fun Click500Challenge(
    onChallengeComplete: () -> Unit,
    viewModel: BlockingOverlayViewModel
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
            Text(
                text = "Challenge completed! You can now access the blocked content.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
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

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}