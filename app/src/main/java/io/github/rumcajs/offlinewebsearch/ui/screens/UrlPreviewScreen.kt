package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.ui.components.UrlPreviewPane
import io.github.rumcajs.offlinewebsearch.webtoolkit.HtmlPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.Page
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage

/**
 * Screen for previewing web page or RSS feed data of a given URL.
 *
 * Renders title, publication date, description, thumbnails (for HTML pages),
 * and feed items (for RSS feeds).
 *
 * @param url The URL to inspect and preview.
 * @param onBack Callback invoked when navigating back.
 * @param onNavigateToDetail Callback invoked when selecting a feed entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlPreviewScreen(
    url: String,
    onBack: () -> Unit,
    onNavigateToDetail: (Entry) -> Unit = {}
) {
    val config by AppConfigManager.config.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Page?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val titleText = when {
        page is HtmlPage -> "Web Page"
        page is RssPage -> "Feed Data"
        else -> "Preview"
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
                        enabled = !isLoading && url.isNotBlank() && !config.networkConfig.disabled
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        UrlPreviewPane(
            url = url,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            refreshTrigger = refreshTrigger,
            onLoadingChanged = { isLoading = it },
            onPageLoaded = { page = it },
            onNavigateToDetail = onNavigateToDetail
        )
    }
}
