package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.ui.components.EntryItem

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History

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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Scrollable entry list
            LazyColumn(
                modifier = Modifier.weight(1f),
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
                items(filteredData) { entry ->
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
    }
}
