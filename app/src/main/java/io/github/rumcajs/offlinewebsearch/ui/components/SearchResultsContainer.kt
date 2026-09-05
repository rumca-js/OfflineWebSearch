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
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsContainer(
    isLoading: Boolean,
    filteredData: List<Entry>,
    activeSearchQuery: String,
    currentPage: Int,
    totalPages: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onNavigateToDetail: (Entry) -> Unit,
    listState: LazyListState,
    showSuggestions: Boolean = false,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    onAddEntry: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val swipeThreshold = 50f
    var totalDragX by remember { mutableFloatStateOf(0f) }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { onRefresh?.invoke() },
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Scrollable entry list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(currentPage, totalPages) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onDragEnd = {
                                    if (totalDragX > swipeThreshold) {
                                        // Swipe right -> Previous page
                                        if (currentPage > 0) {
                                            onPreviousPage()
                                        }
                                    } else if (totalDragX < -swipeThreshold) {
                                        // Swipe left -> Next page (older/next results)
                                        if (currentPage + 1 < totalPages) {
                                            onNextPage()
                                        }
                                    }
                                    totalDragX = 0f
                                },
                                onDragCancel = { totalDragX = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDragX += dragAmount
                                }
                            )
                        },
                    state = listState
                ) {
                    if (showSuggestions && suggestions.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 4.dp
                            ) {
                                Column {
                                    suggestions.forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSuggestionClick(suggestion) }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(suggestion)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    items(
                        items = filteredData,
                        key = { entry -> entry.id ?: entry.link ?: entry.hashCode() }
                    ) { entry ->
                        EntryItem(
                            entry = entry,
                            onClick = onNavigateToDetail
                        )
                    }
                    if (activeSearchQuery.isNotEmpty() && filteredData.isEmpty()) {
                        item {
                            Text("No results found for \"$activeSearchQuery\"")
                        }
                    }

                    // Pagination — scrollable, after search results
                    if (filteredData.isNotEmpty() && totalPages > 1) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onPreviousPage,
                                    enabled = currentPage > 0
                                ) {
                                    Text("Previous")
                                }

                                Text(
                                    text = "Page ${currentPage + 1} of $totalPages",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                TextButton(
                                    onClick = onNextPage,
                                    enabled = (currentPage + 1) < totalPages
                                ) {
                                    Text("Next")
                                }
                            }
                        }
                    }

                    // Add entry button — scrollable, after pagination
                    if (onAddEntry != null) {
                        item {
                            Button(
                                onClick = onAddEntry,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Add Entry")
                            }
                        }
                    }
                }
            }
            if (isLoading && filteredData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
