package com.example.wakt.presentation.screens.phonebrick

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
// Removed unused Schedule import
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakt.data.database.entity.BrickSessionType
import com.example.wakt.data.database.entity.PhoneBrickSession
import com.example.wakt.presentation.ui.theme.WaktGradient
import com.example.wakt.presentation.screens.phonebrick.viewmodel.PhoneBrickViewModel
import com.example.wakt.utils.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneBrickScreen(
    onNavigateBack: () -> Unit,
    viewModel: PhoneBrickViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var showEssentialAppsDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingSessionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Brick") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Essential apps configuration
                    IconButton(onClick = { showEssentialAppsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Essential Apps")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSessionDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Session")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current active session (if any)
            uiState.currentActiveSession?.let { session ->
                item {
                    CurrentActiveSessionCard(
                        session = session,
                        remainingMinutes = uiState.currentSessionRemainingMinutes ?: 0,
                        remainingSeconds = uiState.currentSessionRemainingSeconds ?: 0,
                        onEmergencyOverride = { viewModel.emergencyOverride() }
                    )
                }
            }
            
            // Quick start buttons
            item {
                QuickStartSection(
                    onStartFocusSession = { minutes ->
                        if (PermissionHelper.canDrawOverOtherApps(context)) {
                            viewModel.startQuickFocusSession(minutes)
                        } else {
                            pendingSessionAction = { viewModel.startQuickFocusSession(minutes) }
                            showPermissionDialog = true
                        }
                    }
                )
            }
            
            // Existing sessions list
            item {
                Text(
                    text = "Your Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (uiState.sessions.isEmpty()) {
                item {
                    EmptySessionsState(
                        onCreateSession = { showCreateSessionDialog = true }
                    )
                }
            } else {
                items(uiState.sessions) { session ->
                    SessionCard(
                        session = session,
                        onStartSession = {
                            if (PermissionHelper.canDrawOverOtherApps(context)) {
                                viewModel.startSession(session.id)
                            } else {
                                pendingSessionAction = { viewModel.startSession(session.id) }
                                showPermissionDialog = true
                            }
                        },
                        onDeleteSession = { viewModel.deleteSession(session.id) },
                        onToggleActive = { viewModel.toggleSessionActive(session.id) }
                    )
                }
            }
        }
    }
    
    // Create session dialog
    if (showCreateSessionDialog) {
        CreateSessionDialog(
            onDismiss = { showCreateSessionDialog = false },
            onCreateSession = { sessionType, name, config ->
                viewModel.createSession(sessionType, name, config)
                showCreateSessionDialog = false
            }
        )
    }
    
    // Essential apps dialog
    if (showEssentialAppsDialog) {
        EssentialAppsDialog(
            essentialApps = uiState.essentialApps,
            onDismiss = { showEssentialAppsDialog = false },
            onAddApp = { appName, packageName ->
                viewModel.addEssentialApp(appName, packageName)
            },
            onRemoveApp = { packageName ->
                viewModel.removeEssentialApp(packageName)
            }
        )
    }

    // Permission required dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                pendingSessionAction = null
            },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null)
            },
            title = {
                Text("Permission Required")
            },
            text = {
                Text(
                    "Brick sessions require 'Display over other apps' permission to show the focus overlay. " +
                    "Please grant this permission to continue."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PermissionHelper.requestOverlayPermission(context)
                        showPermissionDialog = false
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        pendingSessionAction = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CurrentActiveSessionCard(
    session: PhoneBrickSession,
    remainingMinutes: Int,
    remainingSeconds: Int,
    onEmergencyOverride: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (session.sessionType) {
                BrickSessionType.FOCUS_SESSION -> MaterialTheme.colorScheme.primaryContainer
                BrickSessionType.SLEEP_SCHEDULE -> MaterialTheme.colorScheme.secondaryContainer
                BrickSessionType.DIGITAL_DETOX -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🔒 ACTIVE SESSION",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            remainingMinutes >= 60 -> "${remainingMinutes / 60}h"
                            remainingMinutes > 1 -> "${remainingMinutes}m"
                            remainingMinutes == 1 -> {
                                val seconds = remainingSeconds % 60
                                if (seconds > 0) "${remainingMinutes}m" else "1m"
                            }
                            remainingSeconds > 0 -> "${remainingSeconds}s"
                            else -> "0s"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val timeText = when {
                remainingMinutes >= 60 -> {
                    val hours = remainingMinutes / 60
                    val mins = remainingMinutes % 60
                    if (mins > 0) "$hours hours $mins minutes remaining" else "$hours hours remaining"
                }
                remainingMinutes > 1 -> "$remainingMinutes minutes remaining"
                remainingMinutes == 1 -> {
                    val seconds = remainingSeconds % 60
                    if (seconds > 0) "$remainingMinutes minute $seconds seconds remaining" else "1 minute remaining"
                }
                remainingSeconds > 0 -> "$remainingSeconds seconds remaining"
                else -> "Ending now..."
            }
            
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (session.allowEmergencyOverride) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onEmergencyOverride,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Emergency Override")
                }
            }
        }
    }
}

@Composable
private fun QuickStartSection(
    onStartFocusSession: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Quick Focus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Start a focus session right now",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickStartButton(
                    text = "25 min",
                    subtitle = "Pomodoro",
                    onClick = { onStartFocusSession(25) },
                    modifier = Modifier.weight(1f)
                )
                
                QuickStartButton(
                    text = "1 hour",
                    subtitle = "Deep work",
                    onClick = { onStartFocusSession(60) },
                    modifier = Modifier.weight(1f)
                )
                
                QuickStartButton(
                    text = "2 hours",
                    subtitle = "Extended",
                    onClick = { onStartFocusSession(120) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickStartButton(
    text: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: PhoneBrickSession,
    onStartSession: () -> Unit,
    onDeleteSession: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = when (session.sessionType) {
                            BrickSessionType.FOCUS_SESSION -> "Focus Session • ${session.durationMinutes}min"
                            BrickSessionType.DIGITAL_DETOX -> "Digital Detox • ${session.durationMinutes}min"
                            BrickSessionType.SLEEP_SCHEDULE -> {
                                "Sleep Schedule • ${String.format("%02d:%02d", session.startHour, session.startMinute)} - " +
                                "${String.format("%02d:%02d", session.endHour, session.endMinute)}"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (session.sessionType == BrickSessionType.SLEEP_SCHEDULE) {
                        Text(
                            text = "Active on: ${formatActiveDays(session.activeDaysOfWeek)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active/Inactive toggle
                    Switch(
                        checked = session.isActive,
                        onCheckedChange = { onToggleActive() }
                    )
                    
                    // Delete button
                    IconButton(
                        onClick = onDeleteSession,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            
            if (session.isActive && session.sessionType != BrickSessionType.SLEEP_SCHEDULE) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Session")
                }
            }
        }
    }
}

@Composable
private fun EmptySessionsState(
    onCreateSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📱",
            style = MaterialTheme.typography.displayMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No Sessions Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Create your first brick session to get started with focused productivity",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onCreateSession
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Session")
        }
    }
}

private fun formatActiveDays(daysString: String): String {
    val dayNames = mapOf(
        '1' to "Sun", '2' to "Mon", '3' to "Tue", '4' to "Wed",
        '5' to "Thu", '6' to "Fri", '7' to "Sat"
    )
    
    return if (daysString == "1234567") {
        "Daily"
    } else if (daysString == "23456") {
        "Weekdays" 
    } else if (daysString == "17") {
        "Weekends"
    } else {
        daysString.map { dayNames[it] ?: "" }.joinToString(", ")
    }
}