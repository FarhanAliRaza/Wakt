package com.example.wakt.presentation.screens.phonebrick

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.wakt.data.database.entity.BrickSessionType

data class SessionConfig(
    val durationMinutes: Int? = null,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val activeDaysOfWeek: String = "1234567",
    val selectedEssentialApps: Set<String> = emptySet()
)

enum class DialogStep {
    NAME, TYPE_AND_DURATION, APPS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onCreateSession: (BrickSessionType, String, SessionConfig) -> Unit
) {
    var currentStep by remember { mutableStateOf(DialogStep.NAME) }
    var sessionName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(BrickSessionType.FOCUS_SESSION) }
    var sessionConfig by remember { mutableStateOf(SessionConfig()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with progress indicator
                DialogHeader(
                    currentStep = currentStep,
                    onBack = {
                        when (currentStep) {
                            DialogStep.TYPE_AND_DURATION -> currentStep = DialogStep.NAME
                            DialogStep.APPS -> currentStep = DialogStep.TYPE_AND_DURATION
                            else -> onDismiss()
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Step content
                when (currentStep) {
                    DialogStep.NAME -> {
                        NameStep(
                            sessionName = sessionName,
                            onNameChange = { sessionName = it },
                            onNext = { currentStep = DialogStep.TYPE_AND_DURATION }
                        )
                    }
                    DialogStep.TYPE_AND_DURATION -> {
                        TypeAndDurationStep(
                            selectedType = selectedType,
                            sessionConfig = sessionConfig,
                            onTypeSelect = { selectedType = it },
                            onConfigChange = { sessionConfig = it },
                            onNext = { currentStep = DialogStep.APPS }
                        )
                    }
                    DialogStep.APPS -> {
                        AppsStep(
                            sessionConfig = sessionConfig,
                            onConfigChange = { sessionConfig = it },
                            onCreateSession = {
                                onCreateSession(selectedType, sessionName, sessionConfig)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(
    currentStep: DialogStep,
    onBack: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            
            Text(
                text = "Create New Session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Progress indicator
        StepProgressIndicator(currentStep = currentStep)
    }
}

@Composable
private fun StepProgressIndicator(currentStep: DialogStep) {
    val steps = listOf("Name", "Session", "Apps")
    val currentStepIndex = when (currentStep) {
        DialogStep.NAME -> 0
        DialogStep.TYPE_AND_DURATION -> 1
        DialogStep.APPS -> 2
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { index, stepName ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (index <= currentStepIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index <= currentStepIndex) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = stepName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index <= currentStepIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(2.dp)
                        .background(
                            color = if (index < currentStepIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun NameStep(
    sessionName: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column {
        Text(
            text = "What would you like to call this session?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Choose a name that helps you remember what this session is for.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = sessionName,
            onValueChange = onNameChange,
            label = { Text("Session Name") },
            placeholder = { Text("e.g., Morning Focus, Study Time, Bedtime") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onNext,
                enabled = sessionName.isNotBlank()
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun TypeAndDurationStep(
    selectedType: BrickSessionType,
    sessionConfig: SessionConfig,
    onTypeSelect: (BrickSessionType) -> Unit,
    onConfigChange: (SessionConfig) -> Unit,
    onNext: () -> Unit
) {
    Column {
        Text(
            text = "What type of session do you want?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SessionTypeCard(
                type = BrickSessionType.FOCUS_SESSION,
                icon = Icons.Default.Star,
                title = "Focus Session",
                description = "Block distractions for any duration (1 minute - 24 hours)",
                isSelected = selectedType == BrickSessionType.FOCUS_SESSION,
                onSelect = { onTypeSelect(BrickSessionType.FOCUS_SESSION) }
            )
            
            SessionTypeCard(
                type = BrickSessionType.SLEEP_SCHEDULE,
                icon = Icons.Default.Lock,
                title = "Sleep Schedule",
                description = "Automatically brick during specific hours daily",
                isSelected = selectedType == BrickSessionType.SLEEP_SCHEDULE,
                onSelect = { onTypeSelect(BrickSessionType.SLEEP_SCHEDULE) }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Duration/Schedule configuration
        when (selectedType) {
            BrickSessionType.FOCUS_SESSION, BrickSessionType.DIGITAL_DETOX -> {
                ImprovedDurationConfiguration(
                    durationMinutes = sessionConfig.durationMinutes ?: 25,
                    onDurationChange = { duration ->
                        onConfigChange(sessionConfig.copy(durationMinutes = duration))
                    }
                )
            }
            BrickSessionType.SLEEP_SCHEDULE -> {
                ScheduleConfiguration(
                    startHour = sessionConfig.startHour ?: 23,
                    startMinute = sessionConfig.startMinute ?: 0,
                    endHour = sessionConfig.endHour ?: 6,
                    endMinute = sessionConfig.endMinute ?: 0,
                    activeDays = sessionConfig.activeDaysOfWeek,
                    onTimeChange = { startH, startM, endH, endM ->
                        onConfigChange(sessionConfig.copy(
                            startHour = startH,
                            startMinute = startM,
                            endHour = endH,
                            endMinute = endM
                        ))
                    },
                    onDaysChange = { days ->
                        onConfigChange(sessionConfig.copy(activeDaysOfWeek = days))
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onNext) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun SessionTypeCard(
    type: BrickSessionType,
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        }
    }
}

@Composable
private fun AppsStep(
    sessionConfig: SessionConfig,
    onConfigChange: (SessionConfig) -> Unit,
    onCreateSession: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Essential Apps (Optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Select apps that will remain accessible during your session",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Embedded app selector instead of separate dialog
        EssentialAppsGrid(
            selectedApps = sessionConfig.selectedEssentialApps,
            onAppsChanged = { selectedApps ->
                onConfigChange(sessionConfig.copy(selectedEssentialApps = selectedApps))
            },
            modifier = Modifier.heightIn(min = 200.dp, max = 400.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Info about emergency access
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Emergency Access Always Available",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Emergency calls and critical functions remain accessible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onCreateSession,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Session")
        }
    }
}

@Composable
private fun ImprovedDurationConfiguration(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit
) {
    val presets = listOf(
        1 to "1m", 5 to "5m", 15 to "15m", 25 to "25m", 
        45 to "45m", 60 to "1h", 90 to "1.5h", 120 to "2h",
        180 to "3h", 240 to "4h", 480 to "8h", 720 to "12h", 1440 to "24h"
    )
    
    Column {
        Text(
            text = "How long should this session last?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Current duration display
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    durationMinutes >= 1440 -> "${durationMinutes / 1440} day${if (durationMinutes > 1440) "s" else ""}"
                    durationMinutes >= 60 -> {
                        val hours = durationMinutes / 60
                        val mins = durationMinutes % 60
                        if (mins > 0) "${hours}h ${mins}m" else "${hours} hour${if (hours > 1) "s" else ""}"
                    }
                    else -> "${durationMinutes} minute${if (durationMinutes > 1) "s" else ""}"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Preset grid
        val chunkedPresets = presets.chunked(4)
        chunkedPresets.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (minutes, label) ->
                    FilterChip(
                        onClick = { onDurationChange(minutes) },
                        label = { Text(label) },
                        selected = durationMinutes == minutes,
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                // Fill remaining space if row has fewer than 4 items
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Custom duration input
        if (!presets.any { it.first == durationMinutes }) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom duration:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when {
                            durationMinutes >= 1440 -> "${durationMinutes / 1440} day${if (durationMinutes > 1440) "s" else ""}"
                            durationMinutes >= 60 -> {
                                val hours = durationMinutes / 60
                                val mins = durationMinutes % 60
                                if (mins > 0) "${hours}h ${mins}m" else "${hours} hour${if (hours > 1) "s" else ""}"
                            }
                            else -> "${durationMinutes} minute${if (durationMinutes > 1) "s" else ""}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleConfiguration(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    activeDays: String,
    onTimeChange: (Int, Int, Int, Int) -> Unit,
    onDaysChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Sleep Schedule",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Time selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Start time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sleep Time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* TODO: Open time picker */ },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%02d:%02d", startHour, startMinute),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // End time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wake Time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* TODO: Open time picker */ },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%02d:%02d", endHour, endMinute),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Days of week
        Text(
            text = "Active Days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { onDaysChange("1234567") },
                    label = { Text("Every day") },
                    selected = activeDays == "1234567",
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    onClick = { onDaysChange("23456") },
                    label = { Text("Weekdays") },
                    selected = activeDays == "23456",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { onDaysChange("17") },
                    label = { Text("Weekends") },
                    selected = activeDays == "17",
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    onClick = { /* TODO: Custom day picker */ },
                    label = { Text("Custom") },
                    selected = !listOf("1234567", "23456", "17").contains(activeDays),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Schedule preview
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Schedule Preview",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your phone will be bricked from ${String.format("%02d:%02d", startHour, startMinute)} to ${String.format("%02d:%02d", endHour, endMinute)} on ${formatActiveDaysReadable(activeDays)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatActiveDaysReadable(daysString: String): String {
    return when (daysString) {
        "1234567" -> "every day"
        "23456" -> "weekdays"
        "17" -> "weekends"
        else -> "selected days"
    }
}

@Composable
private fun EssentialAppsSelectionButton(
    selectedAppsCount: Int,
    onSelectApps: (Set<String>) -> Unit
) {
    var showAppSelector by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = "Essential Apps (Optional)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Select apps that will remain accessible during this session",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { showAppSelector = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedAppsCount > 0) {
                    "$selectedAppsCount apps selected"
                } else {
                    "Select essential apps"
                }
            )
        }
    }
    
    if (showAppSelector) {
        EssentialAppSelectorDialog(
            onDismiss = { showAppSelector = false },
            onAppsSelected = { selectedApps ->
                onSelectApps(selectedApps)
                showAppSelector = false
            }
        )
    }
}

@Composable
private fun EssentialAppsGrid(
    selectedApps: Set<String>,
    onAppsChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    
    // Load installed apps
    LaunchedEffect(Unit) {
        try {
            installedApps = loadInstalledApps(context)
        } catch (e: Exception) {
            // Log error but don't crash
        } finally {
            isLoading = false
        }
    }
    
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter { app ->
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Column(modifier = modifier) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search apps") },
            leadingIcon = { 
                Icon(Icons.Default.Search, contentDescription = "Search") 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedApps.isNotEmpty()) {
            Text(
                text = "${selectedApps.size} apps selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredApps) { app ->
                    AppSelectionItem(
                        app = app,
                        isSelected = selectedApps.contains(app.packageName),
                        onSelectionChange = { isSelected ->
                            if (isSelected) {
                                onAppsChanged(selectedApps + app.packageName)
                            } else {
                                onAppsChanged(selectedApps - app.packageName)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSelectionItem(
    app: AppInfo,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChange(!isSelected) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}



private fun loadInstalledApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    
    return installedPackages
        .filter { appInfo ->
            try {
                // Filter apps that have a launch intent (user-facing apps)
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                launchIntent != null
            } catch (e: Exception) {
                false
            }
        }
        .mapNotNull { appInfo ->
            try {
                AppInfo(
                    name = appInfo.loadLabel(packageManager).toString(),
                    packageName = appInfo.packageName
                )
            } catch (e: Exception) {
                null
            }
        }
        .sortedBy { it.name.lowercase() }
}

// Keep the original formatActiveDays function for backward compatibility in PhoneBrickScreen
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