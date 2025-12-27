package com.example.wakt.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.presentation.ui.theme.WaktGradient
import com.example.wakt.utils.GlobalSettingsManager

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val challengeType by viewModel.challengeType.collectAsState()
    val waitTimeMinutes by viewModel.waitTimeMinutes.collectAsState()
    val clickCount by viewModel.clickCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Challenge Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Challenge Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "When you try to open a blocked app or website, you'll need to complete a challenge first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Challenge Type
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Challenge Type",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            onClick = { viewModel.setChallengeType(ChallengeType.WAIT) },
                            label = { Text("Wait Timer") },
                            selected = challengeType == ChallengeType.WAIT,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Transparent,
                                selectedLabelColor = Color.White
                            ),
                            modifier = if (challengeType == ChallengeType.WAIT) {
                                Modifier.background(brush = WaktGradient, shape = RoundedCornerShape(16.dp))
                            } else Modifier
                        )
                        FilterChip(
                            onClick = { viewModel.setChallengeType(ChallengeType.CLICK_500) },
                            label = { Text("Clicks") },
                            selected = challengeType == ChallengeType.CLICK_500,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Transparent,
                                selectedLabelColor = Color.White
                            ),
                            modifier = if (challengeType == ChallengeType.CLICK_500) {
                                Modifier.background(brush = WaktGradient, shape = RoundedCornerShape(16.dp))
                            } else Modifier
                        )
                    }
                }

                // Wait Time Slider (only shown for WAIT challenge)
                if (challengeType == ChallengeType.WAIT) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Wait Time: $waitTimeMinutes minutes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Slider(
                            value = waitTimeMinutes.toFloat(),
                            onValueChange = { viewModel.setWaitTimeMinutes(it.toInt()) },
                            valueRange = GlobalSettingsManager.MIN_WAIT_TIME.toFloat()..GlobalSettingsManager.MAX_WAIT_TIME.toFloat(),
                            steps = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${GlobalSettingsManager.MIN_WAIT_TIME} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${GlobalSettingsManager.MAX_WAIT_TIME} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Click Count Slider (only shown for CLICK challenge)
                if (challengeType == ChallengeType.CLICK_500) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Click Count: $clickCount clicks",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Slider(
                            value = clickCount.toFloat(),
                            onValueChange = { viewModel.setClickCount(it.toInt()) },
                            valueRange = GlobalSettingsManager.MIN_CLICK_COUNT.toFloat()..GlobalSettingsManager.MAX_CLICK_COUNT.toFloat(),
                            steps = 8,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${GlobalSettingsManager.MIN_CLICK_COUNT}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${GlobalSettingsManager.MAX_CLICK_COUNT}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Wakt helps you stay focused by blocking distracting apps and websites.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
