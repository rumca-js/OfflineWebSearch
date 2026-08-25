package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Component pane displaying search filter chips (e.g. Visited, Read Later).
 */
@Composable
fun SearchFilterChipsPane(
    showVisitedChip: Boolean,
    isFilterVisited: Boolean,
    onToggleVisited: () -> Unit,
    showReadLaterChip: Boolean,
    isFilterReadLater: Boolean,
    onToggleReadLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showVisitedChip && !showReadLaterChip) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showVisitedChip) {
            FilterChip(
                selected = isFilterVisited,
                onClick = onToggleVisited,
                label = { Text("Visited") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Visited Entries",
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
        if (showReadLaterChip) {
            FilterChip(
                selected = isFilterReadLater,
                onClick = onToggleReadLater,
                label = { Text("Read Later") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Read Later",
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
    }
}
