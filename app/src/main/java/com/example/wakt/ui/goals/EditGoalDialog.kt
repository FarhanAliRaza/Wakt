package com.example.wakt.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.GoalBlock
import com.example.wakt.data.database.entity.GoalBlockItem
import com.example.wakt.ui.components.MultiItemSelector
import com.example.wakt.ui.components.SelectedItem
import com.example.wakt.ui.components.createWebsiteSelectedItem
import com.example.wakt.presentation.screens.addblock.AppSelectionDialog
import com.example.wakt.presentation.ui.theme.WaktGradient
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGoalDialog(
    goal: GoalBlock,
    onDismiss: () -> Unit,
    onGoalUpdated: () -> Unit,
    viewModel: EditGoalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    LaunchedEffect(goal.id) {
        viewModel.loadGoal(goal.id)
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Edit Goal: ${goal.name}",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                // Goal info (read-only)
                GoalInfoCard(goal = goal)
                
                // Current items
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    MultiItemSelector(
                        selectedItems = uiState.currentItems.map { it.toSelectedItem() },
                        onAddApp = { viewModel.showAppSelector() },
                        onAddWebsite = { viewModel.showWebsiteInput() },
                        onRemoveItem = { /* No-op: Cannot remove items from long-term goals */ },
                        title = "Currently Blocked Items (${uiState.currentItems.size})",
                        showRemoveButtons = false // Hide remove buttons for goal items
                    )
                }
                
                // Error message
                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = {
                            onGoalUpdated()
                            onDismiss()
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
    
    // App Selection Dialog
    if (uiState.showAppSelector) {
        AppSelectionDialog(
            onDismiss = { viewModel.hideAppSelector() },
            onAppsSelected = { apps ->
                viewModel.addAppsToGoal(apps)
                viewModel.hideAppSelector()
            }
        )
    }
    
    // Website Input Dialog
    if (uiState.showWebsiteInput) {
        WebsiteInputDialog(
            onDismiss = { viewModel.hideWebsiteInput() },
            onWebsiteAdded = { url ->
                viewModel.addWebsiteToGoal(url)
                viewModel.hideWebsiteInput()
            }
        )
    }
}

@Composable
private fun GoalInfoCard(goal: GoalBlock) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val remainingDays = remember(goal) {
        val now = System.currentTimeMillis()
        val remaining = goal.goalEndTime - now
        if (remaining <= 0) 0 else (remaining / (24 * 60 * 60 * 1000)).toInt() + 1
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Goal Information",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duration:", style = MaterialTheme.typography.bodyMedium)
                Text("${goal.goalDurationDays} days", style = MaterialTheme.typography.bodyMedium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Started:", style = MaterialTheme.typography.bodyMedium)
                Text(dateFormatter.format(Date(goal.goalStartTime)), style = MaterialTheme.typography.bodyMedium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ends:", style = MaterialTheme.typography.bodyMedium)
                Text(dateFormatter.format(Date(goal.goalEndTime)), style = MaterialTheme.typography.bodyMedium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Remaining:", style = MaterialTheme.typography.bodyMedium)
                Text("$remainingDays days", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                "⚠️ Note: You can only ADD more apps/websites to make this goal stricter. Items cannot be removed once added, and goal duration/challenge type cannot be changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WebsiteInputDialog(
    onDismiss: () -> Unit,
    onWebsiteAdded: (String) -> Unit
) {
    var websiteUrl by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add Website",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { 
                        websiteUrl = it
                        showError = false
                    },
                    label = { Text("Website URL") },
                    placeholder = { Text("e.g., facebook.com") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError,
                    singleLine = true
                )
                
                if (showError) {
                    Text(
                        "Please enter a valid website URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (websiteUrl.isNotBlank()) {
                                onWebsiteAdded(websiteUrl.trim())
                            } else {
                                showError = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

// Extension function to convert GoalBlockItem to SelectedItem
private fun GoalBlockItem.toSelectedItem(): SelectedItem {
    return SelectedItem(
        name = itemName,
        type = itemType,
        packageOrUrl = packageOrUrl,
        icon = null // We could load app icons here if needed
    )
}