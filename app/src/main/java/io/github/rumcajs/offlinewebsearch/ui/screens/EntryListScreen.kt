package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rumcajs.offlinewebsearch.ui.components.SearchContainer
import io.github.rumcajs.offlinewebsearch.ui.components.SearchResultsContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryListScreen(
    viewModel: io.github.rumcajs.offlinewebsearch.ui.SearchViewModel = viewModel(),
    onNavigateToDetail: (io.github.rumcajs.offlinewebsearch.data.Entry) -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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
            .padding(16.dp)
    ) {
        SearchContainer(
            searchQuery = viewModel.searchQuery,
            onSearchQueryChange = {
                viewModel.searchQuery = it
                viewModel.showSuggestions = true
            },
            onClearSearch = {
                viewModel.clearSearch()
            },
            onPerformSearch = {
                viewModel.performSearch()
            },
            isSearchButtonEnabled = viewModel.isSearchButtonEnabled,
            showSuggestions = viewModel.showSuggestions,
            suggestions = viewModel.suggestions,
            onSuggestionClick = { suggestion ->
                viewModel.searchQuery = suggestion
                viewModel.performSearch()
                coroutineScope.launch {
                    listState.scrollToItem(0)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SearchResultsContainer(
            isLoading = viewModel.isLoading,
            filteredData = viewModel.filteredData,
            activeSearchQuery = viewModel.activeSearchQuery,
            currentPage = viewModel.currentPage,
            totalPages = viewModel.totalPages,
            onPreviousPage = { viewModel.previousPage() },
            onNextPage = { viewModel.nextPage() },
            onNavigateToDetail = onNavigateToDetail,
            listState = listState,
            modifier = Modifier.weight(1f)
        )
    }
}

