package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContainer(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPerformSearch: () -> Unit,
    isSearchButtonEnabled: Boolean,
    modifier: Modifier = Modifier
) {
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
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onPerformSearch()
                }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onPerformSearch,
            modifier = Modifier.fillMaxWidth(),
            enabled = isSearchButtonEnabled
        ) {
            Text("Search")
        }
    }
}
