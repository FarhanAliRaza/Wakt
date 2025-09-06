package com.example.wakt.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.wakt.presentation.ui.theme.WaktGradient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakt.data.database.entity.BlockedItem
import com.example.wakt.utils.PermissionHelper
import com.example.wakt.utils.DeviceAdminManager
import androidx.compose.material.icons.filled.Lock
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddBlock: () -> Unit,
    onNavigateToGoals: () -> Unit = {},
    // VPN permission request removed for battery optimization
    // onVpnPermissionRequest: ((callback: () -> Unit) -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(PermissionHelper.areAllPermissionsGranted(context)) }
    var missingPermissions by remember { mutableStateOf(PermissionHelper.getMissingPermissions(context)) }
    
    // Device Admin state
    var isProtectionEnabled by remember { mutableStateOf(PermissionHelper.isDeviceAdminEnabled(context)) }
    
    // Function to refresh permission status
    val refreshPermissions = {
        permissionsGranted = PermissionHelper.areAllPermissionsGranted(context)
        missingPermissions = PermissionHelper.getMissingPermissions(context)
        isProtectionEnabled = PermissionHelper.isDeviceAdminEnabled(context)
    }
    
    // Refresh permissions when app comes to foreground
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
    
    // Initial permission check
    LaunchedEffect(Unit) {
        refreshPermissions()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wakt") },
                actions = {
                    // Refresh permissions button (for debugging)
                    IconButton(onClick = refreshPermissions) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Permissions"
                        )
                    }
                    
                    if (!permissionsGranted) {
                        IconButton(onClick = { showPermissionDialog = true }) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Permissions Required",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddBlock
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Block")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Permission warning banner
            if (!permissionsGranted) {
                PermissionWarningBanner(
                    missingPermissions = missingPermissions,
                    onRequestPermissions = { showPermissionDialog = true }
                )
            }
            
            // Long-term Goals banner button
            LongTermGoalsBanner(
                onClick = onNavigateToGoals,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Simple uninstall protection checkbox
            UninstallProtectionCheckbox(
                isEnabled = isProtectionEnabled,
                onToggle = { enabled ->
                    if (enabled && !isProtectionEnabled) {
                        // Request device admin permission
                        val intent = viewModel.deviceAdminManager.createEnableDeviceAdminIntent()
                        context.startActivity(intent)
                    } else if (!enabled && isProtectionEnabled) {
                        // User wants to disable - this will be handled by the device admin system
                        Toast.makeText(context, "Go to Settings > Security > Device Administrators to disable protection", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Main content
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.blockedItems.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    BlockedItemsList(
                        items = uiState.blockedItems,
                        onDeleteItem = { viewModel.deleteBlockedItem(it) }
                    )
                }
            }
        }
    }
    
    // Permission dialog
    if (showPermissionDialog) {
        PermissionDialog(
            missingPermissions = missingPermissions,
            onDismiss = { showPermissionDialog = false },
            onRequestPermissions = { permission ->
                when (permission) {
                    "Accessibility Service" -> PermissionHelper.requestAccessibilityPermission(context)
                    "Display over other apps" -> PermissionHelper.requestOverlayPermission(context)
                    // VPN Service permission handling removed for battery optimization
                    /*
                    "VPN Service" -> {
                        if (onVpnPermissionRequest != null) {
                            onVpnPermissionRequest.invoke {
                                // Callback executed when VPN permission is granted
                                refreshPermissions()
                            }
                        } else {
                            // Fallback: open VPN settings manually
                            PermissionHelper.requestOverlayPermission(context)
                        }
                    }
                    */
                }
                showPermissionDialog = false
            }
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
fun BlockedItemsList(
    items: List<BlockedItem>,
    onDeleteItem: (BlockedItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedItemCard(
    item: BlockedItem,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* TODO: Show details */ }
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
                    text = "${item.type} • ${item.challengeType}",
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
fun PermissionWarningBanner(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
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
                    text = "App blocking requires: ${missingPermissions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            TextButton(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .background(
                        brush = WaktGradient,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    "Grant",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PermissionDialog(
    missingPermissions: List<String>,
    onDismiss: () -> Unit,
    onRequestPermissions: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Wakt needs the following permissions to block apps effectively:")
                
                missingPermissions.forEach { permission ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = permission,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = when (permission) {
                                    "Accessibility Service" -> "Monitor when apps are launched and trigger blocking"
                                    "Display over other apps" -> "Show blocking screen over blocked apps"
                                    // VPN Service description removed for battery optimization
                                    // "VPN Service" -> "Intercept network traffic to block websites at DNS level"
                                    else -> "Required for app functionality"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            missingPermissions.forEach { permission ->
                TextButton(
                    onClick = { onRequestPermissions(permission) },
                    modifier = Modifier
                        .background(
                            brush = WaktGradient,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("Grant $permission", color = Color.White)
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

@Composable
fun LongTermGoalsBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = WaktGradient,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Long-term Goals",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Set unbreakable commitments • Build lasting habits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Open Long-term Goals",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun UninstallProtectionCheckbox(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Protect from uninstallation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Make it harder to uninstall this app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}