package com.example.wakt.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wakt.presentation.components.PermissionDialog
import com.example.wakt.presentation.screens.addblock.AddBlockScreen
import com.example.wakt.presentation.screens.lock.LockScreen
import com.example.wakt.presentation.screens.lock.LockViewModel
import com.example.wakt.presentation.screens.lock.TryLockScreen
import com.example.wakt.presentation.screens.schedule.ScheduleScreen
import com.example.wakt.presentation.screens.schedule.ScheduleDetailScreen
import com.example.wakt.presentation.screens.settings.SettingsScreen
import com.example.wakt.utils.PermissionHelper
import androidx.navigation.NavType
import androidx.navigation.navArgument

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Lock", Icons.Outlined.Lock, "lock"),
    BottomNavItem("Schedule", Icons.Outlined.DateRange, "schedule"),
    BottomNavItem("Settings", Icons.Outlined.Settings, "settings")
)

@Composable
fun MainScaffold(
    navController: NavHostController = rememberNavController()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    // Track Lock screen's internal tab (0 = Phone, 1 = App)
    var lockScreenTab by remember { mutableIntStateOf(0) }

    // Shared ViewModel for lock-related screens
    val lockViewModel: LockViewModel = hiltViewModel()

    // Global permission state
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(PermissionHelper.areAllPermissionsGranted(context)) }
    var missingPermissions by remember { mutableStateOf(PermissionHelper.getMissingPermissions(context)) }

    val refreshPermissions = {
        permissionsGranted = PermissionHelper.areAllPermissionsGranted(context)
        missingPermissions = PermissionHelper.getMissingPermissions(context)
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

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    // Global permission dialog
    if (showPermissionDialog) {
        PermissionDialog(
            missingPermissions = missingPermissions,
            context = context,
            onDismiss = { showPermissionDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                bottomNavItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            // Navigate back to main if we're on a nested screen
                            navController.popBackStack("main", inclusive = false)
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("main") {
                when (selectedTab) {
                    0 -> LockScreen(
                        onNavigateToAddBlock = {
                            lockScreenTab = 1 // Stay on App tab when returning
                            navController.navigate("add_block")
                        },
                        onNavigateToTryLock = { navController.navigate("try_lock") },
                        viewModel = lockViewModel,
                        selectedTab = lockScreenTab,
                        onTabChange = { lockScreenTab = it },
                        permissionsGranted = permissionsGranted,
                        missingPermissions = missingPermissions,
                        onRequestPermissions = { showPermissionDialog = true }
                    )
                    1 -> ScheduleScreen(
                        onNavigateToAddSchedule = { isAppSchedule ->
                            navController.navigate("schedule_detail?isAppSchedule=$isAppSchedule")
                        },
                        onNavigateToEditSchedule = { id -> navController.navigate("schedule_detail/$id") }
                    )
                    2 -> SettingsScreen()
                }
            }

            composable("add_block") {
                AddBlockScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("try_lock") {
                TryLockScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onStartTryLock = { durationSeconds ->
                        lockViewModel.startTryLockSession(durationSeconds)
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "schedule_detail?isAppSchedule={isAppSchedule}",
                arguments = listOf(navArgument("isAppSchedule") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val isAppSchedule = backStackEntry.arguments?.getBoolean("isAppSchedule") ?: false
                ScheduleDetailScreen(
                    scheduleId = null,
                    isAppSchedule = isAppSchedule,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "schedule_detail/{scheduleId}",
                arguments = listOf(navArgument("scheduleId") { type = NavType.LongType })
            ) { backStackEntry ->
                val scheduleId = backStackEntry.arguments?.getLong("scheduleId")
                ScheduleDetailScreen(
                    scheduleId = scheduleId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
