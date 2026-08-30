package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Describes a single option shown in the filter dropdown of [SearchContainer].
 *
 * @param key Unique identifier used to track the selected option.
 * @param label Display text shown in the menu item.
 * @param icon Leading icon for the menu item.
 */
data class FilterOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Search widget with a full-width text field, a "Search" button, and a small
 * filter button that opens a dropdown menu.
 *
 * The dropdown list is provided via [filterOptions], making this component
 * reusable for both [EntryListScreen] and [SourcesScreen] with different option
 * labels and semantics.
 *
 * The currently active option is highlighted in the menu and a badge appears on
 * the filter icon to signal that a filter is active. Selecting the already-active
 * option deactivates it (acts as a toggle).
 *
 * @param searchQuery Current value of the search text field.
 * @param onSearchQueryChange Called on every keystroke.
 * @param onClearSearch Called when the user taps the clear icon.
 * @param onPerformSearch Called when the user triggers a search (button or IME).
 * @param isSearchButtonEnabled Whether the "Search" button is enabled.
 * @param filterOptions List of options shown in the filter dropdown. Pass an
 *   empty list to hide the filter button entirely.
 * @param activeFilterKey The [FilterOption.key] of the currently active option,
 *   or null when no filter is active.
 * @param onFilterSelected Called with the selected [FilterOption] when the user
 *   picks an item. If the selected item is already active the caller is
 *   responsible for deactivating it.
 * @param modifier Optional [Modifier] applied to the root [Column].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContainer(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPerformSearch: () -> Unit,
    isSearchButtonEnabled: Boolean,
    filterOptions: List<FilterOption> = emptyList(),
    activeFilterKey: String? = null,
    onFilterSelected: (FilterOption) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var filterMenuExpanded by remember { mutableStateOf(false) }

    val executeSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onPerformSearch()
    }

    Column(modifier = modifier) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Input search text...") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Input search text...") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { executeSearch() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = executeSearch,
                modifier = Modifier.weight(1f),
                enabled = isSearchButtonEnabled
            ) {
                Text("Search")
            }

            if (filterOptions.isNotEmpty()) {
                Box {
                    val filterActive = activeFilterKey != null
                    IconButton(onClick = { filterMenuExpanded = true }) {
                        BadgedBox(
                            badge = { if (filterActive) Badge() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = if (filterActive)
                                    "Filter active"
                                else
                                    "Filter results"
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        filterOptions.forEach { option ->
                            val selected = option.key == activeFilterKey
                            FilterMenuItem(
                                icon = option.icon,
                                label = option.label,
                                selected = selected,
                                onClick = {
                                    filterMenuExpanded = false
                                    onFilterSelected(option)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single row in the filter dropdown.
 *
 * @param icon Leading icon representing the filter option.
 * @param label Display text for this option.
 * @param selected Whether this option is currently active.
 * @param onClick Called when the user taps this item.
 */
@Composable
private fun FilterMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null)
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Active filter",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = onClick,
        colors = if (selected) {
            MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.primary,
                leadingIconColor = MaterialTheme.colorScheme.primary
            )
        } else {
            MenuDefaults.itemColors()
        }
    )
}
