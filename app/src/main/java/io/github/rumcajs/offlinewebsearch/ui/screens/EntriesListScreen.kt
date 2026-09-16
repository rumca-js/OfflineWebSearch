package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.ui.SearchFilter
import io.github.rumcajs.offlinewebsearch.ui.components.EntriesListSearchResultsContainer
import io.github.rumcajs.offlinewebsearch.ui.components.SearchContainer
import kotlinx.coroutines.launch

/**
 * Primary search screen.
 *
 * Displays a search text field, a Search button, and a small filter button
 * (FilterList icon) to the right of Search. The filter button opens a dropdown
 * menu with the following options:
 *  - Visited – restricts results to previously visited entries (shown when visit
 *    tracking is enabled)
 *  - Read Later – restricts results to entries saved for later reading (shown when
 *    the active database is writable)
 *  - By Date Published – sorts results by publication date
 *  - By Votes – sorts results by page-rating votes
 *  - By Visits – sorts results by visit count
 *
 * Selecting a filter immediately re-fetches data from the repository.
 * Selecting the already-active filter deactivates it (acts as a toggle).
 *
 * Results are paginated; navigation controls appear below the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntriesListScreen(
    viewModel: io.github.rumcajs.offlinewebsearch.ui.SearchViewModel = viewModel(),
    onNavigateToDetail: (Entry) -> Unit = {},
    onNavigateToAddEntry: (() -> Unit)? = null,
    onNavigateToVisited: (() -> Unit)? = null,
    onNavigateToReadLater: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = viewModel.listState
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    // Load data once; re-fetches automatically when database or config changes.
    LaunchedEffect(Unit) {
        viewModel.loadDataIfNeeded(context)
    }

    // Reset scroll position only when page or search query actually changes (not on initial composition).
    // TODO if filter changes - also scroll
    var previousPage by remember { mutableStateOf<Int?>(null) }
    var previousQuery by remember { mutableStateOf<String?>(null) }
    var previousFilter by remember { mutableStateOf<SearchFilter?>(null) }
    LaunchedEffect(viewModel.currentPage, viewModel.activeSearchQuery, viewModel.activeFilter) {
        if (previousPage != null && previousQuery != null && previousFilter != null &&
            (previousPage != viewModel.currentPage || previousQuery != viewModel.activeSearchQuery ||
                    previousFilter != viewModel.activeFilter)
        ) {
            listState.scrollToItem(0)
        }
        previousPage = viewModel.currentPage
        previousQuery = viewModel.activeSearchQuery
        previousFilter = viewModel.activeFilter
    }

    val filterOptions = remember(config.dbconfig.trackUserNavigation, isEditable) {
        SearchFilter.entryFilterOptions(
            showVisited = config.dbconfig.trackUserNavigation,
            showReadLater = isEditable
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        EntriesListSearchResultsContainer(
            isLoading = viewModel.isLoading,
            filteredData = viewModel.filteredData,
            activeSearchQuery = viewModel.activeSearchQuery,
            currentPage = viewModel.currentPage,
            totalPages = viewModel.totalPages,
            onPreviousPage = { viewModel.previousPage(context) },
            onNextPage = { viewModel.nextPage(context) },
            onNavigateToDetail = onNavigateToDetail,
            listState = listState,
            searchWidget = {
                SearchContainer(
                    searchQuery = viewModel.searchQuery,
                    onSearchQueryChange = {
                        viewModel.searchQuery = it
                        viewModel.showSuggestions = true
                    },
                    onClearSearch = {
                        viewModel.clearSearch()
                        viewModel.performSearch(context)
                    },
                    onPerformSearch = {
                        viewModel.performSearch(context)
                    },
                    isSearchButtonEnabled = viewModel.isSearchButtonEnabled,
                    filterOptions = filterOptions,
                    activeFilterKey = viewModel.activeFilter.takeIf { it != SearchFilter.None }?.name,
                    onFilterSelected = { option ->
                        viewModel.setFilter(context, SearchFilter.fromKey(option.key))
                        coroutineScope.launch {
                            listState.scrollToItem(0)
                        }
                    }
                )
            },
            showSuggestions = viewModel.showSuggestions,
            suggestions = viewModel.suggestions,
            onSuggestionClick = { suggestion ->
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.searchQuery = suggestion
                viewModel.performSearch(context)
                coroutineScope.launch {
                    listState.scrollToItem(0)
                }
            },
            onRefresh = { viewModel.performSearch(context) },
            modifier = Modifier.fillMaxSize()
        )

        val showAddEntry = isEditable && !viewModel.isFilterReadLater && onNavigateToAddEntry != null
        val showScrollToTop by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 0.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showAddEntry) {
                FloatingActionButton(
                    onClick = onNavigateToAddEntry!!,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Entry"
                    )
                }
            }

            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll to top"
                    )
                }
            }
        }
    }
}
