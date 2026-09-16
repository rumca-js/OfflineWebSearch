package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.ui.components.UrlInputPane
import io.github.rumcajs.offlinewebsearch.ui.components.UrlPreviewPane
import io.github.rumcajs.offlinewebsearch.webtoolkit.HandlerBuilder
import io.github.rumcajs.offlinewebsearch.webtoolkit.HtmlPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.Page
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation

/**
 * Resolves [HandlerBuilder] suggestions for [url] as a flat list of URLs.
 *
 * Combines feed URLs and the channel URL (when non-blank) into a single deduplicated list.
 * Returns an empty list when no handler matches or when the handler provides no links.
 */
private fun resolveHandlerSuggestions(url: String): List<String> {
    if (url.isBlank()) return emptyList()
    val handler = HandlerBuilder(url).build() ?: return emptyList()
    return (handler.getFeeds() + listOf(handler.getChannel()))
        .filter { it.isNotBlank() }
        .distinct()
}

/**
 * Screen for checking links with an interactive URL input bar and URL argument cleaner.
 *
 * Allows users to type or paste any URL to inspect its status, metadata,
 * HTML page preview, or RSS feed entries.
 *
 * When a URL is loaded and a known handler matches it (YouTube, GitHub, Reddit, Odysee),
 * suggested feed / channel URLs are displayed as clickable chips so the user can
 * quickly navigate to related links.
 *
 * @param url The initial URL to inspect (defaults to empty string).
 * @param onBack Callback invoked when navigating back.
 * @param onNavigateToDetail Callback invoked when selecting a feed entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlLinkCheckerScreen(
    url: String = "",
    onBack: () -> Unit,
    onNavigateToDetail: (Entry) -> Unit = {}
) {
    val config by AppConfigManager.config.collectAsState()
    var inputUrlText by remember(url) { mutableStateOf(url) }
    var activeUrl by remember(url) { mutableStateOf(UrlLocation.normalizeUrl(url)) }
    var isLoading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Page?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Derive handler suggestions whenever the active URL changes.
    val suggestions: List<String> = remember(activeUrl) { resolveHandlerSuggestions(activeUrl) }

    val titleText = when {
        page is HtmlPage -> "Web Page"
        page is RssPage -> "Feed Data"
        activeUrl.isNotBlank() -> "Link Checker"
        else -> "Link Checker"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshTrigger++ },
                        enabled = !isLoading && activeUrl.isNotBlank() && !config.networkConfig.disabled
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // URL Input bar & Controls
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                UrlInputPane(
                    url = inputUrlText,
                    onUrlChange = { inputUrlText = it },
                    placeholder = "https://example.com",
                    enabled = !isLoading,
                    showClearArgsButton = true,
                    onClearArgs = {
                        val cleaned = UrlLocation.clearUrlArgs(inputUrlText)
                        inputUrlText = cleaned
                        val normalized = UrlLocation.normalizeUrl(cleaned)
                        if (normalized.isNotBlank()) {
                            activeUrl = normalized
                            refreshTrigger++
                        }
                    },
                    onNormalizeSelected = { normalized ->
                        inputUrlText = normalized
                        if (normalized.isNotBlank()) {
                            activeUrl = normalized
                            refreshTrigger++
                        }
                    },
                    additionalSuggestions = suggestions,
                    onSuggestionClick = { inputUrlText = it },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputUrlText.isNotBlank()) {
                                IconButton(onClick = { inputUrlText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(
                                onClick = {
                                    val normalized = UrlLocation.normalizeUrl(inputUrlText)
                                    if (normalized.isNotBlank()) {
                                        activeUrl = normalized
                                        refreshTrigger++
                                    }
                                },
                                enabled = inputUrlText.isNotBlank() && !isLoading
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Check")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val normalized = UrlLocation.normalizeUrl(inputUrlText)
                            if (normalized.isNotBlank()) {
                                activeUrl = normalized
                                refreshTrigger++
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Embedded preview pane
            UrlPreviewPane(
                url = activeUrl,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                refreshTrigger = refreshTrigger,
                showResponseInfo = true,
                onLoadingChanged = { isLoading = it },
                onPageLoaded = { page = it },
                onNavigateToDetail = onNavigateToDetail,
                onFeedClick = { feedUrl -> inputUrlText = feedUrl }
            )
        }
    }
}

/**
 * Composable alias for [UrlLinkCheckerScreen].
 */
@Composable
fun UrlLinkChecker(
    initialUrl: String = "",
    onBack: () -> Unit,
    onNavigateToDetail: (Entry) -> Unit = {}
) {
    UrlLinkCheckerScreen(
        url = initialUrl,
        onBack = onBack,
        onNavigateToDetail = onNavigateToDetail
    )
}
