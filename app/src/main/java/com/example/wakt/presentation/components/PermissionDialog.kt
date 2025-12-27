package com.example.wakt.presentation.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wakt.utils.PermissionHelper

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
    val needsOverlay = missingPermissions.contains("Display over other apps")
    val needsUsageAccess = missingPermissions.contains("Usage Access")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Wakt needs the following permissions:")

                if (needsAccessibility) {
                    Text(
                        text = "• Accessibility Service - Monitor app launches",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (needsOverlay) {
                    Text(
                        text = "• Display over other apps - Show blocking screen",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (needsUsageAccess) {
                    Text(
                        text = "• Usage Access - Detect foreground app",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                            onDismiss()
                        }
                    ) {
                        Text("Grant Accessibility")
                    }
                }
                if (needsOverlay) {
                    Button(
                        onClick = {
                            PermissionHelper.requestOverlayPermission(context)
                            onDismiss()
                        }
                    ) {
                        Text("Grant Overlay")
                    }
                }
                if (needsUsageAccess) {
                    Button(
                        onClick = {
                            PermissionHelper.requestUsageAccessPermission(context)
                            onDismiss()
                        }
                    ) {
                        Text("Grant Usage Access")
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
