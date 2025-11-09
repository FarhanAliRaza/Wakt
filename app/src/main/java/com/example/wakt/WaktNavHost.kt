package com.example.wakt

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wakt.presentation.screens.addblock.AddBlockScreen
import com.example.wakt.presentation.screens.home.HomeScreen
import com.example.wakt.presentation.screens.phonebrick.PhoneBrickScreen
import com.example.wakt.ui.goals.GoalBlockScreen

@Composable
fun WaktNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
    // VPN permission request removed for battery optimization
    // onVpnPermissionRequest: ((callback: () -> Unit) -> Unit)? = null
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToAddBlock = {
                    navController.navigate("add_block")
                },
                onNavigateToGoals = {
                    navController.navigate("goal_blocks")
                },
                onNavigateToPhoneBrick = {
                    navController.navigate("phone_brick")
                }
                // VPN permission request removed for battery optimization
                // onVpnPermissionRequest = onVpnPermissionRequest
            )
        }
        
        composable("add_block") {
            AddBlockScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("goal_blocks") {
            GoalBlockScreen()
        }
        
        composable("phone_brick") {
            PhoneBrickScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}