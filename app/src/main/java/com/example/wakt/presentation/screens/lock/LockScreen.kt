package com.example.wakt.presentation.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LockScreen(
    onNavigateToAddBlock: () -> Unit,
    onNavigateToTryLock: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    permissionsGranted: Boolean = true,
    missingPermissions: List<String> = emptyList(),
    onRequestPermissions: () -> Unit = {}
) {
    val tabs = listOf("Phone", "App/Site")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top tab toggle (pill style)
        TabToggle(
            tabs = tabs,
            selectedTab = selectedTab,
            onTabSelected = { onTabChange(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Content based on selected tab
        when (selectedTab) {
            0 -> PhoneTab(
                viewModel = viewModel,
                onNavigateToTryLock = onNavigateToTryLock,
                permissionsGranted = permissionsGranted,
                missingPermissions = missingPermissions,
                onRequestPermissions = onRequestPermissions
            )
            1 -> AppTab(
                viewModel = viewModel,
                onNavigateToAddBlock = onNavigateToAddBlock,
                permissionsGranted = permissionsGranted,
                missingPermissions = missingPermissions,
                onRequestPermissions = onRequestPermissions
            )
        }
    }
}

@Composable
private fun TabToggle(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
