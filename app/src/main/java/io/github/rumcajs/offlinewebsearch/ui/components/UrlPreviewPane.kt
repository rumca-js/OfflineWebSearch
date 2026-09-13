package io.github.rumcajs.offlinewebsearch.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.HtmlPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.Page
import io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url

/**
 * Reusable pane for fetching and displaying the preview of a web page or RSS feed.
 *
 * Handles network loading, parsing HTML/RSS page content, error states, and rendering
 * metadata, thumbnail galleries, or feed entry lists. Optionally displays HTTP response info.
 *
 * @param url The URL to fetch and preview.
 * @param modifier Modifier for container layout and sizing.
 * @param refreshTrigger Trigger value to force-refresh data on change.
 * @param showResponseInfo Whether to display the HTTP response info pane (status code, length, headers).
 * @param onLoadingChanged Callback invoked when loading state changes.
 * @param onPageLoaded Callback invoked when page data is fetched or cleared.
 * @param onResponseLoaded Callback invoked when raw HTTP response is fetched or cleared.
 * @param onNavigateToDetail Callback invoked when a feed entry is clicked.
 */
@Composable
fun UrlPreviewPane(
    url: String,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    showResponseInfo: Boolean = false,
    onLoadingChanged: (Boolean) -> Unit = {},
    onPageLoaded: (Page?) -> Unit = {},
    onResponseLoaded: (PageResponseObject?) -> Unit = {},
    onNavigateToDetail: (Entry) -> Unit = {},
    onFeedClick: (String) -> Unit = {}
) {
    val config by AppConfigManager.config.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Page?>(null) }
    var pageResponse by remember { mutableStateOf<PageResponseObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var internalRetryTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(url, refreshTrigger, internalRetryTrigger) {
        if (url.isBlank()) {
            isLoading = false
            page = null
            pageResponse = null
            error = null
            onLoadingChanged(false)
            onPageLoaded(null)
            onResponseLoaded(null)
            return@LaunchedEffect
        }
        if (config.networkConfig.disabled) {
            isLoading = false
            page = null
            pageResponse = null
            error = "Network communication is disabled in settings."
            onLoadingChanged(false)
            onPageLoaded(null)
            onResponseLoaded(null)
            return@LaunchedEffect
        }

        isLoading = true
        error = null
        onLoadingChanged(true)
        try {
            val urlObj = Url(url)
            val resp = urlObj.getResponse()
            pageResponse = resp
            onResponseLoaded(resp)
            if (resp.text != null) {
                val parsedPage = urlObj.getPage()
                page = parsedPage
                onPageLoaded(parsedPage)
            } else {
                error = resp.error ?: "Failed to download content (${resp.statusCode})"
                page = null
                onPageLoaded(null)
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Failed to load page"
            page = null
            onPageLoaded(null)
        }
        isLoading = false
        onLoadingChanged(false)
    }

    Box(
        modifier = modifier
    ) {
        val currentPage = page
        val currentPageResponse = pageResponse
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

            url.isBlank() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Enter a URL above to check.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            error != null && currentPage == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (showResponseInfo && currentPageResponse != null) {
                        UrlResponseInfoPane(
                            pageResponse = currentPageResponse,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = "Failed to load page",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { internalRetryTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }

            currentPage == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (showResponseInfo && currentPageResponse != null) {
                        UrlResponseInfoPane(
                            pageResponse = currentPageResponse,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = "No content loaded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            currentPage is HtmlPage -> {
                HtmlPageDetails(
                    page = currentPage,
                    url = url,
                    showIcons = config.dbconfig.showIcons,
                    onFeedClick = onFeedClick,
                    onNavigateToDetail = onNavigateToDetail,
                    pageResponse = if (showResponseInfo) currentPageResponse else null
                )
            }

            else -> {
                DefaultPageDetails(
                    page = currentPage,
                    url = url,
                    showIcons = config.dbconfig.showIcons,
                    onFeedClick = onFeedClick,
                    onNavigateToDetail = onNavigateToDetail,
                    error = error,
                    pageResponse = if (showResponseInfo) currentPageResponse else null
                )
            }
        }
    }
}

/**
 * A [SuggestionChip] that displays a feed URL.
 * - Single tap triggers [onClick].
 * - Long press copies the URL to the clipboard and shows a toast.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedSuggestionChip(feedUrl: String, onClick: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    SuggestionChip(
        onClick = onClick,
        label = {
            Text(
                feedUrl,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(feedUrl))
                    Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                }
            )
    )
}

/**
 * Displays page metadata including title, publication date, description, and source link.
 */
@Composable
private fun PageMetadataSection(page: Page, url: String) {
    val uriHandler = LocalUriHandler.current

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
        ?.let { DateUtils.toIsoString(it) }
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
}

/**
 * Shared header section rendered inside both [DefaultPageDetails] and [RssPageDetails].
 *
 * Displays, in order:
 * 1. Optional HTTP response info.
 * 2. [extraTopContent] slot — inserted before metadata (e.g. hero image for HTML pages).
 * 3. Page metadata (title, date, description, source link).
 * 4. Feed chips with long-press copy support.
 *
 * @param page The page whose metadata is displayed.
 * @param url The source URL, used to filter out self-referential feeds.
 * @param onFeedClick Called when a feed chip is tapped.
 * @param pageResponse Optional HTTP response to display in [UrlResponseInfoPane].
 * @param extraTopContent Optional composable slot rendered between response info and metadata.
 */
@Composable
private fun PageDetailsHeader(
    page: Page,
    url: String,
    showIcons: Boolean,
    onFeedClick: (String) -> Unit,
    pageResponse: PageResponseObject?
) {
    if (pageResponse != null) {
        UrlResponseInfoPane(
            pageResponse = pageResponse,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Hero image — shown for any page type that provides a thumbnail
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

    PageMetadataSection(page = page, url = url)

    // Feed links advertised by the page itself, excluding the current URL
    val pageFeeds = page.getFeeds().filter { it.isNotBlank() && it != url }
    if (pageFeeds.isNotEmpty()) {
        Text(
            text = "Feeds",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        pageFeeds.forEach { feedUrl ->
            FeedSuggestionChip(
                feedUrl = feedUrl,
                onClick = { onFeedClick(feedUrl) }
            )
        }
    }
}

/**
 * Generic page details view backed by the [Page] interface.
 *
 * Uses a [LazyColumn] so that a potentially large entries list renders efficiently.
 * The shared header (response info, hero image, metadata, feeds) occupies the first
 * item. Entry cards follow. An [extraContent] slot is placed after the header and
 * before the entries, allowing subtype-specific content (e.g. a thumbnail gallery
 * for HTML pages).
 *
 * Entries are shown for any [Page] implementation that returns a non-empty list from
 * [Page.getEntries]. For [HtmlPage] this is always empty, so the section is skipped.
 *
 * @param page The page to display.
 * @param url The source URL.
 * @param showIcons Whether thumbnails should be displayed.
 * @param onFeedClick Called when a feed chip is tapped.
 * @param onNavigateToDetail Called when an entry card is tapped.
 * @param pageResponse Optional HTTP response info.
 * @param error Optional partial-load error message shown above the entry list.
 * @param extraContent Slot rendered between the header and the entries section.
 */
@Composable
private fun DefaultPageDetails(
    page: Page,
    url: String,
    showIcons: Boolean,
    onFeedClick: (String) -> Unit,
    onNavigateToDetail: (Entry) -> Unit,
    pageResponse: PageResponseObject? = null,
    error: String? = null,
    extraContent: @Composable () -> Unit = {}
) {
    val entries = page.getEntries()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Shared header: response info, hero image, metadata, feeds
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageDetailsHeader(
                    page = page,
                    url = url,
                    showIcons = showIcons,
                    onFeedClick = onFeedClick,
                    pageResponse = pageResponse
                )
                extraContent()
            }
        }

        // Entry count label (only when there are entries)
        if (entries.isNotEmpty()) {
            item {
                Text(
                    text = "Feed Entries (${entries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Partial-load error banner
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

        // Entry cards — skipped automatically when getEntries() returns empty
        items(entries) { entry ->
            FeedEntryCard(
                entry = entry,
                showIcons = showIcons,
                onClick = { onNavigateToDetail(entry) }
            )
        }
    }
}

/**
 * Details view for HTML web pages.
 *
 * Extends [DefaultPageDetails] with a horizontal thumbnail gallery (additional images
 * beyond the hero) rendered after the shared header.
 */
@Composable
private fun HtmlPageDetails(
    page: HtmlPage,
    url: String,
    showIcons: Boolean,
    onFeedClick: (String) -> Unit,
    onNavigateToDetail: (Entry) -> Unit,
    pageResponse: PageResponseObject? = null
) {
    val thumbnails = page.getThumbnails()
    DefaultPageDetails(
        page = page,
        url = url,
        showIcons = showIcons,
        onFeedClick = onFeedClick,
        onNavigateToDetail = onNavigateToDetail,
        pageResponse = pageResponse,
        extraContent = {
            // Thumbnail gallery (images beyond the first, which is already the hero)
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
                            modifier = Modifier.size(120.dp, 80.dp),
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
    )
}


/**
 * Card representing a single RSS feed item.
 */
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
