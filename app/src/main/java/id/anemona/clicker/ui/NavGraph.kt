package id.anemona.clicker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.anemona.clicker.ui.screens.HomeScreen
import id.anemona.clicker.ui.screens.PointsScreen
import id.anemona.clicker.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("home", "Home", Icons.Filled.Home),
        Tab("points", "Points", Icons.Filled.TouchApp),
        Tab("settings", "Settings", Icons.Filled.Settings),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val current by nav.currentBackStackEntryAsState()
                val route = current?.destination?.route
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) { launchSingleTop = true }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") { HomeScreen() }
            composable("points") { PointsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
