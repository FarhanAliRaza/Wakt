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

@Composable
fun LockSessionDialog(
    sessionName: String,
    onConfirm: (durationDays: Int, commitmentPhrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    var durationDays by remember { mutableIntStateOf(7) }
    var commitmentPhrase by remember { mutableStateOf("") }
    val isValid = commitmentPhrase.trim().length >= 10

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
        title = {
            Text(
                text = "Lock Schedule",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Locking prevents editing or deleting this schedule for the selected duration.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Duration selector
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
                            onValueChange = { durationDays = it.toInt() },
                            valueRange = 1f..90f,
                            steps = 88,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "$durationDays days",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(70.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Quick select buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(7, 14, 30, 90).forEach { days ->
                        FilterChip(
                            selected = durationDays == days,
                            onClick = { durationDays = days },
                            label = { Text("${days}d") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // Commitment phrase input
                Column {
                    Text(
                        text = "Unlock Phrase",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter a phrase you must type exactly to unlock early",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commitmentPhrase,
                        onValueChange = { commitmentPhrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., I am breaking my commitment") },
                        singleLine = false,
                        minLines = 2,
                        supportingText = {
                            Text(
                                text = if (commitmentPhrase.trim().isEmpty()) {
                                    "Minimum 10 characters"
                                } else if (commitmentPhrase.trim().length < 10) {
                                    "${commitmentPhrase.trim().length}/10 characters"
                                } else {
                                    "${commitmentPhrase.trim().length} characters"
                                },
                                color = if (isValid)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                // Warning
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "You must type this phrase exactly (case-sensitive) to unlock early.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(durationDays, commitmentPhrase.trim()) },
                enabled = isValid
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lock for $durationDays days")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
