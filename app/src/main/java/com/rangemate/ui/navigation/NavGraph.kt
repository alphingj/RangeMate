package com.rangemate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rangemate.ui.screens.connect.ConnectScreen
import com.rangemate.ui.screens.dashboard.DashboardScreen
import com.rangemate.ui.screens.debug.DebugScreen
import com.rangemate.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Connect : Screen("connect")
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    object Debug : Screen("debug")
}

@Composable
fun RangeMateNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Connect.route
    ) {
        composable(Screen.Connect.route) {
            ConnectScreen(
                onDeviceConnected = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Connect.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onDisconnect = {
                    navController.navigate(Screen.Connect.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onDebugClick = {
                    navController.navigate(Screen.Debug.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Debug.route) {
            DebugScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}