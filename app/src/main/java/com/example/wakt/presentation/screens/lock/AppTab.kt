package com.example.wakt.presentation.screens.lock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakt.data.database.entity.BlockedItem
import com.example.wakt.utils.PermissionHelper

@Composable
fun AppTab(
    viewModel: LockViewModel,
    onNavigateToAddBlock: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(PermissionHelper.areAllPermissionsGranted(context)) }
    var missingPermissions by remember { mutableStateOf(PermissionHelper.getMissingPermissions(context)) }

    val refreshPermissions = {
        permissionsGranted = PermissionHelper.areAllPermissionsGranted(context)
        missingPermissions = PermissionHelper.getMissingPermissions(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Permission warning banner
            if (!permissionsGranted) {
                PermissionWarningBanner(
                    missingPermissions = missingPermissions,
                    onRequestPermissions = { showPermissionDialog = true },
                    onRefresh = refreshPermissions
                )
            }

            // Main content
            if (uiState.blockedItems.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                BlockedItemsList(
                    items = uiState.blockedItems,
                    onDeleteItem = { viewModel.deleteBlockedItem(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // FAB for adding blocks
        FloatingActionButton(
            onClick = onNavigateToAddBlock,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Block")
        }
    }

    // Permission dialog
    if (showPermissionDialog) {
        PermissionDialog(
            missingPermissions = missingPermissions,
            onDismiss = { showPermissionDialog = false },
            onRequestPermission = { permission ->
                when (permission) {
                    "Accessibility Service" -> PermissionHelper.requestAccessibilityPermission(context)
                    "Display over other apps" -> PermissionHelper.requestOverlayPermission(context)
                }
                showPermissionDialog = false
            }
        )
    }
}

@Composable
private fun PermissionWarningBanner(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit
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

            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onErrorContainer
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
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Apps or Websites Blocked",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the + button to add apps or websites to block",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BlockedItemsList(
    items: List<BlockedItem>,
    onDeleteItem: (BlockedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = items,
            key = { item -> item.id }
        ) { item ->
            BlockedItemCard(
                item = item,
                onDelete = { onDeleteItem(item) }
            )
        }
    }
}

@Composable
private fun BlockedItemCard(
    item: BlockedItem,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${item.type} - ${item.challengeType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
        }
    }
}

@Composable
private fun PermissionDialog(
    missingPermissions: List<String>,
    onDismiss: () -> Unit,
    onRequestPermission: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Wakt needs the following permissions to block apps:")

                missingPermissions.forEach { permission ->
                    Column {
                        Text(
                            text = permission,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = when (permission) {
                                "Accessibility Service" -> "Monitor when apps are launched"
                                "Display over other apps" -> "Show blocking screen over apps"
                                else -> "Required for functionality"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column {
                missingPermissions.forEach { permission ->
                    Button(
                        onClick = { onRequestPermission(permission) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant $permission")
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
