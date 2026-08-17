package io.github.rumcajs.offlinewebsearch

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.List
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
import androidx.lifecycle.viewmodel.compose.viewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Browse", Icons.Filled.Home)
    object Databases : Screen("databases", "Databases", Icons.Filled.Storage)
    object Sources : Screen("sources", "Sources", Icons.Filled.List)
    object About : Screen("about", "About", Icons.Filled.Info)
    object Options : Screen("options", "Options", Icons.Filled.Settings)
    object Detail : Screen("detail", "Detail", Icons.Filled.Search)
    object LinkPreview : Screen("link_preview", "Link Preview", Icons.Filled.Search)
    object LinkData : Screen("link_data", "Link Data", Icons.Filled.Search)
    object DatabaseDetail : Screen("database_detail", "Database Detail", Icons.Filled.Storage)
    object Edit : Screen("edit", "Edit Entry", Icons.Filled.Edit)
    object EntryAdd : Screen("entry_add", "Add Entry", Icons.Filled.Edit)
    object SourceDetail : Screen("source_detail", "Source Detail", Icons.Filled.List)
    object SourceEdit : Screen("source_edit", "Source Edit", Icons.Filled.Edit)
}

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.initialize(this)
        enableEdgeToEdge()
        setContent {
            val searchViewModel: io.github.rumcajs.offlinewebsearch.ui.SearchViewModel = viewModel()
            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.OfflineWebSearchTheme {
                val navController = rememberNavController()
                val items = listOf(
                    Screen.Home,
                    Screen.Sources,
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
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryListScreen(
                                viewModel = searchViewModel,
                                onNavigateToDetail = { entry ->
                                    searchViewModel.selectedEntry = entry
                                    navController.navigate(Screen.Detail.route)
                                },
                                onNavigateToAddEntry = {
                                    navController.navigate(Screen.EntryAdd.route)
                                }
                            )
                        }
                        composable(Screen.Sources.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SourcesScreen(
                                onNavigateToSource = { source ->
                                    searchViewModel.selectedSource = source
                                    navController.navigate(Screen.SourceDetail.route)
                                },
                                onNavigateToEditSource = { source ->
                                    searchViewModel.selectedSource = source
                                    navController.navigate(Screen.SourceEdit.route)
                                },
                                onNavigateToAddSource = {
                                    searchViewModel.selectedSource = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.Source()
                                    navController.navigate(Screen.SourceEdit.route)
                                }
                            )
                        }
                        composable(Screen.SourceDetail.route) {
                            searchViewModel.selectedSource?.let { source ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SourceScreen(
                                    source = source,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.SourceEdit.route) {
                            searchViewModel.selectedSource?.let { source ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SourceEditScreen(
                                    source = source,
                                    onSourceUpdated = { updatedSource ->
                                        searchViewModel.selectedSource = updatedSource
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.Databases.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.DatabasesScreen(
                                onNavigateToDatabaseDetail = { url, state ->
                                    searchViewModel.selectedDatabaseUrl = url
                                    searchViewModel.selectedDatabaseState = state
                                    navController.navigate(Screen.DatabaseDetail.route)
                                }
                            )
                        }
                        composable(Screen.DatabaseDetail.route) {
                            val url = searchViewModel.selectedDatabaseUrl
                            val config = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState().value
                            val state = if (url == null) {
                                searchViewModel.selectedDatabaseState ?: _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.DatabaseState(
                                    url = "",
                                    localFileName = "places_0.json",
                                    status = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.DatabaseStatus.READY,
                                    progress = 1.0f,
                                    isReadOnly = true
                                )
                            } else {
                                config.databases[url] ?: searchViewModel.selectedDatabaseState
                            }
                            if (state != null) {
                                val dbConfig = if (url == null) config.defaultDbConfig else config.dbConfigs[url] ?: config.defaultDbConfig
                                val isActive = if (url == null) config.activeDatabase == null else config.activeDatabase == url
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.DatabaseScreen(
                                    url = url,
                                    state = state,
                                    dbConfig = dbConfig,
                                    isActive = isActive,
                                    onBack = { navController.popBackStack() },
                                    onSetActive = { _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setActiveDatabase(url) }
                                )
                            }
                        }
                        composable(Screen.About.route) { _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.AboutScreen(onBack = { navController.popBackStack() }) }
                        composable(Screen.Options.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.OptionsScreen(
                                onNavigateToDatabases = {
                                    navController.navigate(_root_ide_package_.io.github.rumcajs.offlinewebsearch.Screen.Databases.route)
                                },
                                onNavigateToDatabaseDetail = { url, state ->
                                    searchViewModel.selectedDatabaseUrl = url
                                    searchViewModel.selectedDatabaseState = state
                                    navController.navigate(Screen.DatabaseDetail.route)
                                },
                                onNavigateToAbout = {
                                    navController.navigate(Screen.About.route)
                                }
                            )
                        }
                        composable(Screen.Detail.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            searchViewModel.selectedEntry?.let { place ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryDetailScreen(
                                    entry = searchViewModel.selectedEntry ?: place,
                                    onNavigateToLinkPreview = { url ->
                                        searchViewModel.previewUrl = url
                                        navController.navigate(Screen.LinkPreview.route)
                                    },
                                    onNavigateToEdit = {
                                        navController.navigate(Screen.Edit.route)
                                    },
                                    onDelete = {
                                        searchViewModel.deleteEntry(context, place) { success ->
                                            if (success) {
                                                searchViewModel.selectedEntry = null
                                                navController.popBackStack()
                                            }
                                        }
                                    },
                                    onTagClick = { tag ->
                                        searchViewModel.searchQuery = "tag LIKE '%$tag%'"
                                        searchViewModel.performSearch(context)
                                        navController.popBackStack(Screen.Home.route, false)
                                    },
                                    onVisit = {
                                        searchViewModel.recordVisit(context, place)
                                    },
                                    onSelectEntry = { targetEntry ->
                                        searchViewModel.selectedEntry = targetEntry
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.Edit.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            searchViewModel.selectedEntry?.let { place ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryEditScreen(
                                    entry = place,
                                    onEntryUpdated = { updatedEntry ->
                                        searchViewModel.selectedEntry = updatedEntry
                                        searchViewModel.refreshPage(context)
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.EntryAdd.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryEditScreen(
                                entry = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.Entry(),
                                onEntryUpdated = { newEntry ->
                                    searchViewModel.refreshPage(context)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.LinkPreview.route) {
                            searchViewModel.previewUrl?.let { url ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryStatusScreen(
                                    url = url,
                                    onNavigateToLinkData = {
                                        searchViewModel.previewUrl = url
                                        navController.navigate(Screen.LinkData.route)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.LinkData.route) {
                            searchViewModel.previewUrl?.let { url ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryPreviewScreen(
                                    url = url,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToDetail = { entry ->
                                        searchViewModel.selectedEntry = entry
                                        navController.navigate(Screen.Detail.route)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}