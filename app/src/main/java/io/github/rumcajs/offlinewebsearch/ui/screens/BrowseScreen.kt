package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.ui.components.RemoteImage
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
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

    val searchQuery = viewModel.searchQuery
    val isLoading = viewModel.isLoading
    val filteredData = viewModel.filteredData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextField(
            value = searchQuery,
            onValueChange = { 
                viewModel.searchQuery = it
                viewModel.showSuggestions = true
            },
            label = { Text("Input search text...") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Input search text...") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        viewModel.clearSearch()
                    }) {
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
                    viewModel.performSearch()
                }
            )
        )

        if (viewModel.showSuggestions && viewModel.suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Column {
                    viewModel.suggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.searchQuery = suggestion
                                    viewModel.performSearch()
                                    coroutineScope.launch {
                                        listState.scrollToItem(0)
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(suggestion)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.performSearch() },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.isSearchButtonEnabled
        ) {
            Text("Search")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pagination Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { viewModel.previousPage() },
                enabled = viewModel.currentPage > 0
            ) {
                Text("Previous")
            }
            Text(
                text = "Page ${viewModel.currentPage + 1} of ${viewModel.totalPages}",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = { viewModel.nextPage() },
                enabled = (viewModel.currentPage + 1) < viewModel.totalPages
            ) {
                Text("Next")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val activeSearchQuery = viewModel.activeSearchQuery
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                items(filteredData) { entry ->
                    _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.EntryItem(
                        entry,
                        onNavigateToDetail
                    )
                }
                if (activeSearchQuery.isNotEmpty() && filteredData.isEmpty()) {
                    item {
                        Text("No results found for \"$activeSearchQuery\"")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryItem(entry: io.github.rumcajs.offlinewebsearch.data.Entry, onClick: (io.github.rumcajs.offlinewebsearch.data.Entry) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val config by _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState()
    val isDead = !entry.date_dead_since.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (isDead) 0.5f else 1f)
            .clickable(enabled = entry.link != null || !config.dbconfig.directLinks) {
                if (config.dbconfig.directLinks) {
                    entry.link?.let { uriHandler.openUri(it) }
                } else {
                    onClick(entry)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (config.dbconfig.viewStyle == io.github.rumcajs.offlinewebsearch.data.ViewStyle.GALLERY && config.dbconfig.showIcons && entry.thumbnail != null) {
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.components.RemoteImage(
                    url = entry.thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    showErrorText = false,
                    isRestricted = _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.isRestricted(
                        entry,
                        config.userAge
                    )
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        if (config.dbconfig.showIcons && (config.dbconfig.viewStyle != io.github.rumcajs.offlinewebsearch.data.ViewStyle.GALLERY || entry.thumbnail == null)) {
                            if (entry.thumbnail != null) {
                                _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.components.RemoteImage(
                                    url = entry.thumbnail,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp),
                                    showErrorText = false,
                                    isRestricted = _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.isRestricted(
                                        entry,
                                        config.userAge
                                    )
                                )
                            } else if (entry.link != null) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.getDisplayTitle(entry, config.userAge),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (entry.bookmarked == true) {
                            Text(
                                text = "📌",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        if (isDead) {
                            Text(
                                text = "💀",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        entry.page_rating_votes?.let { votes ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Text(
                                    text = "⭐ $votes",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                
                val isRestricted = io.github.rumcajs.offlinewebsearch.util.EntryUtils.isRestricted(entry, config.userAge)
                
                if (config.dbconfig.viewStyle == io.github.rumcajs.offlinewebsearch.data.ViewStyle.SEARCH_ENGINE) {
                    entry.link?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRestricted) "xXx" else it,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (config.dbconfig.viewStyle == io.github.rumcajs.offlinewebsearch.data.ViewStyle.STANDARD || config.dbconfig.viewStyle == io.github.rumcajs.offlinewebsearch.data.ViewStyle.GALLERY) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        entry.date_published?.let {
                            Text(
                                text = if (isRestricted) "xXx" else _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.getFormattedDate(it),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        entry.author?.let {
                            Text(
                                text = if (isRestricted) "xXx" else it,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                entry.language?.let { lang ->
                    if (lang.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRestricted) "xXx" else lang,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                entry.tags?.let { tags ->
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isRestricted) "xXx" else tag,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
