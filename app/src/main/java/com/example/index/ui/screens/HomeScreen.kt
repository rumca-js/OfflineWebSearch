package com.example.index.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.index.data.AppConfigManager
import com.example.index.data.ViewStyle
import com.example.index.data.Place
import com.example.index.ui.components.RemoteImage
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.index.ui.SearchViewModel
import com.example.index.util.EntryUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: SearchViewModel = viewModel(),
    onNavigateToDetail: (Place) -> Unit = {}
) {
    val context = LocalContext.current

    // Load data once
    LaunchedEffect(Unit) {
        viewModel.loadDataIfNeeded(context)
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
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            
            // Reset scroll position when page changes
            LaunchedEffect(viewModel.currentPage) {
                listState.scrollToItem(0)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                items(filteredData) { place ->
                    PlaceItem(place, onNavigateToDetail)
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
fun PlaceItem(place: Place, onClick: (Place) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val config by AppConfigManager.config.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = place.link != null || !config.directLinks) {
                if (config.directLinks) {
                    place.link?.let { uriHandler.openUri(it) }
                } else {
                    onClick(place)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (config.viewStyle == ViewStyle.GALLERY && config.showIcons && place.thumbnail != null) {
                RemoteImage(
                    url = place.thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    showErrorText = false,
                    isRestricted = EntryUtils.isRestricted(place, config.userAge)
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
                        if (config.showIcons && (config.viewStyle != ViewStyle.GALLERY || place.thumbnail == null)) {
                            if (place.thumbnail != null) {
                                RemoteImage(
                                    url = place.thumbnail,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp),
                                    showErrorText = false,
                                    isRestricted = EntryUtils.isRestricted(place, config.userAge)
                                )
                            } else if (place.link != null) {
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
                            text = EntryUtils.getDisplayTitle(place, config.userAge),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    place.page_rating_votes?.let { votes ->
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
                EntryUtils.getDisplayDescription(place, config.userAge)?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        maxLines = if (config.viewStyle == ViewStyle.RSS) 2 else 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                if (config.viewStyle != ViewStyle.RSS) {
                    place.link?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                place.tags?.let { tags ->
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
                                        text = tag,
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
