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
import androidx.compose.material.icons.filled.Lock
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
import com.farhanaliraza.wakt.presentation.components.FilterPauseDialog
import com.farhanaliraza.wakt.presentation.components.LockBlockDialog
import com.farhanaliraza.wakt.presentation.components.PermissionWarningBanner
import com.farhanaliraza.wakt.presentation.components.PinDialog
import com.farhanaliraza.wakt.presentation.components.UnlockSessionDialog
import com.farhanaliraza.wakt.services.WebsiteBlockingVpnService
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val sitesPinSet by viewModel.sitesPinSet.collectAsStateWithLifecycle()
    val sitesRevealed by viewModel.sitesRevealed.collectAsStateWithLifecycle()
    val sitesHidden = sitesPinSet && !sitesRevealed
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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
            // Leaving the app re-hides the private site list
            if (event == Lifecycle.Event.ON_STOP) viewModel.hideSites()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dialog state
    var lockTargets by remember { mutableStateOf<List<BlockedItem>?>(null) }
    var unlockTarget by remember { mutableStateOf<BlockedItem?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val vpnPause by viewModel.vpnPause.collectAsStateWithLifecycle()
    val pausableApps by viewModel.pausableApps.collectAsStateWithLifecycle()
    val pausableAppsLoading by viewModel.pausableAppsLoading.collectAsStateWithLifecycle()

    val unlockedItems = uiState.blockedItems.filter { !it.isCommitmentLocked() }

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
                        pause = vpnPause,
                        onStart = { viewModel.refreshServices() },
                        onOpenLog = onNavigateToDnsLog,
                        onPause = {
                            viewModel.loadPausableApps()
                            showPauseDialog = true
                        },
                        onResume = { viewModel.resumeFilterNow() }
                    )
                }
            }

            // Toolbar row: reveal hidden sites / lock everything
            if (uiState.blockedItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sitesHidden) {
                        TextButton(onClick = { showPinDialog = true }) { Text("Reveal sites") }
                    } else if (sitesPinSet) {
                        TextButton(onClick = { viewModel.hideSites() }) { Text("Hide sites") }
                    }
                    if (unlockedItems.isNotEmpty()) {
                        TextButton(onClick = { lockTargets = unlockedItems }) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (unlockedItems.size == uiState.blockedItems.size) "Lock all" else "Lock unlocked")
                        }
                    }
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
                    sitesHidden = sitesHidden,
                    onDeleteItem = { viewModel.deleteBlockedItem(it) },
                    onLockItem = { lockTargets = listOf(it) },
                    onUnlockEarly = { item ->
                        when {
                            !item.allowsEarlyUnlock() ->
                                infoMessage = "This lock has no early unlock. It ends on ${formatDate(item.lockExpiresAt)}."
                            item.unlockRequestedAt == null -> unlockTarget = item
                            (item.unlockWaitRemainingMs() ?: 0L) > 0L -> unlockTarget = item
                            else -> unlockTarget = item
                        }
                    },
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

    // ============== DIALOGS ==============

    if (showPauseDialog) {
        val last = remember { viewModel.lastPauseChoice() }
        FilterPauseDialog(
            apps = pausableApps,
            loading = pausableAppsLoading,
            initialLockMode = last.lockMode,
            initialTargetPackage = last.targetPackage,
            initialPlainMinutes = last.plainMinutes,
            initialLockMinutes = last.lockMinutes,
            onConfirmPlain = { minutes ->
                showPauseDialog = false
                viewModel.pauseFilter(minutes)
            },
            onConfirmLock = { packageName, minutes ->
                showPauseDialog = false
                viewModel.pauseFilterFor(packageName, minutes)
            },
            onDismiss = { showPauseDialog = false }
        )
    }

    lockTargets?.let { targets ->
        LockBlockDialog(
            title = if (targets.size == 1) "Lock \"${displayName(targets.first(), sitesHidden)}\"" else "Lock ${targets.size} blocks",
            itemCount = targets.size,
            onConfirm = { days, phrase ->
                viewModel.lockItems(targets, days, phrase)
                lockTargets = null
            },
            onDismiss = { lockTargets = null }
        )
    }

    unlockTarget?.let { item ->
        val remainingMs = item.unlockWaitRemainingMs()
        when {
            remainingMs == null -> {
                // Step 1: start the cooling-off period
                AlertDialog(
                    onDismissRequest = { unlockTarget = null },
                    title = { Text("Start early unlock?") },
                    text = {
                        Text(
                            "This begins a 24-hour wait. After that you can type your commitment phrase " +
                                "to remove the lock on \"${displayName(item, sitesHidden)}\". The block stays active meanwhile."
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.requestEarlyUnlock(item)
                            unlockTarget = null
                        }) { Text("Start 24h wait") }
                    },
                    dismissButton = {
                        TextButton(onClick = { unlockTarget = null }) { Text("Keep my promise") }
                    }
                )
            }
            remainingMs > 0L -> {
                // Step 2: still waiting
                val hours = (remainingMs / 3_600_000L).toInt()
                val minutes = ((remainingMs % 3_600_000L) / 60_000L).toInt()
                AlertDialog(
                    onDismissRequest = { unlockTarget = null },
                    title = { Text("Still waiting") },
                    text = { Text("You can type the phrase in ${hours}h ${minutes}m. The block stays active until then.") },
                    confirmButton = {
                        TextButton(onClick = { unlockTarget = null }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.cancelEarlyUnlock(item)
                            unlockTarget = null
                        }) { Text("Cancel request") }
                    }
                )
            }
            else -> {
                // Step 3: wait is over, type the phrase
                val remainingDays = (((item.lockExpiresAt ?: 0L) - System.currentTimeMillis()) / 86_400_000L).toInt()
                UnlockSessionDialog(
                    sessionName = displayName(item, sitesHidden),
                    requiredPhrase = item.lockCommitmentPhrase ?: "",
                    remainingDays = remainingDays.coerceAtLeast(0),
                    onConfirm = {
                        scope.launch {
                            val ok = viewModel.unlockWithPhrase(item, item.lockCommitmentPhrase ?: "")
                            infoMessage = if (ok) "Lock removed." else "Could not unlock."
                            unlockTarget = null
                        }
                    },
                    onDismiss = { unlockTarget = null }
                )
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            title = "Private site list",
            message = "Enter your PIN to show website names.",
            onSubmit = { pin ->
                val ok = viewModel.revealSites(pin)
                if (ok) showPinDialog = false
                ok
            },
            onDismiss = { showPinDialog = false }
        )
    }

    infoMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { infoMessage = null }) { Text("OK") } }
        )
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }
}

private fun displayName(item: BlockedItem, sitesHidden: Boolean): String =
    if (sitesHidden && item.type == BlockType.WEBSITE) "Hidden website" else item.name

private fun formatDate(millis: Long?): String {
    if (millis == null) return "?"
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
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
private fun WebsiteFilterStatus(
    pause: GlobalSettingsManager.VpnPause?,
    onStart: () -> Unit,
    onOpenLog: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    var running by remember { mutableStateOf(WebsiteBlockingVpnService.isServiceRunning) }
    var seen by remember { mutableIntStateOf(WebsiteBlockingVpnService.queriesSeen.get()) }
    var blocked by remember { mutableIntStateOf(WebsiteBlockingVpnService.queriesBlocked.get()) }
    var dohBypass by remember { mutableStateOf(WebsiteBlockingVpnService.dohBypassDetected) }
    var avgMs by remember { mutableIntStateOf(WebsiteBlockingVpnService.averageUpstreamMs) }

    LaunchedEffect(Unit) {
        while (true) {
            running = WebsiteBlockingVpnService.isServiceRunning
            seen = WebsiteBlockingVpnService.queriesSeen.get()
            blocked = WebsiteBlockingVpnService.queriesBlocked.get()
            dohBypass = WebsiteBlockingVpnService.dohBypassDetected
            avgMs = WebsiteBlockingVpnService.averageUpstreamMs
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
                if (pause != null) {
                    Text(
                        text = if (pause.locked) "Website filter: paused for ${pause.targetLabel}"
                               else "Website filter: paused until ${formatTime(pause.until)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = if (pause.locked) {
                            "Phone is locked to that app until ${formatTime(pause.until)}. " +
                                "The filter comes back on its own when the lock ends."
                        } else {
                            "The filter comes back on its own at ${formatTime(pause.until)}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (running) "Website filter: active" else "Website filter: not running",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (running) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "$seen DNS lookups seen, $blocked blocked" +
                            (if (avgMs > 0) ", avg lookup $avgMs ms" else "") +
                            ". Tap to view the log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                dohBypass?.let { who ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$who uses its own encrypted DNS, which bypasses website blocking. " +
                            "Wakt is cutting that off so it falls back to normal DNS. If that app then " +
                            "can't open any site, turn off \"Use secure DNS\" in its settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            when {
                pause != null -> TextButton(onClick = onResume) { Text("Resume now") }
                !running -> TextButton(onClick = onStart) { Text("Start") }
                else -> TextButton(onClick = onPause) { Text("Pause") }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

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
    sitesHidden: Boolean,
    onDeleteItem: (BlockedItem) -> Unit,
    onLockItem: (BlockedItem) -> Unit,
    onUnlockEarly: (BlockedItem) -> Unit,
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
                hidden = sitesHidden && item.type == BlockType.WEBSITE,
                onDelete = { onDeleteItem(item) },
                onLock = { onLockItem(item) },
                onUnlockEarly = { onUnlockEarly(item) }
            )
        }
    }
}

@Composable
private fun BlockedItemCard(
    item: BlockedItem,
    hidden: Boolean,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onUnlockEarly: () -> Unit
) {
    val locked = item.isCommitmentLocked()
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
                    text = if (hidden) "Hidden website" else item.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (hidden) "WEBSITE" else "${item.type} - ${item.challengeType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (locked) {
                    val waiting = (item.unlockWaitRemainingMs() ?: 0L) > 0L
                    Text(
                        text = "Locked until ${formatDate(item.lockExpiresAt)}" +
                            (if (waiting) " (unlock requested, waiting)" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (hidden) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Hidden",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (locked) {
                TextButton(onClick = onUnlockEarly) { Text("Unlock early") }
            } else {
                IconButton(onClick = onLock) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock")
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
