package com.example.wakt.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.ui.components.MultiItemSelector
import com.example.wakt.ui.components.SelectedItem
import com.example.wakt.ui.components.createWebsiteSelectedItem
import com.example.wakt.ui.components.toSelectedItem
import com.example.wakt.presentation.screens.addblock.AppSelectionDialog
import com.example.wakt.presentation.ui.theme.WaktGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, List<Pair<String, BlockType>>, Int, ChallengeType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedItems by remember { mutableStateOf(listOf<SelectedItem>()) }
    var durationDays by remember { mutableStateOf("30") }
    var challengeType by remember { mutableStateOf(ChallengeType.WAIT) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showAppSelector by remember { mutableStateOf(false) }
    var showWebsiteInput by remember { mutableStateOf(false) }
    
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
                    "Create Long-term Blocking Goal",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Text(
                    "⚠️ Warning: Once created, this goal is a permanent commitment! You can only ADD more apps/websites but can never remove items or cancel the goal until it expires.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        showError = false
                        errorMessage = ""
                    },
                    label = { Text("Goal Name") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., No Social Media for 30 Days") },
                    singleLine = true
                )
                
                // Multi-item selector
                MultiItemSelector(
                    selectedItems = selectedItems,
                    onAddApp = { showAppSelector = true },
                    onAddWebsite = { showWebsiteInput = true },
                    onRemoveItem = { item ->
                        selectedItems = selectedItems.filter { it != item }
                    },
                    title = "Apps & Websites to Block"
                )
                
                OutlinedTextField(
                    value = durationDays,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() }) {
                            durationDays = it
                            showError = false
                            errorMessage = ""
                        }
                    },
                    label = { Text("Duration (days)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("1 to 90 days") },
                    isError = showError,
                    singleLine = true
                )
                
                // Error message
                if (showError && errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Text(
                    "Challenge Type",
                    style = MaterialTheme.typography.labelLarge
                )
                
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = challengeType == ChallengeType.WAIT,
                            onClick = { challengeType = ChallengeType.WAIT }
                        )
                        Text("Wait Timer (10-30 minutes)")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = challengeType == ChallengeType.CLICK_500,
                            onClick = { challengeType = ChallengeType.CLICK_500 }
                        )
                        Text("Click 500 Times")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val days = durationDays.toIntOrNull() ?: 0
                            when {
                                name.isBlank() -> {
                                    showError = true
                                    errorMessage = "Please enter a goal name"
                                }
                                selectedItems.isEmpty() -> {
                                    showError = true
                                    errorMessage = "Please select at least one app or website to block"
                                }
                                days !in 1..90 -> {
                                    showError = true
                                    errorMessage = "Duration must be between 1 and 90 days"
                                }
                                else -> {
                                    val items = selectedItems.map { 
                                        it.packageOrUrl to it.type 
                                    }
                                    onConfirm(name, items, days, challengeType)
                                }
                            }
                        },
                        modifier = Modifier.background(
                            brush = WaktGradient,
                            shape = RoundedCornerShape(8.dp)
                        ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Create Goal", color = Color.White)
                    }
                }
            }
        }
    }
    
    // App Selection Dialog
    if (showAppSelector) {
        AppSelectionDialog(
            onDismiss = { showAppSelector = false },
            onAppsSelected = { apps ->
                val newItems = apps.map { it.toSelectedItem() }
                selectedItems = selectedItems + newItems.filter { newItem ->
                    selectedItems.none { existing -> existing.packageOrUrl == newItem.packageOrUrl }
                }
                showAppSelector = false
            }
        )
    }
    
    // Website Input Dialog
    if (showWebsiteInput) {
        WebsiteInputDialog(
            onDismiss = { showWebsiteInput = false },
            onWebsiteAdded = { url ->
                val websiteItem = createWebsiteSelectedItem(url)
                if (selectedItems.none { it.packageOrUrl == websiteItem.packageOrUrl }) {
                    selectedItems = selectedItems + websiteItem
                }
                showWebsiteInput = false
            }
        )
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
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (websiteUrl.isNotBlank()) {
                                onWebsiteAdded(websiteUrl.trim())
                            } else {
                                showError = true
                            }
                        },
                        modifier = Modifier.background(
                            brush = WaktGradient,
                            shape = RoundedCornerShape(8.dp)
                        ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Add", color = Color.White)
                    }
                }
            }
        }
    }
}