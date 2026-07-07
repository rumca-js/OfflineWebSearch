package io.github.rumcajs.offlinewebsearch

import android.os.Bundle
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
import io.github.rumcajs.offlinewebsearch.ui.screens.AboutScreen
import io.github.rumcajs.offlinewebsearch.ui.screens.BrowseScreen
import io.github.rumcajs.offlinewebsearch.ui.screens.OptionsScreen
import io.github.rumcajs.offlinewebsearch.ui.screens.LinkPreviewScreen
import io.github.rumcajs.offlinewebsearch.ui.theme.OfflineWebSearchTheme
import androidx.lifecycle.viewmodel.compose.viewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : io.github.rumcajs.offlinewebsearch.Screen("home", "Browse", Icons.Filled.Home)
    object About : io.github.rumcajs.offlinewebsearch.Screen("about", "About", Icons.Filled.Info)
    object Options : io.github.rumcajs.offlinewebsearch.Screen("options", "Options", Icons.Filled.Settings)
    object Detail : io.github.rumcajs.offlinewebsearch.Screen("detail", "Detail", Icons.Filled.Search)
    object LinkPreview : io.github.rumcajs.offlinewebsearch.Screen("link_preview", "Link Preview", Icons.Filled.Search)
}

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.loadNetworkConfig(this)
        enableEdgeToEdge()
        setContent {
            val searchViewModel: io.github.rumcajs.offlinewebsearch.ui.SearchViewModel = viewModel()
            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.OfflineWebSearchTheme {
                val navController = rememberNavController()
                val items = listOf(
                    _root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Home,
                    _root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.About,
                    _root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Options,
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
                        startDestination = _root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Home.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.BrowseScreen(
                                viewModel = searchViewModel,
                                onNavigateToDetail = { entry ->
                                    searchViewModel.selectedEntry = entry
                                    navController.navigate(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Detail.route)
                                }
                            )
                        }
                        composable(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.About.route) { _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.AboutScreen() }
                        composable(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Options.route) { _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.OptionsScreen() }
                        composable(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Detail.route) {
                            searchViewModel.selectedEntry?.let { place ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryDetailScreen(
                                    entry = place,
                                    onNavigateToLinkPreview = { url ->
                                        searchViewModel.previewUrl = url
                                        navController.navigate(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.LinkPreview.route)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.LinkPreview.route) {
                            searchViewModel.previewUrl?.let { url ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.LinkPreviewScreen(
                                    url = url,
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