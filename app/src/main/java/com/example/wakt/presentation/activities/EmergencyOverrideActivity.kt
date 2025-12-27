package com.example.wakt.presentation.activities

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.presentation.activities.viewmodel.EmergencyOverrideViewModel
import com.example.wakt.presentation.ui.theme.WaktTheme
import com.example.wakt.services.BrickOverlayService
import com.example.wakt.utils.BrickSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class EmergencyOverrideActivity : ComponentActivity() {

    @Inject
    lateinit var brickSessionManager: BrickSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Suspend the brick overlay to prevent it from covering this activity
        BrickOverlayService.suspendForEmergency()

        // Set window flags to appear above the brick overlay
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Prevent back navigation during emergency override
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - user must complete emergency override or wait
            }
        })
        
        setContent {
            WaktTheme {
                EmergencyOverrideScreen(
                    onEmergencyComplete = {
                        // Resume overlay when emergency is complete
                        BrickOverlayService.resumeAfterEmergency()
                        finish()
                    },
                    onCancel = {
                        // Resume overlay when user cancels
                        BrickOverlayService.resumeAfterEmergency()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun EmergencyOverrideScreen(
    onEmergencyComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EmergencyOverrideViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Watch for override completion and finish the activity
    LaunchedEffect(uiState.overrideCompleted) {
        if (uiState.overrideCompleted) {
            onEmergencyComplete()
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
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "⚠️ EMERGENCY OVERRIDE",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "You are requesting to break your focus session early. This should only be used for genuine emergencies.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when (uiState.currentStep) {
                    EmergencyStep.CONFIRMATION -> {
                        EmergencyConfirmationStep(
                            onConfirm = { viewModel.confirmEmergency() },
                            onCancel = onCancel
                        )
                    }
                    EmergencyStep.CHALLENGE -> {
                        EmergencyChallengeStep(
                            challengeType = uiState.challengeType,
                            remainingTimeSeconds = uiState.challengeTimeRemaining,
                            clicksRemaining = uiState.clicksRemaining,
                            challengeCompleted = uiState.challengeCompleted,
                            onChallengeComplete = { viewModel.completeChallenge() },
                            onClickRecord = { viewModel.recordClick() },
                            onCancel = onCancel
                        )
                    }
                    EmergencyStep.REASON -> {
                        // Reason step no longer used - override executes immediately after challenge
                        Text("Processing...")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyConfirmationStep(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Are you sure you want to break your focus session?",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "This action will be logged and will count against your session completion rate.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Continue", color = Color.White)
            }
        }
    }
}

@Composable
private fun EmergencyChallengeStep(
    challengeType: ChallengeType,
    remainingTimeSeconds: Int,
    clicksRemaining: Int,
    challengeCompleted: Boolean,
    onChallengeComplete: () -> Unit,
    onClickRecord: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Emergency Override Challenge",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        when (challengeType) {
            ChallengeType.WAIT -> {
                Text(
                    text = "Please wait before proceeding.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (remainingTimeSeconds > 0) {
                    var timeLeft by remember { mutableIntStateOf(remainingTimeSeconds) }

                    LaunchedEffect(remainingTimeSeconds) {
                        while (timeLeft > 0) {
                            delay(1000)
                            timeLeft--
                        }
                        onChallengeComplete()
                    }

                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                MaterialTheme.colorScheme.error,
                                RoundedCornerShape(60.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeLeft.toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = onChallengeComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Proceed", color = Color.White)
                    }
                }
            }
            ChallengeType.CLICK_500 -> {
                Text(
                    text = "Click to verify",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Taps remaining: $clicksRemaining",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )

                Button(
                    onClick = onClickRecord,
                    modifier = Modifier
                        .size(140.dp)
                        .padding(8.dp),
                    enabled = !challengeCompleted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "TAP",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Show completion message when done
                if (challengeCompleted) {
                    Text(
                        text = "✓ Challenge complete! Ending session...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            else -> {
                Text(
                    text = "Challenge type not implemented",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!challengeCompleted) {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Cancel Override")
            }
        }
    }
}

@Composable
private fun EmergencyReasonStep(
    reason: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Emergency Reason",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Please briefly explain why you need to break this session:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        OutlinedTextField(
            value = reason,
            onValueChange = onReasonChange,
            label = { Text("Reason") },
            placeholder = { Text("e.g., Medical emergency, urgent work call...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Override Session", color = Color.White)
            }
        }
    }
}

enum class EmergencyStep {
    CONFIRMATION,
    CHALLENGE,
    REASON
}