package io.github.rumcajs.offlinewebsearch.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Source
import io.github.rumcajs.offlinewebsearch.data.SourceOperationalData
import io.github.rumcajs.offlinewebsearch.data.SourceOperationalDataRepository
import io.github.rumcajs.offlinewebsearch.data.SourceRepository
import kotlinx.coroutines.launch

/**
 * Screen displaying details of a single Source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceScreen(
    source: Source,
    onNavigateToEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onBrowseEntries: ((Source) -> Unit)? = null,
    onRefreshSuccess: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    var currentSource by remember(source) { mutableStateOf(source) }
    var operationalData by remember { mutableStateOf<SourceOperationalData?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentSource.id, activeDbState) {
        val sourceId = currentSource.id
        if (sourceId != null) {
            operationalData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, activeDbState, sourceId)
        }
    }

    val performRefresh: () -> Unit = {
        if (currentSource.url.isBlank()) {
            Toast.makeText(context, "Source URL is empty", Toast.LENGTH_SHORT).show()
        } else if (config.networkConfig.disabled) {
            Toast.makeText(context, "Network operations are disabled", Toast.LENGTH_SHORT).show()
        } else if (activeDbState == null || activeDbState.isReadOnly || activeDbState.extension != ".db") {
            Toast.makeText(context, "Active database is read-only or not writable", Toast.LENGTH_SHORT).show()
        } else {
            isRefreshing = true
            SourceRefreshWorker.enqueueSource(
                context = context,
                dbState = activeDbState,
                source = currentSource,
                onFinished = { success, msg ->
                    scope.launch {
                        if (success) {
                            val updatedSources = SourceRepository.getSourcesByFetchTime(context, activeDbState)
                            val updated = updatedSources.firstOrNull { it.id == currentSource.id || it.url == currentSource.url }
                            if (updated != null) {
                                currentSource = updated
                            }
                            val sourceId = currentSource.id
                            if (sourceId != null) {
                                operationalData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, activeDbState, sourceId)
                            }
                            onRefreshSuccess?.invoke()
                        }
                        isRefreshing = false
                        Toast.makeText(context, msg ?: if (success) "Source refreshed" else "Failed to refresh source", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Source") },
            text = { Text("Are you sure you want to delete source '${source.title.ifBlank { "Untitled" }}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentSource.url.isNotBlank() && !config.networkConfig.disabled) {
                        IconButton(
                            onClick = performRefresh,
                            enabled = !isRefreshing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Source")
                        }
                    }
                    if (currentSource.url.isNotBlank()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, currentSource.url)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share link"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    if (onNavigateToEdit != null) {
                        IconButton(onClick = onNavigateToEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                    if (isEditable && onDelete != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
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
            // Thumbnail
            if (currentSource.favicon.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentSource.favicon)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Thumbnail for ${currentSource.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = currentSource.title.ifBlank { "Untitled Source" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (currentSource.url.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .pointerInput(currentSource.url) {
                            detectTapGestures(
                                onTap = { uriHandler.openUri(currentSource.url) },
                                onLongPress = {
                                    clipboardManager.setText(AnnotatedString(currentSource.url))
                                    Toast.makeText(context, "Source URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "URL",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = currentSource.url,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            DetailRow(
                label = "ID",
                value = currentSource.id?.toString() ?: "N/A"
            )

            DetailRow(
                label = "Status",
                value = if (currentSource.enabled) "Enabled" else "Disabled"
            )

            DetailRow(
                label = "Type",
                value = currentSource.source_type?.takeIf { it.isNotBlank() } ?: SourceRepository.SOURCE_TYPE_RSS
            )

            DetailRow(
                label = "Last Fetched",
                value = operationalData?.date_fetched ?: "Never"
            )

            DetailRow(
                label = "Import Duration",
                value = operationalData?.import_seconds?.let { "${it}s" } ?: "N/A"
            )

            DetailRow(
                label = "Number of Entries",
                value = operationalData?.number_of_entries?.toString() ?: "N/A"
            )

            DetailRow(
                label = "Consecutive Errors",
                value = operationalData?.consecutive_errors?.toString() ?: "0"
            )

            DetailRow(
                label = "Page Hash",
                value = operationalData?.page_hash?.joinToString("") { "%02x".format(it) }?.takeIf { it.isNotBlank() } ?: "N/A"
            )

            DetailRow(
                label = "Body Hash",
                value = operationalData?.body_hash?.joinToString("") { "%02x".format(it) }?.takeIf { it.isNotBlank() } ?: "N/A"
            )

            if (onBrowseEntries != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { onBrowseEntries(currentSource) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse entries")
                }
            }
        }
    }
}
