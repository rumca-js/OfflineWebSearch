package com.example.index

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.index.ui.screens.AboutScreen
import com.example.index.ui.screens.BrowseScreen
import com.example.index.ui.screens.OptionsScreen
import com.example.index.ui.theme.OfflineWebSearchTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.index.ui.SearchViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Browse", Icons.Filled.Home)
    object About : Screen("about", "About", Icons.Filled.Info)
    object Options : Screen("options", "Options", Icons.Filled.Settings)
    object Detail : Screen("detail", "Detail", Icons.Filled.Search)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val searchViewModel: SearchViewModel = viewModel()
            OfflineWebSearchTheme {
                val navController = rememberNavController()
                val items = listOf(
                    Screen.Home,
                    Screen.About,
                    Screen.Options,
                )
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) { 
                            BrowseScreen(
                                viewModel = searchViewModel,
                                onNavigateToDetail = { entry ->
                                    searchViewModel.selectedEntry = entry
                                    navController.navigate(Screen.Detail.route)
                                }
                            )
                        }
                        composable(Screen.About.route) { AboutScreen() }
                        composable(Screen.Options.route) { OptionsScreen() }
                        composable(Screen.Detail.route) {
                            searchViewModel.selectedEntry?.let { place ->
                                com.example.index.ui.screens.EntryDetailScreen(
                                    entry = place,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}