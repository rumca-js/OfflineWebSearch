package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.ui.SearchFilter
import io.github.rumcajs.offlinewebsearch.ui.components.SearchContainer
import io.github.rumcajs.offlinewebsearch.ui.components.SearchResultsContainer

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
fun EntryListScreen(
    viewModel: io.github.rumcajs.offlinewebsearch.ui.SearchViewModel = viewModel(),
    onNavigateToDetail: (io.github.rumcajs.offlinewebsearch.data.Entry) -> Unit = {},
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
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
        SearchResultsContainer(
            isLoading = viewModel.isLoading,
            filteredData = viewModel.filteredData,
            activeSearchQuery = viewModel.activeSearchQuery,
            currentPage = viewModel.currentPage,
            totalPages = viewModel.totalPages,
            onPreviousPage = { viewModel.previousPage(context) },
            onNextPage = { viewModel.nextPage(context) },
            onNavigateToDetail = onNavigateToDetail,
            listState = listState,
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
            onAddEntry = if (isEditable && !viewModel.isFilterReadLater && onNavigateToAddEntry != null) onNavigateToAddEntry else null,
            onRefresh = { viewModel.performSearch(context) },
            modifier = Modifier.weight(1f)
        )
    }
}
