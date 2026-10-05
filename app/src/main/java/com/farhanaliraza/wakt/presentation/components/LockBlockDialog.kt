package com.farhanaliraza.wakt.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Commitment lock for one or more blocks: pick a duration up to a year and
 * decide whether an early unlock is possible at all. With a phrase, an early
 * unlock still needs a 24-hour wait and the exact phrase; without one, the
 * block simply cannot be removed until the lock expires.
 */
@Composable
fun LockBlockDialog(
    title: String,
    itemCount: Int,
    onConfirm: (durationDays: Int, commitmentPhrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    var durationDays by remember { mutableIntStateOf(30) }
    var allowEarlyUnlock by remember { mutableStateOf(true) }
    var commitmentPhrase by remember { mutableStateOf("") }
    val phraseValid = !allowEarlyUnlock || commitmentPhrase.trim().length >= 10

    val durationLabel = when {
        durationDays >= 365 -> "1 year"
        durationDays % 30 == 0 -> "${durationDays / 30} month${if (durationDays > 30) "s" else ""}"
        else -> "$durationDays days"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (itemCount == 1) {
                        "While locked, this block cannot be deleted and its unlock challenge is disabled."
                    } else {
                        "While locked, these $itemCount blocks cannot be deleted and their unlock challenges are disabled."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                Column {
                    Text(
                        text = "Lock Duration",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = durationDays.toFloat(),
                            onValueChange = { durationDays = it.toInt().coerceIn(1, 365) },
                            valueRange = 1f..365f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = durationLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30 to "1m", 90 to "3m", 180 to "6m", 365 to "1y").forEach { (days, label) ->
                        FilterChip(
                            selected = durationDays == days,
                            onClick = { durationDays = days },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow early unlock",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Requires a 24-hour wait, then typing the phrase below exactly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowEarlyUnlock,
                        onCheckedChange = { allowEarlyUnlock = it }
                    )
                }

                if (allowEarlyUnlock) {
                    OutlinedTextField(
                        value = commitmentPhrase,
                        onValueChange = { commitmentPhrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Unlock phrase") },
                        placeholder = { Text("e.g., I am breaking the promise I made to myself") },
                        singleLine = false,
                        minLines = 2,
                        supportingText = {
                            Text(
                                text = if (commitmentPhrase.trim().length < 10) {
                                    "${commitmentPhrase.trim().length}/10 characters minimum"
                                } else {
                                    "${commitmentPhrase.trim().length} characters, case-sensitive"
                                }
                            )
                        }
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "No way out: this lock only ends when the time is over. Short of uninstalling Wakt, nothing can remove these blocks before then.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(durationDays, if (allowEarlyUnlock) commitmentPhrase.trim() else "")
                },
                enabled = phraseValid
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lock for $durationLabel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
