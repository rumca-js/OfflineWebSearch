package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.ui.SOURCE_FILTER_OPTIONS
import io.github.rumcajs.offlinewebsearch.ui.SourcesViewModel
import io.github.rumcajs.offlinewebsearch.ui.components.SearchContainer
import io.github.rumcajs.offlinewebsearch.ui.components.SourceListItem
import kotlinx.coroutines.launch

/**
 * Screen displaying the list of RSS/feed sources from `sourcedatamodel`.
 *
 * Supports pull-to-refresh to reload the source list from the active database.
 *
 * The search widget is the first item inside a [LazyColumn] so that it scrolls
 * together with the source list — consistent with [EntriesListScreen].
 *
 * The widget uses the shared [SearchContainer] component:
 *  - Full-width text field
 *  - "Search" button that applies the current query
 *  - Filter icon button opening a dropdown with "By Url", "By Title", and "By Fetch Time"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesListScreen(
    viewModel: SourcesViewModel = viewModel(),
    onNavigateToSource: (Source) -> Unit,
    onNavigateToEditSource: (Source) -> Unit,
    onNavigateToAddSource: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    /** Called when a source refresh worker run finishes with at least one fetched source. */
    onRefreshSuccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    val listState = viewModel.listState
    var sourceToDelete by remember { mutableStateOf<Source?>(null) }
    var deleteEntriesWithSource by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadDataIfNeeded(context)
    }

    fun getSourcesEmptyText(): String {
        if (isEditable) {
            return "No sources available in current database. Feeds and RSS sources can be added via the add button."
        }
        return "Database is read-only. Cannot edit sources"
    }

    if (sourceToDelete != null) {
        val source = sourceToDelete!!
        AlertDialog(
            onDismissRequest = {
                sourceToDelete = null
                deleteEntriesWithSource = false
            },
            title = { Text("Delete Source") },
            text = {
                Column {
                    Text("Are you sure you want to delete source '${source.title.ifBlank { "Untitled" }}'?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteEntriesWithSource = !deleteEntriesWithSource }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteEntriesWithSource,
                            onCheckedChange = { deleteEntriesWithSource = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Also delete all entries from this source",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = sourceToDelete
                        val shouldDeleteEntries = deleteEntriesWithSource
                        sourceToDelete = null
                        deleteEntriesWithSource = false
                        if (target?.id != null) {
                            viewModel.deleteSource(context, target.id, shouldDeleteEntries) { success, err ->
                                if (success) {
                                    Toast.makeText(context, "Source deleted", Toast.LENGTH_SHORT).show()
                                } else {
                                    val msg = err ?: "Failed to delete source"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    sourceToDelete = null
                    deleteEntriesWithSource = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.loadSources(context) },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (isEditable) {
                    item(key = "search_widget") {
                        SearchContainer(
                            searchQuery = viewModel.searchQuery,
                            onSearchQueryChange = { viewModel.searchQuery = it },
                            onClearSearch = {
                                viewModel.clearSearch()
                            },
                            onPerformSearch = {
                                viewModel.performSearch(context)
                                scope.launch {
                                    listState.scrollToItem(0)
                                }
                            },
                            isSearchButtonEnabled = viewModel.isSearchButtonEnabled,
                            filterOptions = SOURCE_FILTER_OPTIONS,
                            activeFilterKey = viewModel.activeFilterKey,
                            onFilterSelected = { option ->
                                viewModel.setFilter(option, context)
                                scope.launch {
                                    listState.scrollToItem(0)
                                }
                            },
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item(key = "readonly_banner") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Database is read-only. Editing is disabled.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                when {
                    viewModel.isLoading && viewModel.filteredSources.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    viewModel.filteredSources.isEmpty() && viewModel.activeSearchQuery.isNotBlank() -> {
                        item {
                            Text(
                                text = "No matching sources found.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    viewModel.filteredSources.isEmpty() -> {
                        item {
                            Text(
                                text = getSourcesEmptyText(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        items(viewModel.filteredSources, key = { it.source.id ?: it.source.url }) { item ->
                            SourceListItem(
                                source = item.source,
                                operationalData = item.operationalData,
                                activeDbState = activeDbState,
                                isEditable = isEditable,
                                onClick = { onNavigateToSource(item.source) },
                                onEditClick = { onNavigateToEditSource(item.source) },
                                onDeleteClick = { sourceToDelete = item.source },
                                config = config,
                                isRefreshing = viewModel.isRefreshingAll
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            val showAddSource = isEditable && onNavigateToAddSource != null
            val showScrollToTop by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (viewModel.hasOutdatedSources && !viewModel.isRefreshingAll) {
                    FloatingActionButton(
                        onClick = {
                            if (!viewModel.isRefreshingAll) {
                                viewModel.refreshAll(context) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Fetch all sources")
                    }
                }

                if (showAddSource) {
                    FloatingActionButton(
                        onClick = onNavigateToAddSource!!,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Source"
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
                            scope.launch {
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
}
