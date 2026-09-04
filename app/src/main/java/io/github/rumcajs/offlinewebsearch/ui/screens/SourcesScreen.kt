package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.rumcajs.offlinewebsearch.ui.components.FilterOption
import io.github.rumcajs.offlinewebsearch.ui.components.LinkText
import io.github.rumcajs.offlinewebsearch.ui.components.SearchContainer
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Source
import io.github.rumcajs.offlinewebsearch.data.SourceRepository
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker
import kotlinx.coroutines.launch

/** Key constants for [SourcesScreen] filter dropdown options. */
private const val FILTER_KEY_BY_URL = "by_url"
private const val FILTER_KEY_BY_TITLE = "by_title"
private const val FILTER_KEY_BY_FETCH_TIME = "by_fetch_time"

/** Sort mode applied to the in-memory source list. */
private enum class SourceOrder { ByUrl, ByTitle, ByFetchTime }

/** [FilterOption] list shown in the [SearchContainer] dropdown for [SourcesScreen]. */
private val SOURCE_FILTER_OPTIONS = listOf(
    FilterOption(
        key = FILTER_KEY_BY_URL,
        label = "By Url",
        icon = Icons.Default.SortByAlpha
    ),
    FilterOption(
        key = FILTER_KEY_BY_TITLE,
        label = "By Title",
        icon = Icons.Default.SortByAlpha
    ),
    FilterOption(
        key = FILTER_KEY_BY_FETCH_TIME,
        label = "By Fetch Time",
        icon = Icons.Default.DateRange
    )
)

/**
 * Screen displaying the list of RSS/feed sources from `sourcedatamodel`.
 *
 * Supports pull-to-refresh to reload the source list from the active database.
 *
 * The search widget is the first item inside a [LazyColumn] so that it scrolls
 * together with the source list — consistent with [EntryListScreen].
 *
 * The widget uses the shared [SearchContainer] component:
 *  - Full-width text field
 *  - "Search" button that applies the current query (in-memory filter)
 *  - Filter icon button opening a dropdown with "By Url", "By Title", and "By Fetch Time"
 *
 * By default, "By Url" filter is applied. A filter is always applied.
 * Selecting a filter immediately re-sorts the list; no "Search" press is needed.
 * Selecting an active non-default filter resets back to "By Url".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onNavigateToSource: (Source) -> Unit,
    onNavigateToEditSource: (Source) -> Unit,
    onNavigateToAddSource: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    // Raw search input (typing in the text field).
    var searchQuery by remember { mutableStateOf("") }
    // The query that was last submitted via the Search button.
    var activeSearchQuery by remember { mutableStateOf("") }
    var sourceOrder by remember { mutableStateOf(SourceOrder.ByUrl) }
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshingAll by remember { mutableStateOf(false) }
    var sourceToDelete by remember { mutableStateOf<Source?>(null) }

    val loadSources: () -> Unit = {
        scope.launch {
            isLoading = true
            sources = SourceRepository.getAllSources(context, activeDbState)
            isLoading = false
        }
    }

    LaunchedEffect(config.activeDatabase) {
        loadSources()
    }

    fun getSourcesEmptyText() : String
    {
        if (isEditable) {
            return "No sources available in current database. Feeds and RSS sources can be added via the add button."
        }
        return "Database is read-only. Cannot edit sources"
    }

    /**
     * Applies [activeSearchQuery] and [sourceOrder] to [sources] to produce the
     * displayed list.
     */
    val filteredSources = remember(sources, activeSearchQuery, sourceOrder) {
        val base = if (activeSearchQuery.isBlank()) {
            sources
        } else {
            val query = activeSearchQuery.trim().lowercase()
            sources.filter { source ->
                source.title.lowercase().contains(query) ||
                    source.url.lowercase().contains(query)
            }
        }
        when (sourceOrder) {
            SourceOrder.ByUrl -> base.sortedWith(compareBy<Source> { it.url.lowercase() }.thenBy { it.title.lowercase() })
            SourceOrder.ByTitle -> base.sortedWith(compareBy<Source> { it.title.lowercase() }.thenBy { it.url.lowercase() })
            // Fetch time is stored in a separate table; sort by id as an insertion-order proxy.
            SourceOrder.ByFetchTime -> base.sortedBy { it.id ?: Long.MAX_VALUE }
        }
    }

    /** Whether the Search button should be enabled (query differs from active query). */
    val isSearchButtonEnabled = searchQuery != activeSearchQuery

    /** Key of the currently active filter option. */
    val activeFilterKey: String? = when (sourceOrder) {
        SourceOrder.ByUrl -> FILTER_KEY_BY_URL
        SourceOrder.ByTitle -> FILTER_KEY_BY_TITLE
        SourceOrder.ByFetchTime -> FILTER_KEY_BY_FETCH_TIME
    }

    /** Called when the user selects an option from the filter dropdown. */
    val onFilterSelected: (FilterOption) -> Unit = { option ->
        sourceOrder = when (option.key) {
            FILTER_KEY_BY_URL -> SourceOrder.ByUrl
            FILTER_KEY_BY_TITLE ->
                if (sourceOrder == SourceOrder.ByTitle) SourceOrder.ByUrl else SourceOrder.ByTitle
            FILTER_KEY_BY_FETCH_TIME ->
                if (sourceOrder == SourceOrder.ByFetchTime) SourceOrder.ByUrl else SourceOrder.ByFetchTime
            else -> SourceOrder.ByUrl
        }
    }

    val performRefreshAll: () -> Unit = {
        if (config.networkConfig.disabled) {
            Toast.makeText(context, "Network operations are disabled", Toast.LENGTH_SHORT).show()
        } else if (activeDbState == null || activeDbState.isReadOnly || !activeDbState.isSQLite) {
            Toast.makeText(context, "Active database is read-only or not writable", Toast.LENGTH_SHORT).show()
        } else if (sources.isEmpty()) {
            Toast.makeText(context, "No sources to fetch", Toast.LENGTH_SHORT).show()
        } else {
            isRefreshingAll = true
            scope.launch {
                val orderedSources = SourceRepository.getSourcesByFetchTime(context, activeDbState)
                SourceRefreshWorker.enqueueSources(
                    context = context,
                    dbState = activeDbState,
                    sources = orderedSources,
                    onFinished = { fetchedCount ->
                        scope.launch {
                            sources = SourceRepository.getSourcesByFetchTime(context, activeDbState)
                            isRefreshingAll = false
                            Toast.makeText(context, "Refreshed $fetchedCount source(s)", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    if (sourceToDelete != null) {
        val source = sourceToDelete!!
        AlertDialog(
            onDismissRequest = { sourceToDelete = null },
            title = { Text("Delete Source") },
            text = { Text("Are you sure you want to delete source '${source.title.ifBlank { "Untitled" }}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = sourceToDelete
                        sourceToDelete = null
                        if (target?.id != null) {
                            scope.launch {
                                val (success, err) = SourceRepository.deleteSource(context, activeDbState, target.id)
                                if (success) {
                                    Toast.makeText(context, "Source deleted", Toast.LENGTH_SHORT).show()
                                    sources = SourceRepository.getAllSources(context, activeDbState)
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
                TextButton(onClick = { sourceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isEditable && onNavigateToAddSource != null) {
                        IconButton(onClick = onNavigateToAddSource) {
                            Icon(Icons.Default.Add, contentDescription = "Add Source")
                        }
                    }
                    if (isEditable && !config.networkConfig.disabled && sources.isNotEmpty()) {
                        IconButton(
                            onClick = performRefreshAll,
                            enabled = !isRefreshingAll
                        ) {
                            if (isRefreshingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Fetch all sources")
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = loadSources,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // The search widget is the first item in the LazyColumn so it scrolls
            // together with the source list — consistent with EntryListScreen.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (isEditable) {
                    item(key = "search_widget") {
                        SearchContainer(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onClearSearch = {
                                searchQuery = ""
                                activeSearchQuery = ""
                            },
                            onPerformSearch = { activeSearchQuery = searchQuery },
                            isSearchButtonEnabled = isSearchButtonEnabled,
                            filterOptions = SOURCE_FILTER_OPTIONS,
                            activeFilterKey = activeFilterKey,
                            onFilterSelected = onFilterSelected,
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
                    filteredSources.isEmpty() -> {
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
                    else -> {
                        items(filteredSources, key = { it.id ?: it.url }) { source ->
                            SourceItemRow(
                                source = source,
                                isEditable = isEditable,
                                onClick = { onNavigateToSource(source) },
                                onEditClick = { onNavigateToEditSource(source) },
                                onDeleteClick = { sourceToDelete = source }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceItemRow(
    source: Source,
    isEditable: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (source.url.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(source.url))
                        Toast.makeText(context, "Source URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail / favicon
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (source.favicon.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(source.favicon)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumbnail for ${source.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinkText(
                        text = source.favicon,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = source.title.ifBlank { "Untitled Source" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // URL
                if (source.url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinkText(text = source.url)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons & status chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(if (source.enabled) "Enabled" else "Disabled")
                        }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Source",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            enabled = isEditable
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Source",
                                tint = if (isEditable) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    }
}
