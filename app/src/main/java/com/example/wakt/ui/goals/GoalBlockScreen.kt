package com.example.wakt.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.presentation.ui.theme.WaktGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalBlockScreen(
    viewModel: GoalBlockViewModel = hiltViewModel()
) {
    val goals by viewModel.activeGoals.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var goalToEdit by remember { mutableStateOf<com.example.wakt.data.database.entity.GoalBlock?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Long-term Blocking Goals") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Goal")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Goal-based Blocking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Once set, goals cannot be removed until completion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            if (goals.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active goals. Tap + to create one.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                items(goals) { goal ->
                    GoalCard(
                        goal = goal,
                        onAttemptDelete = {
                            scope.launch {
                                val deleted = viewModel.tryDeleteGoal(goal.id)
                                if (!deleted) {
                                    // Show snackbar or toast that goal cannot be deleted
                                }
                            }
                        },
                        onEdit = {
                            goalToEdit = goal
                            showEditDialog = true
                        }
                    )
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, items, days, challengeType ->
                scope.launch {
                    viewModel.createGoalWithMultipleItems(name, items, days, challengeType)
                    showAddDialog = false
                }
            }
        )
    }
    
    if (showEditDialog && goalToEdit != null) {
        EditGoalDialog(
            goal = goalToEdit!!,
            onDismiss = { 
                showEditDialog = false
                goalToEdit = null
            },
            onGoalUpdated = {
                // Goal has been updated, refresh the list if needed
                showEditDialog = false
                goalToEdit = null
            }
        )
    }
}

@Composable
fun GoalCard(
    goal: com.example.wakt.data.database.entity.GoalBlock,
    onAttemptDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val remainingDays = remember(goal) {
        val now = System.currentTimeMillis()
        val remaining = goal.goalEndTime - now
        if (remaining <= 0) 0 else (remaining / (24 * 60 * 60 * 1000)).toInt() + 1
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = if (goal.isActive) WaktGradient else androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { if (goal.isActive) onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (goal.isActive) Color.White else MaterialTheme.colorScheme.onSurface
                    )

                    
                    if (goal.isActive) {
                        Text(
                            "Click to edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!goal.isActive) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Start: ${dateFormatter.format(Date(goal.goalStartTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (goal.isActive) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "End: ${dateFormatter.format(Date(goal.goalEndTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (goal.isActive) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (goal.isActive) {
                    Text(
                        "$remainingDays days remaining",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (!goal.isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onAttemptDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove Completed Goal")
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "⚠️ Cannot be removed until goal period ends",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}