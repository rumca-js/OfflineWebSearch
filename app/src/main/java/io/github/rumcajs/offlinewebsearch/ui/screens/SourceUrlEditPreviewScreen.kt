package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Source
import io.github.rumcajs.offlinewebsearch.data.SourceRepository
import io.github.rumcajs.offlinewebsearch.ui.components.SourceFormPane
import io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url
import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation
import kotlinx.coroutines.launch

/**
 * Screen displayed when adding a source.
 * Fetches RSS/feed page data using [Url] from the given URL and automatically sets fields
 * (like Title and Favicon) once ready.
 * Shares the same [SourceFormPane] with [SourceEditScreen], while displaying additional HTTP metadata
 * such as status code, content-type, content length, and entry count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceUrlEditPreviewScreen(
    initialUrl: String = "",
    onSourceAdded: (Source) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState

    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl) }
    var enabled by remember { mutableStateOf(true) }
    var favicon by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var previewResponseObject by remember { mutableStateOf<PageResponseObject?>(null) }
    var rssEntryCount by remember { mutableStateOf<Int?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val canSave = url.isNotBlank()

    // Fetch RSS preview data using Url whenever url changes or refresh is tapped
    LaunchedEffect(url, refreshTrigger) {
        if (url.isBlank() || config.networkConfig.disabled) {
            previewResponseObject = null
            rssEntryCount = null
            return@LaunchedEffect
        }
        if (!UrlLocation(url).isWebLink()) {
            return@LaunchedEffect
        }

        isLoadingPreview = true
        try {
            val urlObj = Url(url)
            val resp = urlObj.getResponse()
            previewResponseObject = resp
            if (resp.isValid) {
                val page = urlObj.getPage()
                val extractedTitle = page.getTitle()
                if (title.isBlank() && !extractedTitle.isNullOrBlank()) {
                    title = extractedTitle
                }
                val thumbnails = page.getThumbnails()
                val extractedFavicon = thumbnails.firstOrNull { it.isNotBlank() }
                if (extractedFavicon != null) {
                    favicon = extractedFavicon
                }
                if (page is RssPage) {
                    rssEntryCount = page.getEntries().size
                } else {
                    rssEntryCount = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingPreview = false
        }
    }

    suspend fun saveSource(): Boolean {
        if (!UrlLocation(url).isWebLink()) {
            urlError = "URL must start with http://, https://, smb://, or ftp:// and have a valid domain"
            return false
        }
        urlError = null

        val (success, err) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = activeDbState,
            title = title,
            url = url,
            enabled = enabled
        )
        errorMessage = if (!success) err else null
        return success
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Source") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!config.networkConfig.disabled && url.isNotBlank()) {
                        IconButton(
                            onClick = { refreshTrigger++ },
                            enabled = !isLoadingPreview
                        ) {
                            if (isLoadingPreview) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Preview")
                            }
                        }
                    }
                    if (isEditable) {
                        IconButton(
                            onClick = {
                                if (isSaving) return@IconButton
                                isSaving = true
                                coroutineScope.launch {
                                    val success = saveSource()
                                    isSaving = false
                                    if (success) {
                                        Toast.makeText(context, "Source added", Toast.LENGTH_SHORT).show()
                                        val createdSource = SourceRepository.getSourceByUrl(context, activeDbState, url)
                                            ?: Source(title = title, url = url, enabled = enabled, favicon = favicon)
                                        onSourceAdded(createdSource)
                                    } else {
                                        val msg = errorMessage ?: "Failed to save source"
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = canSave && !isSaving
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SourceFormPane(
                title = title,
                onTitleChange = { title = it },
                url = url,
                onUrlChange = {
                    url = it
                    urlError = null
                },
                enabled = enabled,
                onEnabledChange = { enabled = it },
                isEditable = isEditable,
                urlError = urlError
            )

            // Preview Info Card displaying status code, content-type, etc.
            if (previewResponseObject != null || isLoadingPreview) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Feed Info",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (isLoadingPreview) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        previewResponseObject?.let { resp ->
                            Spacer(modifier = Modifier.height(8.dp))

                            val (statusColor, statusText) = when {
                                resp.isValid -> {
                                    androidx.compose.ui.graphics.Color(0xFF2E7D32) to "Success (${resp.statusCode})"
                                }
                                resp.isInvalid -> {
                                    MaterialTheme.colorScheme.error to "Error (${resp.statusCode})"
                                }
                                else -> {
                                    MaterialTheme.colorScheme.error to "Unknown (${resp.statusCode})"
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status Code",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(statusText) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        labelColor = statusColor
                                    )
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Content-Type",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = resp.contentType ?: "N/A",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            resp.length?.let { len ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Content Length",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "$len bytes",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            rssEntryCount?.let { count ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Feed Entries",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "$count entries found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (resp.error != null) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "Error: ${resp.error}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isEditable) {
                errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        coroutineScope.launch {
                            val success = saveSource()
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "Source added", Toast.LENGTH_SHORT).show()
                                val createdSource = SourceRepository.getSourceByUrl(context, activeDbState, url)
                                    ?: Source(title = title, url = url, enabled = enabled, favicon = favicon)
                                onSourceAdded(createdSource)
                            } else {
                                val msg2 = errorMessage ?: "Failed to save source"
                                Toast.makeText(context, msg2, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = canSave && !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Add Source")
                    }
                }
            }
        }
    }
}
