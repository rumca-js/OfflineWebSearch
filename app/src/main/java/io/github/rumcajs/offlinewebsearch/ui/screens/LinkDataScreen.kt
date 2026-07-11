package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.util.NetworkUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.Page
import io.github.rumcajs.offlinewebsearch.webtoolkit.PageBuilder
import io.github.rumcajs.offlinewebsearch.webtoolkit.HtmlPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalUriHandler
import io.github.rumcajs.offlinewebsearch.ui.components.RemoteImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDataScreen(
    url: String,
    onBack: () -> Unit,
    onNavigateToDetail: (Entry) -> Unit
) {
    val config by io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf<Page?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(url, refreshTrigger) {
        isLoading = true
        error = null
        val response = NetworkUtils.downloadAll(url)
        if (response.statusCode in 200..299 && response.text != null) {
            val body = response.text
            val inputType = if (url.contains(".html") || url.contains(".htm") ||
                body.trim().startsWith("<html", ignoreCase = true) ||
                body.trim().contains("<!doctype html", ignoreCase = true)) {
                "html"
            } else {
                "rss"
            }
            try {
                page = PageBuilder.build(url, body, inputType)
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Failed to parse page"
                page = null
            }
        } else {
            error = response.error ?: "Failed to download content"
            page = null
        }
        isLoading = false
    }

    val titleText = when (page) {
        is HtmlPage -> "Web Page"
        is RssPage -> "Feed Data"
        else -> "Page Data"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading page…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                error != null && page == null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Failed to load page",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }

                page == null -> {
                    Text(
                        text = "No content loaded.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                page is HtmlPage -> {
                    HtmlPageDetails(
                        page = page as HtmlPage,
                        url = url,
                        showIcons = config.showIcons
                    )
                }

                else -> {
                    val entries = page?.getEntries() ?: emptyList()
                    if (entries.isEmpty()) {
                        Text(
                            text = "No entries found in feed.",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                // Feed source card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Feed source",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${entries.size} entries",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (error != null) {
                                item {
                                    Text(
                                        text = "⚠ Partial load: $error",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            items(entries) { entry ->
                                FeedEntryCard(
                                    entry = entry,
                                    showIcons = config.showIcons,
                                    onClick = { onNavigateToDetail(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HtmlPageDetails(page: HtmlPage, url: String, showIcons: Boolean) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Section or Main image
        val thumbnails = page.getThumbnails()
        if (showIcons && thumbnails.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                RemoteImage(
                    url = thumbnails.first(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Title
        val title = page.getTitle() ?: "Untitled Page"
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Date published badge
        val datePublished = page.getDatePublished()
        if (!datePublished.isNullOrBlank()) {
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = "Published: $datePublished",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }

        // Description Card
        val description = page.getDescription()
        if (!description.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // Source Link
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Source Link",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Button(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Open in Browser", fontSize = 12.sp)
                }
            }
        }

        // Gallery of other thumbnails if more than 1
        if (showIcons && thumbnails.size > 1) {
            Text(
                text = "Thumbnails",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(thumbnails.drop(1)) { imageUrl ->
                    Card(
                        modifier = Modifier
                            .size(120.dp, 80.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        RemoteImage(
                            url = imageUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun FeedEntryCard(entry: Entry, showIcons: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail
            if (showIcons && entry.thumbnail != null) {
                RemoteImage(
                    url = entry.thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                    contentScale = ContentScale.Crop,
                    isRestricted = false
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                // Title
                if (entry.title != null) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Author + date
                val meta = listOfNotNull(entry.author, entry.date_published).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Description
                if (entry.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Link
                if (entry.link != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.link,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
