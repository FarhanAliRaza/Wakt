package com.example.wakt.presentation.screens.addblock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wakt.data.database.entity.ChallengeType
import com.example.wakt.presentation.ui.theme.WaktGradient
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockScreen(onNavigateBack: () -> Unit, viewModel: AddBlockViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadInstalledApps(context) }

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
                                    onClick = {
                                        viewModel.saveSelectedItems()
                                        onNavigateBack()
                                    },
                                    enabled =
                                            uiState.selectedApps.isNotEmpty() ||
                                                    uiState.websiteUrl.isNotBlank(),
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
                0 ->
                        AppsTab(
                                apps = uiState.filteredApps,
                                selectedApps = uiState.selectedApps,
                                searchQuery = uiState.searchQuery,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onAppToggle = viewModel::toggleAppSelection,
                                onLoadMore = viewModel::loadMoreIfNeeded,
                                isLoading = uiState.isLoading,
                                challengeType = uiState.challengeType,
                                onChallengeTypeChange = viewModel::updateChallengeType,
                                waitTimeMinutes = uiState.waitTimeMinutes,
                                onWaitTimeChange = viewModel::updateWaitTime
                        )
                1 ->
                        WebsitesTab(
                                websiteUrl = uiState.websiteUrl,
                                onWebsiteUrlChange = viewModel::updateWebsiteUrl,
                                challengeType = uiState.challengeType,
                                onChallengeTypeChange = viewModel::updateChallengeType,
                                waitTimeMinutes = uiState.waitTimeMinutes,
                                onWaitTimeChange = viewModel::updateWaitTime
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
        isLoading: Boolean,
        challengeType: ChallengeType,
        onChallengeTypeChange: (ChallengeType) -> Unit,
        waitTimeMinutes: Int,
        onWaitTimeChange: (Int) -> Unit
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

        if (selectedApps.isNotEmpty()) {
            Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = "Block Settings", style = MaterialTheme.typography.titleMedium)

                    ChallengeTypeSelector(
                            challengeType = challengeType,
                            onChallengeTypeChange = onChallengeTypeChange
                    )

                    if (challengeType == ChallengeType.WAIT) {
                        WaitTimeSlider(
                                waitTimeMinutes = waitTimeMinutes,
                                onWaitTimeChange = onWaitTimeChange
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val listState = rememberLazyListState()
            
            // Load more when nearing the end of the list - removed apps.size from dependencies
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
        onWebsiteUrlChange: (String) -> Unit,
        challengeType: ChallengeType,
        onChallengeTypeChange: (ChallengeType) -> Unit,
        waitTimeMinutes: Int,
        onWaitTimeChange: (Int) -> Unit
) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WebsiteUrlInput(websiteUrl = websiteUrl, onWebsiteUrlChange = onWebsiteUrlChange)

        ChallengeTypeSelector(
                challengeType = challengeType,
                onChallengeTypeChange = onChallengeTypeChange
        )

        if (challengeType == ChallengeType.WAIT) {
            WaitTimeSlider(waitTimeMinutes = waitTimeMinutes, onWaitTimeChange = onWaitTimeChange)
        }

        Text(
                text =
                        "Note: You'll need to grant accessibility permissions for website blocking to work in browsers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WebsiteUrlInput(websiteUrl: String, onWebsiteUrlChange: (String) -> Unit) {
    OutlinedTextField(
            value = websiteUrl,
            onValueChange = onWebsiteUrlChange,
            label = { Text("Website URL") },
            placeholder = { Text("e.g., facebook.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
    )
}

@Composable
private fun ChallengeTypeSelector(
        challengeType: ChallengeType,
        onChallengeTypeChange: (ChallengeType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Challenge Type", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                    onClick = { onChallengeTypeChange(ChallengeType.WAIT) },
                    label = { Text("Wait Timer") },
                    selected = challengeType == ChallengeType.WAIT,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = Color.White
                    ),
                    modifier = if (challengeType == ChallengeType.WAIT) {
                        Modifier.background(brush = WaktGradient, shape = RoundedCornerShape(16.dp))
                    } else Modifier
            )
            FilterChip(
                    onClick = { onChallengeTypeChange(ChallengeType.CLICK_500) },
                    label = { Text("500 Clicks") },
                    selected = challengeType == ChallengeType.CLICK_500,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = Color.White
                    ),
                    modifier = if (challengeType == ChallengeType.CLICK_500) {
                        Modifier.background(brush = WaktGradient, shape = RoundedCornerShape(16.dp))
                    } else Modifier
            )
        }
    }
}

@Composable
private fun WaitTimeSlider(waitTimeMinutes: Int, onWaitTimeChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
                text = "Wait Time: $waitTimeMinutes minutes",
                style = MaterialTheme.typography.bodyLarge
        )

        Slider(
                value = waitTimeMinutes.toFloat(),
                onValueChange = { onWaitTimeChange(it.toInt()) },
                valueRange = 10f..30f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("10 min", style = MaterialTheme.typography.bodySmall)
            Text("30 min", style = MaterialTheme.typography.bodySmall)
        }
    }
}

