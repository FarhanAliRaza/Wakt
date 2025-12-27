package com.example.wakt.presentation.screens.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakt.data.database.entity.PhoneBrickSession
import com.example.wakt.presentation.screens.schedule.components.ScheduleCard
import java.util.Calendar

@Composable
fun ScheduleScreen(
    onNavigateToAddSchedule: (isAppSchedule: Boolean) -> Unit = {},
    onNavigateToEditSchedule: (Long) -> Unit = {},
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddSchedule(uiState.selectedTabIndex == 1) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Schedule",
                    tint = Color.White
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )

            // Tab Row
            TabRow(
                selectedTabIndex = uiState.selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = uiState.selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Phone") }
                )
                Tab(
                    selected = uiState.selectedTabIndex == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Apps") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Next lock reminder card
            uiState.nextScheduledLock?.let { nextLock ->
                NextLockReminderCard(
                    schedule = nextLock,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Schedule list
            val schedules = when (uiState.selectedTabIndex) {
                0 -> uiState.phoneSchedules
                1 -> uiState.appSchedules
                else -> emptyList()
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (schedules.isEmpty()) {
                EmptyScheduleState(
                    isPhoneTab = uiState.selectedTabIndex == 0,
                    onAddClick = { onNavigateToAddSchedule(uiState.selectedTabIndex == 1) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = schedules,
                        key = { it.id }
                    ) { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            onToggle = { active ->
                                viewModel.toggleScheduleActive(schedule.id, active)
                            },
                            onClick = { onNavigateToEditSchedule(schedule.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextLockReminderCard(
    schedule: PhoneBrickSession,
    modifier: Modifier = Modifier
) {
    val timeUntilLock = calculateTimeUntilLock(schedule)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Next lock in $timeUntilLock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = formatScheduleTime(schedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduleState(
    isPhoneTab: Boolean,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isPhoneTab) "No phone schedules yet" else "No app schedules yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isPhoneTab)
                "Create a schedule to lock your entire phone at specific times"
            else
                "Create a schedule to block specific apps at specific times",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onAddClick,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Schedule")
        }
    }
}

private fun calculateTimeUntilLock(schedule: PhoneBrickSession): String {
    val now = Calendar.getInstance()
    val startHour = schedule.startHour ?: return ""
    val startMinute = schedule.startMinute ?: return ""

    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)

    var minutesUntil = (startHour * 60 + startMinute) - (currentHour * 60 + currentMinute)

    if (minutesUntil < 0) {
        minutesUntil += 24 * 60 // Add a day if schedule is tomorrow
    }

    val hours = minutesUntil / 60
    val minutes = minutesUntil % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "now"
    }
}

private fun formatScheduleTime(schedule: PhoneBrickSession): String {
    val startHour = schedule.startHour ?: 0
    val startMinute = schedule.startMinute ?: 0
    return String.format("%02d:%02d", startHour, startMinute)
}
