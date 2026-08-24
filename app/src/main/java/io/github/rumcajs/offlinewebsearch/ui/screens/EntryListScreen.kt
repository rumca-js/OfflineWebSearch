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
import io.github.rumcajs.offlinewebsearch.ui.components.SearchContainer
import io.github.rumcajs.offlinewebsearch.ui.components.SearchResultsContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryListScreen(
    viewModel: io.github.rumcajs.offlinewebsearch.ui.SearchViewModel = viewModel(),
    onNavigateToDetail: (io.github.rumcajs.offlinewebsearch.data.Entry) -> Unit = {},
    onNavigateToAddEntry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly && activeDbState.extension == ".db"

    // Load data once
    LaunchedEffect(Unit) {
        viewModel.loadDataIfNeeded(context)
    }

    // Reset scroll position when page or search query changes
    LaunchedEffect(viewModel.currentPage, viewModel.activeSearchQuery) {
        listState.scrollToItem(0)
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
            isSearchButtonEnabled = viewModel.isSearchButtonEnabled
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
            onAddEntry = if (isEditable && onNavigateToAddEntry != null) onNavigateToAddEntry else null,
            onRefresh = { viewModel.performSearch(context) },
            modifier = Modifier.weight(1f)
        )
    }
}
