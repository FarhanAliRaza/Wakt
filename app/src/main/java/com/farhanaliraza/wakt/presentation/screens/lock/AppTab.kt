package com.farhanaliraza.wakt.presentation.screens.lock

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.farhanaliraza.wakt.data.database.entity.BlockType
import com.farhanaliraza.wakt.data.database.entity.BlockedItem
import com.farhanaliraza.wakt.presentation.components.PermissionWarningBanner
import com.farhanaliraza.wakt.services.WebsiteBlockingVpnService
import kotlinx.coroutines.delay

@Composable
fun AppTab(
    viewModel: LockViewModel,
    onNavigateToAddBlock: () -> Unit,
    onNavigateToDnsLog: () -> Unit = {},
    permissionsGranted: Boolean = true,
    missingPermissions: List<String> = emptyList(),
    onRequestPermissions: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Website blocks are enforced by the DNS-filtering VPN, which needs one-time
    // consent. VpnService.prepare() returns null once consent has been granted.
    val now = System.currentTimeMillis()
    val hasWebsiteBlocks = uiState.blockedItems.any {
        it.type == BlockType.WEBSITE && (it.blockEndTime == null || it.blockEndTime!! > now)
    }
    var vpnConsentNeeded by remember { mutableStateOf(VpnService.prepare(context) != null) }
    val refreshVpnState = {
        vpnConsentNeeded = VpnService.prepare(context) != null
        viewModel.refreshServices()
    }
    val vpnConsentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshVpnState()
        }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshVpnState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Permission warning banner
            if (!permissionsGranted) {
                PermissionWarningBanner(
                    missingPermissions = missingPermissions,
                    onRequestPermissions = onRequestPermissions
                )
            }

            if (hasWebsiteBlocks) {
                if (vpnConsentNeeded) {
                    VpnConsentBanner(
                        onGrant = {
                            val consentIntent = VpnService.prepare(context)
                            if (consentIntent != null) {
                                vpnConsentLauncher.launch(consentIntent)
                            } else {
                                refreshVpnState()
                            }
                        }
                    )
                } else {
                    WebsiteFilterStatus(
                        onStart = { viewModel.refreshServices() },
                        onOpenLog = onNavigateToDnsLog
                    )
                }
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
}

@Composable
private fun VpnConsentBanner(onGrant: () -> Unit) {
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
                    text = "VPN Permission Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Website blocks are inactive until Wakt's local DNS filter is allowed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Button(
                onClick = onGrant,
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
 * Live status of the DNS website filter. "Lookups seen" staying at zero while
 * browsing means DNS is bypassing the filter (e.g. a strict Private DNS setting).
 */
@Composable
private fun WebsiteFilterStatus(onStart: () -> Unit, onOpenLog: () -> Unit) {
    var running by remember { mutableStateOf(WebsiteBlockingVpnService.isServiceRunning) }
    var seen by remember { mutableIntStateOf(WebsiteBlockingVpnService.queriesSeen.get()) }
    var blocked by remember { mutableIntStateOf(WebsiteBlockingVpnService.queriesBlocked.get()) }

    LaunchedEffect(Unit) {
        while (true) {
            running = WebsiteBlockingVpnService.isServiceRunning
            seen = WebsiteBlockingVpnService.queriesSeen.get()
            blocked = WebsiteBlockingVpnService.queriesBlocked.get()
            delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { onOpenLog() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (running) "Website filter: active" else "Website filter: not running",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (running) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "$seen DNS lookups seen, $blocked blocked. Tap to view the log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!running) {
                TextButton(onClick = onStart) { Text("Start") }
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

