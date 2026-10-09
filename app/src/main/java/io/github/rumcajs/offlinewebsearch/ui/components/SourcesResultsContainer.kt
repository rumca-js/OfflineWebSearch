package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceWithOperationalData

/**
 * Container component for displaying a scrollable list of sources.
 *
 * Supports pull-to-refresh, slot for search widget, progress indicator for source refresh,
 * read-only banners, and empty state representations.
 *
 * @param isLoading Whether sources are currently loading.
 * @param sources List of sources with operational metadata to display.
 * @param activeSearchQuery The active search term applied to the list.
 * @param isEditable Whether the active database allows editing sources.
 * @param activeDbState The active database state.
 * @param config Application configuration.
 * @param isRefreshingAll Whether a refresh of all sources is in progress.
 * @param listState Scroll state for the underlying [LazyColumn].
 * @param searchWidget Optional composable slot for rendering the search widget at the top of the list.
 * @param progressPane Optional composable slot for rendering the source refresh progress indicator. Defaults to [SourceRefreshProgressPane].
 * @param onRefresh Callback triggered when pull-to-refresh is executed.
 * @param onNavigateToSource Callback when a source item is clicked to view details.
 * @param onNavigateToEditSource Callback when a source item's edit action is clicked.
 * @param onDeleteClick Callback when a source item's delete action is clicked.
 * @param modifier Optional [Modifier] for configuring the container layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesContainer(
    isLoading: Boolean,
    sources: List<SourceWithOperationalData>,
    activeSearchQuery: String = "",
    isEditable: Boolean = true,
    activeDbState: DatabaseState? = null,
    config: AppConfiguration? = null,
    isRefreshingAll: Boolean = false,
    listState: LazyListState,
    searchWidget: (@Composable () -> Unit)? = null,
    progressPane: (@Composable () -> Unit)? = { SourceRefreshProgressPane() },
    onRefresh: () -> Unit = {},
    onNavigateToSource: (Source) -> Unit,
    onNavigateToEditSource: (Source) -> Unit,
    onDeleteClick: (Source) -> Unit,
    modifier: Modifier = Modifier
) {
    fun getSourcesEmptyText(): String {
        if (isEditable) {
            return "No sources available in current database. Feeds and RSS sources can be added via the add button."
        }
        return "Database is read-only. Cannot edit sources"
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (isEditable) {
                if (searchWidget != null) {
                    item(key = "search_widget") {
                        searchWidget()
                    }
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

            if (progressPane != null) {
                item(key = "source_refresh_progress") {
                    progressPane()
                }
            }

            when {
                isLoading && sources.isEmpty() -> {
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
                sources.isEmpty() && activeSearchQuery.isNotBlank() -> {
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
                sources.isEmpty() -> {
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
                    items(sources, key = { it.source.id ?: it.source.url }) { item ->
                        SourceListItem(
                            source = item.source,
                            operationalData = item.operationalData,
                            activeDbState = activeDbState,
                            isEditable = isEditable,
                            onClick = { onNavigateToSource(item.source) },
                            onEditClick = { onNavigateToEditSource(item.source) },
                            onDeleteClick = { onDeleteClick(item.source) },
                            config = config,
                            isRefreshing = isRefreshingAll
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
