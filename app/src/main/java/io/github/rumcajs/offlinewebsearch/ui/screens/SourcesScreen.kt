package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Source
import io.github.rumcajs.offlinewebsearch.data.SourceRepository
import kotlinx.coroutines.launch

/**
 * Screen displaying the list of sources from `sourcedatamodel` table.
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
    val isEditable = activeDbState != null && !activeDbState.isReadOnly && activeDbState.extension == ".db"

    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sourceToDelete by remember { mutableStateOf<Source?>(null) }

    LaunchedEffect(config.activeDatabase) {
        isLoading = true
        sources = SourceRepository.loadSources(context, activeDbState)
        isLoading = false
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
                                        sources = SourceRepository.loadSources(context, activeDbState)
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
                }
            )
        },
        floatingActionButton = {
            if (isEditable && onNavigateToAddSource != null) {
                FloatingActionButton(onClick = onNavigateToAddSource) {
                    Icon(Icons.Default.Add, contentDescription = "Add Source")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (sources.isEmpty()) {
                Text(
                    text = "No sources available in current database.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sources) { source ->
                        SourceItemRow(
                            source = source,
                            isEditable = isEditable,
                            onClick = { onNavigateToSource(source) },
                            onEditClick = { onNavigateToEditSource(source) },
                            onDeleteClick = { sourceToDelete = source }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItemRow(
    source: Source,
    isEditable: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            // Thumbnail
            if (source.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(source.thumbnail)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Thumbnail for ${source.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp)
                )
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
                    Text(
                        text = source.url,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons & Status Chip
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
                                tint = if (isEditable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    }
}
