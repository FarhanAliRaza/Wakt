package com.farhanaliraza.wakt.presentation.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farhanaliraza.wakt.utils.PermissionHelper

@Composable
fun PermissionWarningBanner(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = missingPermissions.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
fun PermissionDialog(
    missingPermissions: List<String>,
    context: Context,
    onDismiss: () -> Unit
) {
    val needsAccessibility = missingPermissions.contains("Accessibility Service")
    val needsBatteryOptimization = !PermissionHelper.isBatteryOptimizationDisabled(context)
    val isAggressiveOem = PermissionHelper.isAggressiveBatteryOem()
    val oemInstructions = PermissionHelper.getOemBatteryInstructions()
    val hasOemSettings = PermissionHelper.getOemBatterySettingsIntent(context) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setup Required") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Wakt needs the following to work:")

                if (needsAccessibility) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "• Accessibility Service",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Used to detect which app is running, block distracting apps, and show the lock overlay. No personal data is collected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (needsBatteryOptimization) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "• Disable battery optimization",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Required so Android doesn't stop the app from blocking in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isAggressiveOem) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your device may also need:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = oemInstructions,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• View installed apps",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Used to show you a list of installed apps so you can select which ones to block.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• Background service",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Used to keep focus sessions running and enforce app blocking even when the app is in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "All data stays on your device. Nothing is sent to any server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (needsAccessibility) {
                    Button(
                        onClick = {
                            PermissionHelper.requestAccessibilityPermission(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Accessibility")
                    }
                }

                if (needsBatteryOptimization) {
                    OutlinedButton(
                        onClick = {
                            PermissionHelper.requestBatteryOptimizationExemption(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization")
                    }

                    if (hasOemSettings) {
                        TextButton(
                            onClick = {
                                PermissionHelper.openOemBatterySettings(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Device Battery Settings")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
