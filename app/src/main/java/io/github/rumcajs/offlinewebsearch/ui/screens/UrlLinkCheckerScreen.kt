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
import io.github.rumcajs.offlinewebsearch.ui.components.UrlPreviewPane
import io.github.rumcajs.offlinewebsearch.webtoolkit.HtmlPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.Page
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage

/**
 * Normalizes a user-entered URL string by adding a scheme prefix if missing.
 *
 * @param raw The raw input string.
 * @return Normalized URL string with protocol.
 */
private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return if (!trimmed.contains("://") && !trimmed.startsWith("//")) {
        "https://$trimmed"
    } else if (trimmed.startsWith("//")) {
        "https:$trimmed"
    } else {
        trimmed
    }
}

/**
 * Screen for checking links with an interactive URL input bar.
 *
 * Allows users to type or paste any URL to inspect its status, metadata,
 * HTML page preview, or RSS feed entries.
 *
 * @param initialUrl The initial URL to inspect (defaults to empty string).
 * @param onBack Callback invoked when navigating back.
 * @param onNavigateToDetail Callback invoked when selecting a feed entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlLinkCheckerScreen(
    initialUrl: String = "",
    onBack: () -> Unit,
    onNavigateToDetail: (Entry) -> Unit = {}
) {
    val config by AppConfigManager.config.collectAsState()
    var inputUrlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var activeUrl by remember(initialUrl) { mutableStateOf(normalizeUrl(initialUrl)) }
    var isLoading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Page?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

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
            // URL Input bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputUrlText,
                        onValueChange = { inputUrlText = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inputUrlText.isNotBlank()) {
                                    IconButton(onClick = { inputUrlText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val normalized = normalizeUrl(inputUrlText)
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
                                val normalized = normalizeUrl(inputUrlText)
                                if (normalized.isNotBlank()) {
                                    activeUrl = normalized
                                    refreshTrigger++
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                onNavigateToDetail = onNavigateToDetail
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
        initialUrl = initialUrl,
        onBack = onBack,
        onNavigateToDetail = onNavigateToDetail
    )
}
