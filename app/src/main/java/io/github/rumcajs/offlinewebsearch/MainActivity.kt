package io.github.rumcajs.offlinewebsearch

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.ui.components.StartupWizardDialog
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Browse", Icons.Filled.Search)
    object Databases : Screen("databases", "Databases", Icons.Filled.Storage)
    object Sources : Screen("sources", "Sources", Icons.AutoMirrored.Filled.List)
    object About : Screen("about", "About", Icons.Filled.Info)
    object Options : Screen("options", "Options", Icons.Filled.Settings)
    object Detail : Screen("detail", "Detail", Icons.Filled.Search)
    object LinkPreview : Screen("link_preview", "Link Preview", Icons.Filled.Search)
    object LinkData : Screen("link_data", "Link Data", Icons.Filled.Search)
    object DatabaseDetail : Screen("database_detail", "Database Detail", Icons.Filled.Storage)
    object DatabasePreselectedList : Screen("database_preselected_list", "Preselected Databases", Icons.Filled.Storage)
    object Edit : Screen("edit", "Edit Entry", Icons.Filled.Edit)
    object EntryAdd : Screen("entry_add", "Add Entry", Icons.Filled.Edit)
    object SourceDetail : Screen("source_detail", "Source Detail", Icons.AutoMirrored.Filled.List)
    object SourceEdit : Screen("source_edit", "Source Edit", Icons.Filled.Edit)
    object SourceUrlEditPreview : Screen("source_url_edit_preview", "Add Source Preview", Icons.Filled.Edit)
    object Visited : Screen("visited", "Visited", Icons.AutoMirrored.Filled.List)
    object ReadLater : Screen("read_later", "Read Later", Icons.Filled.Bookmark)
    object AppLogging : Screen("app_logging", "Logs", Icons.AutoMirrored.Filled.List)
    object OptionsAdvanced : Screen("options_advanced", "Advanced", Icons.Filled.Settings)
    object LinkChecker : Screen("link_checker", "Link Checker", Icons.AutoMirrored.Filled.List)
}

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfigManager.initialize(this)
        enableEdgeToEdge()
        setContent {
            val entriesViewModel: io.github.rumcajs.offlinewebsearch.ui.EntriesViewModel = viewModel()
            val sourcesViewModel: io.github.rumcajs.offlinewebsearch.ui.SourcesViewModel = viewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val cfg = AppConfigManager.config.value
                        val activeState = cfg.activeDatabaseState
                        if (activeState != null && !activeState.isReadOnly && activeState.extension == ".db" && !cfg.networkConfig.disabled) {
                            // Enqueue outdated sources for background refresh.
                            // Completion (entries list refresh, toast) is handled by
                            // SourcesListScreen via the onRefreshSuccess callback.
                            SourceRefreshWorker.enqueueOutdatedSources(context, activeState)
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.OfflineWebSearchTheme {
                val config by AppConfigManager.config.collectAsState()
                if (!config.isInitialized) {
                    StartupWizardDialog()
                }

                val navController = rememberNavController()
                val items = listOf(
                    Screen.Home,
                    Screen.Sources,
                    Screen.Options,
                )
                val sourceRefreshProgress by SourceRefreshWorker.progress.collectAsState()
                var hasOutdatedSources by remember { mutableStateOf(false) }

                LaunchedEffect(config.activeDatabaseUrl, config.networkConfig.disabled, sourceRefreshProgress.isRunning) {
                    if (!sourceRefreshProgress.isRunning) {
                        hasOutdatedSources = SourceRepository.hasOutdatedSources(context, config.activeDatabaseState)
                    } else {
                        hasOutdatedSources = false
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = {
                                        if (screen == Screen.Sources && sourceRefreshProgress.isRunning) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            BadgedBox(
                                                badge = {
                                                    if (screen == Screen.Sources && hasOutdatedSources) {
                                                        Badge()
                                                    }
                                                }
                                            ) {
                                                Icon(screen.icon, contentDescription = null)
                                            }
                                        }
                                    },
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
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntriesListScreen(
                                viewModel = entriesViewModel,
                                onNavigateToDetail = { entry ->
                                    entriesViewModel.selectedEntry = entry
                                    navController.navigate(Screen.Detail.route)
                                },
                                onNavigateToAddEntry = {
                                    navController.navigate(Screen.EntryAdd.route)
                                },
                                onNavigateToVisited = {
                                    navController.navigate(Screen.Visited.route)
                                },
                                onNavigateToReadLater = {
                                    navController.navigate(Screen.ReadLater.route)
                                }
                            )
                        }
                        composable(Screen.Sources.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SourcesListScreen(
                                viewModel = sourcesViewModel,
                                onNavigateToSource = { source ->
                                    sourcesViewModel.selectedSource = source
                                    navController.navigate(Screen.SourceDetail.route)
                                },
                                onNavigateToEditSource = { source ->
                                    sourcesViewModel.selectedSource = source
                                    navController.navigate(Screen.SourceEdit.route)
                                },
                                onNavigateToAddSource = {
                                    navController.navigate(Screen.SourceUrlEditPreview.route)
                                },
                                onRefreshSuccess = {
                                    entriesViewModel.refreshPage(context)
                                }
                            )
                        }
                        composable(Screen.SourceDetail.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val scope = rememberCoroutineScope()
                            val config by AppConfigManager.config.collectAsState()
                            sourcesViewModel.selectedSource?.let { source ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SourceDetailScreen(
                                    source = source,
                                    onNavigateToEdit = {
                                        navController.navigate(Screen.SourceEdit.route)
                                    },
                                    onDelete = { deleteEntries ->
                                        source.id?.let { sourceId ->
                                            scope.launch {
                                                val (success, err) = SourceRepository.deleteSource(
                                                    context,
                                                    config.activeDatabaseState,
                                                    sourceId,
                                                    deleteEntries = deleteEntries
                                                )
                                                if (success) {
                                                    android.widget.Toast.makeText(context, "Source deleted", android.widget.Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                } else {
                                                    val msg = err ?: "Failed to delete source"
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    onBrowseEntries = { src ->
                                        val queryVal = if (src.id != null && src.id != 0L) "source_id = '${src.id}'" else "source_url = '${src.url}'"
                                        entriesViewModel.searchQuery = queryVal
                                        entriesViewModel.performSearch(context)
                                        navController.popBackStack(Screen.Home.route, false)
                                    },
                                    onRefreshSuccess = {
                                        entriesViewModel.refreshPage(context)
                                    },
                                    onSourceUpdated = { updated ->
                                        sourcesViewModel.selectedSource = updated
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.SourceEdit.route) {
                            sourcesViewModel.selectedSource?.let { source ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SourceEditScreen(
                                    source = source,
                                    onSourceUpdated = { updatedSource ->
                                        sourcesViewModel.selectedSource = updatedSource
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.SourceUrlEditPreview.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SourceUrlEditPreviewScreen(
                                onSourceAdded = { addedSource ->
                                    sourcesViewModel.selectedSource = addedSource
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.DatabaseDetail.route) {
                            val url = entriesViewModel.selectedDatabaseUrl
                            val config = AppConfigManager.config.collectAsState().value
                            // All databases including the default ("") are stored in config.databases.
                            // Fall back to selectedDatabaseState (snapshot passed at navigation time)
                            // in case the entry is not yet registered in the map.
                            val state = if (url != null) {
                                config.databases[url] ?: entriesViewModel.selectedDatabaseState
                            } else {
                                entriesViewModel.selectedDatabaseState
                            }
                            if (state != null) {
                                val isDefault = url == _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.DEFAULT_DATABASE_URL
                                val dbConfig = if (isDefault) config.defaultDbConfig else config.dbConfigs[url] ?: config.defaultDbConfig
                                val isActive = config.activeDatabaseUrl == url
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.DatabaseScreen(
                                    url = url,
                                    state = state,
                                    dbConfig = dbConfig,
                                    isActive = isActive,
                                    onBack = { navController.popBackStack() },
                                    onSetActive = { handleDatabaseChange(url, entriesViewModel, navController) }
                                )
                            }
                        }
                        composable(Screen.About.route) { _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.AboutScreen(onBack = { navController.popBackStack() }) }
                        composable(Screen.Options.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.OptionsScreen(
                                onNavigateToDatabases = {
                                    navController.navigate(Screen.Databases.route)
                                },
                                onNavigateToDatabaseDetail = { url, state ->
                                    entriesViewModel.selectedDatabaseUrl = url ?: _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.DEFAULT_DATABASE_URL
                                    entriesViewModel.selectedDatabaseState = state
                                    navController.navigate(Screen.DatabaseDetail.route)
                                },
                                onNavigateToPreselectedList = {
                                    navController.navigate(Screen.DatabasePreselectedList.route)
                                },
                                onNavigateToAbout = {
                                    navController.navigate(Screen.About.route)
                                },
                                onNavigateToLogs = {
                                    navController.navigate(Screen.AppLogging.route)
                                },
                                onNavigateToAdvanced = {
                                    navController.navigate(Screen.OptionsAdvanced.route)
                                },
                                onNavigateToLinkChecker = {
                                    navController.navigate(Screen.LinkChecker.route)
                                },
                                onSetActive = { url ->
                                    handleDatabaseChange(url, entriesViewModel, navController)
                                }
                            )
                        }
                        composable(Screen.AppLogging.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.AppLoggingScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.OptionsAdvanced.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.OptionsAdvancedScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.DatabasePreselectedList.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.DatabasePreselectedListScreen(
                                onDatabaseSelected = { dbUrl ->
                                    AppConfigManager.refreshDatabaseInBackground(context, dbUrl)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Detail.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            entriesViewModel.selectedEntry?.let { place ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryDetailScreen(
                                    entry = entriesViewModel.selectedEntry ?: place,
                                    onNavigateToLinkPreview = { url ->
                                        entriesViewModel.previewUrl = url
                                        navController.navigate(Screen.LinkPreview.route)
                                    },
                                    onNavigateToEdit = {
                                        navController.navigate(Screen.Edit.route)
                                    },
                                    onDelete = {
                                        entriesViewModel.deleteEntry(context, place) { success ->
                                            if (success) {
                                                entriesViewModel.selectedEntry = null
                                                navController.popBackStack()
                                            }
                                        }
                                    },
                                    onTagClick = { tag ->
                                        entriesViewModel.searchQuery = "tag LIKE '%$tag%'"
                                        entriesViewModel.performSearch(context)
                                        navController.popBackStack(Screen.Home.route, false)
                                    },
                                    onVisit = {
                                        entriesViewModel.recordVisit(context, place)
                                    },
                                    onSelectEntry = { targetEntry ->
                                        entriesViewModel.selectedEntry = targetEntry
                                    },
                                    onSelectSource = { source ->
                                        sourcesViewModel.selectedSource = source
                                        navController.navigate(Screen.SourceDetail.route)
                                    },
                                    onReadLaterChanged = { isNowBookmarked ->
                                         // Keep the in-memory list in sync so the bookmark icon
                                         // and alpha are correct immediately on back navigation.
                                         entriesViewModel.selectedEntry?.let { entry ->
                                             entriesViewModel.updateEntryBookmarked(entry, isNowBookmarked)
                                         }
                                         // Refresh list so that removing/adding a Read Later
                                         // entry is reflected immediately when the filter is active.
                                         if (entriesViewModel.isFilterReadLater) {
                                             entriesViewModel.refreshPage(context)
                                         }
                                     },
                                     onBack = {
                                         val cfg = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.value
                                         val isSortingByVisits = entriesViewModel.isFilterVisits ||
                                             (entriesViewModel.activeFilter == _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.EntrySearchFilter.None &&
                                                 cfg.dbconfig.orderBy == _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.OrderBy.PAGE_RATING_VISITS_DESC)
                                         if (isSortingByVisits) {
                                             entriesViewModel.refreshPage(context)
                                         }
                                         navController.popBackStack()
                                     }
                                )
                            }
                        }
                        composable(Screen.Edit.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            entriesViewModel.selectedEntry?.let { place ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryEditScreen(
                                    entry = place,
                                    onEntryUpdated = { updatedEntry ->
                                        entriesViewModel.selectedEntry = updatedEntry
                                        entriesViewModel.refreshPage(context)
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.EntryAdd.route) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryEditScreen(
                                entry = Entry(),
                                onEntryUpdated = { newEntry ->
                                    entriesViewModel.refreshPage(context)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.LinkPreview.route) {
                            entriesViewModel.previewUrl?.let { url ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.UrlStatusScreen(
                                    url = url,
                                    onNavigateToLinkData = {
                                        entriesViewModel.previewUrl = url
                                        navController.navigate(Screen.LinkData.route)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        composable(Screen.LinkData.route) {
                            entriesViewModel.previewUrl?.let { url ->
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.UrlPreviewScreen(
                                    url = url,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToDetail = { entry ->
                                        entriesViewModel.selectedEntry = entry
                                        navController.navigate(Screen.Detail.route)
                                    }
                                )
                            }
                        }
                        composable(Screen.LinkChecker.route) {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.UrlLinkCheckerScreen(
                                url = "",
                                onBack = { navController.popBackStack() },
                                onNavigateToDetail = { entry ->
                                    entriesViewModel.selectedEntry = entry
                                    navController.navigate(Screen.Detail.route)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles changing (activating) the selected database and resets screens to defaults.
     *
     * @param databaseUrl The URL/key of the database to activate.
     *   Pass [io.github.rumcajs.offlinewebsearch.data.DEFAULT_DATABASE_URL] ("") for the built-in default (Assets) database.
     * @param entriesViewModel The ViewModel to reset screen defaults on.
     * @param navController The NavController to reset backstack and routes back to defaults.
     */
    fun handleDatabaseChange(
        databaseUrl: String?,
        entriesViewModel: io.github.rumcajs.offlinewebsearch.ui.EntriesViewModel,
        navController: androidx.navigation.NavController? = null
    ) {
        AppConfigManager.setActiveDatabase(databaseUrl)
        entriesViewModel.resetToDefaults()

        // If the user changed the database from DatabaseDetail (or another sub-screen),
        // pop back to OptionsScreen so OptionsScreen remains the current screen.
        /* TODO this does not work
        navController?.let { nav ->
            val currentRoute = nav.currentDestination?.route
            if (currentRoute == Screen.DatabaseDetail.route) {
                nav.popBackStack()
            }
        }
        */
    }
}