package com.farhanaliraza.wakt.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farhanaliraza.wakt.utils.FilterPauseManager

/**
 * Pause the website filter either plainly for a few minutes, or for longer
 * with the phone locked to one chosen app. The lock variant needs an app pick;
 * the plain variant just needs a duration.
 */
@Composable
fun FilterPauseDialog(
    apps: List<FilterPauseManager.PausableApp>,
    loading: Boolean,
    onConfirmPlain: (minutes: Int) -> Unit,
    onConfirmLock: (packageName: String, minutes: Int) -> Unit,
    onDismiss: () -> Unit,
    initialLockMode: Boolean = false,
    initialTargetPackage: String? = null,
    initialPlainMinutes: Int = 2,
    initialLockMinutes: Int = 5
) {
    var lockMode by remember { mutableStateOf(initialLockMode) }
    var query by remember { mutableStateOf("") }
    // Remembered as a package name so the last pick survives the async app list load
    var selectedPackage by remember { mutableStateOf(initialTargetPackage) }
    var plainMinutes by remember {
        mutableIntStateOf(initialPlainMinutes.takeIf { it in FilterPauseManager.PLAIN_DURATION_OPTIONS } ?: 2)
    }
    var lockMinutes by remember {
        mutableIntStateOf(initialLockMinutes.takeIf { it in FilterPauseManager.LOCK_DURATION_OPTIONS } ?: 5)
    }
    val selected = apps.firstOrNull { it.packageName == selectedPackage }

    val filtered = remember(apps, query, selectedPackage) {
        val q = query.trim().lowercase()
        val matches = if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) || it.packageName.contains(q) }
        // Keep the remembered app at the top so it is visible without scrolling
        val pinned = matches.firstOrNull { it.packageName == selectedPackage }
        if (pinned == null) matches else listOf(pinned) + matches.filter { it.packageName != pinned.packageName }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause website filter") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "For banking apps that refuse to run while a VPN is on. The filter comes back on its own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !lockMode,
                        onClick = { lockMode = false },
                        label = { Text("Just pause") }
                    )
                    FilterChip(
                        selected = lockMode,
                        onClick = { lockMode = true },
                        label = { Text("Pause and lock to an app") }
                    )
                }

                if (!lockMode) {
                    Text(
                        text = "Up to ${FilterPauseManager.MAX_PLAIN_MINUTES} minutes, nothing else changes. " +
                            "Short on purpose: enough for a payment, not enough to be worth abusing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Duration", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPauseManager.PLAIN_DURATION_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = plainMinutes == option,
                                onClick = { plainMinutes = option },
                                label = { Text("$option") }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Up to ${FilterPauseManager.MAX_LOCK_MINUTES} minutes. Your phone locks to the one app " +
                            "you pick; leaving the lock early brings the filter back at once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Duration", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterPauseManager.LOCK_DURATION_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = lockMinutes == option,
                                onClick = { lockMinutes = option },
                                label = { Text("$option") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search apps") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )

                    if (loading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    } else if (filtered.isEmpty()) {
                        Text(
                            text = "No app matches. Browsers and blocked apps are never offered here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                val isSelected = selectedPackage == app.packageName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPackage = app.packageName }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSelected, onClick = { selectedPackage = app.packageName })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                    }
                }
            }
        },
        confirmButton = {
            if (!lockMode) {
                Button(onClick = { onConfirmPlain(plainMinutes) }) {
                    Text("Pause for $plainMinutes min")
                }
            } else {
                Button(
                    onClick = { selected?.let { onConfirmLock(it.packageName, lockMinutes) } },
                    enabled = selected != null
                ) {
                    Text(selected?.let { "Lock to ${it.label} for $lockMinutes min" } ?: "Pick an app")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
