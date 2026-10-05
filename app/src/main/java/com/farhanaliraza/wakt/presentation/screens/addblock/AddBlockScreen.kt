package com.farhanaliraza.wakt.presentation.screens.addblock

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.farhanaliraza.wakt.presentation.ui.theme.WaktGradient
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockScreen(onNavigateBack: () -> Unit, viewModel: AddBlockViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadInstalledApps(context) }

    // Website blocks use a local DNS-filtering VPN, which needs one-time user
    // consent. The save proceeds regardless of the dialog result - accessibility
    // based blocking still works without the VPN.
    val vpnConsentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.saveSelectedItems()
            onNavigateBack()
        }

    fun saveAndClose() {
        val consentIntent =
            if (uiState.websiteUrl.isNotBlank()) VpnService.prepare(context) else null
        if (consentIntent != null) {
            vpnConsentLauncher.launch(consentIntent)
        } else {
            viewModel.saveSelectedItems()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Block") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { saveAndClose() },
                        enabled = uiState.selectedApps.isNotEmpty() || uiState.websiteUrl.isNotBlank(),
                        modifier = if (uiState.selectedApps.isNotEmpty() || uiState.websiteUrl.isNotBlank()) {
                            Modifier.background(
                                brush = WaktGradient,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                        } else Modifier
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = if (uiState.selectedApps.isNotEmpty() || uiState.websiteUrl.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = uiState.selectedTabIndex) {
                Tab(
                    selected = uiState.selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Apps") }
                )
                Tab(
                    selected = uiState.selectedTabIndex == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Websites") }
                )
            }

            when (uiState.selectedTabIndex) {
                0 -> AppsTab(
                    apps = uiState.filteredApps,
                    selectedApps = uiState.selectedApps,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onAppToggle = viewModel::toggleAppSelection,
                    onLoadMore = viewModel::loadMoreIfNeeded,
                    isLoading = uiState.isLoading
                )
                1 -> WebsitesTab(
                    websiteUrl = uiState.websiteUrl,
                    onWebsiteUrlChange = viewModel::updateWebsiteUrl
                )
            }
        }
    }
}

@Composable
private fun AppsTab(
    apps: List<AppInfo>,
    selectedApps: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAppToggle: (String) -> Unit,
    onLoadMore: () -> Unit,
    isLoading: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val listState = rememberLazyListState()

            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .distinctUntilChanged()
                    .collect { lastIndex ->
                        if (lastIndex >= 0 && lastIndex >= apps.size - 5) {
                            onLoadMore()
                        }
                    }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                state = listState
            ) {
                items(
                    items = apps,
                    key = { app -> app.packageName },
                    contentType = { "app_item" }
                ) { app ->
                    val isSelected = remember(app.packageName, selectedApps) {
                        selectedApps.contains(app.packageName)
                    }
                    AppItem(
                        app = app,
                        isSelected = isSelected,
                        onToggle = { onAppToggle(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
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

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun WebsitesTab(
    websiteUrl: String,
    onWebsiteUrlChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = websiteUrl,
            onValueChange = { onWebsiteUrlChange(it.trim()) },
            label = { Text("Website URL") },
            placeholder = { Text("e.g., facebook.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Uri
            )
        )

        Text(
            text = "Enter the main domain, e.g. facebook.com. All its subdomains (www., m., ...) " +
                "and the matching app are blocked at the DNS level. You'll be asked to allow a VPN " +
                "connection once - all filtering happens locally on your device, nothing is sent " +
                "to a server and browsing speed is unaffected. Already-open pages may keep working " +
                "until the browser is closed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
