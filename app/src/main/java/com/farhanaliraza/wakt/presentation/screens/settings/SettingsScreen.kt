package com.farhanaliraza.wakt.presentation.screens.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.farhanaliraza.wakt.R
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.presentation.components.PinDialog
import com.farhanaliraza.wakt.presentation.components.SetPinDialog
import com.farhanaliraza.wakt.utils.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val clickCount by viewModel.clickCount.collectAsState()
    val defaultAllowedApps by viewModel.defaultAllowedApps.collectAsState()
    val emergencyExitEnabled by viewModel.emergencyExitEnabled.collectAsState()
    val vpnExcludedApps by viewModel.vpnExcludedApps.collectAsState()
    val sitesPinSet by viewModel.sitesPinSet.collectAsState()
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var pinVerifiedForChange by remember { mutableStateOf(false) }

    var showAppSelectorDialog by remember { mutableStateOf(false) }
    var showVpnExclusionDialog by remember { mutableStateOf(false) }

    // Permission status, refreshed whenever the user comes back from system settings
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefresh++
                viewModel.refreshServices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val usageAccessGranted = remember(permissionRefresh) { PermissionHelper.isUsageAccessGranted(context) }
    val overlayGranted = remember(permissionRefresh) { PermissionHelper.isOverlayPermissionGranted(context) }
    val accessibilityEnabled = remember(permissionRefresh) { PermissionHelper.isAccessibilityServiceEnabled(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Emergency Exit Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow Emergency Exit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When disabled, you cannot exit brick sessions early",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = emergencyExitEnabled,
                    onCheckedChange = { viewModel.setEmergencyExitEnabled(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Blocking Method Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Blocking Method",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Standard mode needs two permissions and keeps banking apps working. " +
                        "The Accessibility Service is an optional upgrade: it reacts instantly and " +
                        "blocks websites inside browsers, but many banking apps refuse to run while " +
                        "any accessibility service is enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionStatusRow(
                    title = "Usage Access",
                    subtitle = "Detects which app is open",
                    granted = usageAccessGranted,
                    actionLabel = "Grant",
                    onAction = { PermissionHelper.requestUsageAccessPermission(context) }
                )
                PermissionStatusRow(
                    title = "Display over other apps",
                    subtitle = "Shows the lock and challenge screens",
                    granted = overlayGranted,
                    actionLabel = "Grant",
                    onAction = { PermissionHelper.requestOverlayPermission(context) }
                )
                PermissionStatusRow(
                    title = "Accessibility Service (optional)",
                    subtitle = if (accessibilityEnabled) "Enhanced blocking on. Turn off before using banking apps."
                               else "Enhanced blocking off",
                    granted = accessibilityEnabled,
                    actionLabel = if (accessibilityEnabled) "Turn off" else "Turn on",
                    onAction = { PermissionHelper.requestAccessibilityPermission(context) }
                )

                PermissionHelper.getBackgroundPopupInstructions()?.let { instructions ->
                    if (!accessibilityEnabled) {
                        Text(
                            text = "On this device also allow: $instructions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Challenge Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Challenge Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "When you try to open a blocked app or website, you'll need to tap the screen multiple times.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Click Count Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tap Count: $clickCount taps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Slider(
                        value = clickCount.toFloat(),
                        onValueChange = { viewModel.setClickCount(it.toInt()) },
                        valueRange = GlobalSettingsManager.MIN_CLICK_COUNT.toFloat()..GlobalSettingsManager.MAX_CLICK_COUNT.toFloat(),
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${GlobalSettingsManager.MIN_CLICK_COUNT}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${GlobalSettingsManager.MAX_CLICK_COUNT}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Default Allowed Apps Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Default Allowed Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "These apps will be pre-selected when creating new brick sessions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (defaultAllowedApps.isNotEmpty()) {
                    Text(
                        text = "${defaultAllowedApps.size} app${if (defaultAllowedApps.size > 1) "s" else ""} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { showAppSelectorDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (defaultAllowedApps.isEmpty()) "Configure Apps" else "Edit Apps")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Private Site List Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Private Site List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hide the names of blocked websites and the DNS log behind a PIN, so someone looking at your phone cannot see what you blocked. Blocking keeps working while hidden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sitesPinSet) {
                    Text(
                        text = "PIN is set. Website names are hidden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pinVerifiedForChange = false; showChangePinDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Change PIN") }
                        OutlinedButton(
                            onClick = { showRemovePinDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Remove PIN") }
                    }
                } else {
                    Button(
                        onClick = { showSetPinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Set PIN") }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Website Filter Exclusions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Apps Excluded From Website Filter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Website blocking runs as a local DNS filter (a VPN). Some banking and payment apps refuse to work while a VPN is active. Exclude them here; their traffic bypasses the filter entirely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (vpnExcludedApps.isNotEmpty()) {
                    Text(
                        text = "${vpnExcludedApps.size} app${if (vpnExcludedApps.size > 1) "s" else ""} excluded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { showVpnExclusionDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (vpnExcludedApps.isEmpty()) "Choose Apps" else "Edit Apps")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            val context = LocalContext.current

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "I built Wakt for myself. I struggled with phone addiction and couldn't find a blocker that was truly free — no ads, no tracking, no premium features locked behind paywalls. So I built what I needed and decided to share it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Free & open source. No ads. No tracking. Privacy first.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                TextButton(onClick = { viewModel.replayOnboarding() }) {
                    Text("Show the intro and permission setup again")
                }

                // GitHub Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FarhanAliRaza/Wakt"))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = "GitHub",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "View on GitHub",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Star the repo if you find it useful",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // LinkedIn Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/farhanaliraza"))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_linkedin),
                        contentDescription = "LinkedIn",
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF0A66C2)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Connect on LinkedIn",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Farhan Ali Raza",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showAppSelectorDialog) {
        DefaultAllowedAppsSelectorDialog(
            currentlySelected = defaultAllowedApps,
            onDismiss = { showAppSelectorDialog = false },
            onAppsSelected = { selectedApps ->
                viewModel.setDefaultAllowedApps(selectedApps)
                showAppSelectorDialog = false
            }
        )
    }

    if (showSetPinDialog) {
        SetPinDialog(
            title = "Set PIN",
            onConfirm = { pin ->
                viewModel.setSitesPin(pin)
                showSetPinDialog = false
            },
            onDismiss = { showSetPinDialog = false }
        )
    }

    if (showRemovePinDialog) {
        PinDialog(
            title = "Remove PIN",
            message = "Enter the current PIN to stop hiding website names.",
            confirmLabel = "Remove",
            onSubmit = { pin ->
                val ok = viewModel.verifySitesPin(pin)
                if (ok) {
                    viewModel.clearSitesPin()
                    showRemovePinDialog = false
                }
                ok
            },
            onDismiss = { showRemovePinDialog = false }
        )
    }

    if (showChangePinDialog) {
        if (!pinVerifiedForChange) {
            PinDialog(
                title = "Change PIN",
                message = "Enter the current PIN first.",
                confirmLabel = "Next",
                onSubmit = { pin ->
                    val ok = viewModel.verifySitesPin(pin)
                    if (ok) pinVerifiedForChange = true
                    ok
                },
                onDismiss = { showChangePinDialog = false }
            )
        } else {
            SetPinDialog(
                title = "New PIN",
                onConfirm = { pin ->
                    viewModel.setSitesPin(pin)
                    showChangePinDialog = false
                },
                onDismiss = { showChangePinDialog = false }
            )
        }
    }

    if (showVpnExclusionDialog) {
        DefaultAllowedAppsSelectorDialog(
            currentlySelected = vpnExcludedApps,
            onDismiss = { showVpnExclusionDialog = false },
            onAppsSelected = { selectedApps ->
                viewModel.setVpnExcludedApps(selectedApps)
                showVpnExclusionDialog = false
            },
            title = "Exclude From Website Filter",
            subtitle = "These apps will not go through the DNS website filter"
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (granted) {
            Text(
                text = "On",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF22C55E),
                fontWeight = FontWeight.SemiBold
            )
            if (actionLabel == "Turn off") {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        } else {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private data class AppInfo(
    val name: String,
    val packageName: String
)

@Composable
private fun DefaultAllowedAppsSelectorDialog(
    currentlySelected: Set<String>,
    onDismiss: () -> Unit,
    onAppsSelected: (Set<String>) -> Unit,
    title: String = "Select Default Apps",
    subtitle: String = "These apps will be pre-selected when creating new brick sessions"
) {
    var selectedApps by remember { mutableStateOf(currentlySelected) }
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            installedApps = withContext(Dispatchers.IO) {
                loadInstalledApps(context)
            }
        } catch (e: Exception) {
            // Log error but don't crash
        } finally {
            isLoading = false
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter { app ->
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedApps.isNotEmpty()) {
                    Text(
                        text = "${selectedApps.size} app${if (selectedApps.size > 1) "s" else ""} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps) { app ->
                            AppSelectionItem(
                                app = app,
                                isSelected = selectedApps.contains(app.packageName),
                                onSelectionChange = { isSelected ->
                                    selectedApps = if (isSelected) {
                                        selectedApps + app.packageName
                                    } else {
                                        selectedApps - app.packageName
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onAppsSelected(selectedApps) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectionItem(
    app: AppInfo,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChange(!isSelected) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
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

private fun loadInstalledApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

    return installedPackages
        .filter { appInfo ->
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                launchIntent != null
            } catch (e: Exception) {
                false
            }
        }
        .mapNotNull { appInfo ->
            try {
                AppInfo(
                    name = appInfo.loadLabel(packageManager).toString(),
                    packageName = appInfo.packageName
                )
            } catch (e: Exception) {
                null
            }
        }
        .sortedBy { it.name.lowercase() }
}
