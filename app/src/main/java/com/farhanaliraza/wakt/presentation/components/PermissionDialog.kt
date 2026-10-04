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

/**
 * Setup dialog for the standard, accessibility-free configuration:
 * Usage Access (which app is open) + Display over other apps (the lock screen).
 * The accessibility service is offered as an optional upgrade only.
 */
@Composable
fun PermissionDialog(
    missingPermissions: List<String>,
    context: Context,
    onDismiss: () -> Unit
) {
    val needsUsageAccess = missingPermissions.contains(PermissionHelper.PERMISSION_USAGE_ACCESS)
    val needsOverlay = missingPermissions.contains(PermissionHelper.PERMISSION_OVERLAY)
    val needsBatteryOptimization = !PermissionHelper.isBatteryOptimizationDisabled(context)
    val isAggressiveOem = PermissionHelper.isAggressiveBatteryOem()
    val oemInstructions = PermissionHelper.getOemBatteryInstructions()
    val popupInstructions = PermissionHelper.getBackgroundPopupInstructions()
    val hasOemSettings = PermissionHelper.getOemBatterySettingsIntent(context) != null
    val accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)

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

                if (needsUsageAccess) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "• Usage Access",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Used to detect which app is open so blocked apps and focus sessions can be enforced. No usage data leaves your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (needsOverlay) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "• Display over other apps",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Used to show the lock screen and the challenge screen on top of a blocked app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (popupInstructions != null) {
                            Text(
                                text = "Your device may also need:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = popupInstructions,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                if (!accessibilityEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Optional: Accessibility Service",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Reacts instantly and adds website blocking inside browsers. Not needed if the two permissions above are granted. Note: many banking apps refuse to run while any accessibility service is enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                if (needsUsageAccess) {
                    Button(
                        onClick = { PermissionHelper.requestUsageAccessPermission(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Usage Access")
                    }
                }

                if (needsOverlay) {
                    Button(
                        onClick = { PermissionHelper.requestOverlayPermission(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow Display Over Apps")
                    }
                }

                if (needsBatteryOptimization) {
                    OutlinedButton(
                        onClick = { PermissionHelper.requestBatteryOptimizationExemption(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization")
                    }

                    if (hasOemSettings) {
                        TextButton(
                            onClick = { PermissionHelper.openOemBatterySettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Device Battery Settings")
                        }
                    }
                }

                if (!accessibilityEnabled) {
                    TextButton(
                        onClick = { PermissionHelper.requestAccessibilityPermission(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Accessibility (optional)")
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
