package io.github.rumcajs.offlinewebsearch.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalData
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalDataRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.ui.components.PropertiesPane
import io.github.rumcajs.offlinewebsearch.ui.components.PropertyItem
import io.github.rumcajs.offlinewebsearch.ui.components.PropertyType
import kotlinx.coroutines.launch

/**
 * Screen displaying details of a single Source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceScreen(
    source: Source,
    onNavigateToEdit: (() -> Unit)? = null,
    onDelete: ((deleteEntries: Boolean) -> Unit)? = null,
    onBrowseEntries: ((Source) -> Unit)? = null,
    onRefreshSuccess: (() -> Unit)? = null,
    onSourceUpdated: ((Source) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    var currentSource by remember(source) { mutableStateOf(source) }
    var operationalData by remember { mutableStateOf<SourceOperationalData?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteEntriesWithSource by remember { mutableStateOf(false) }
    var showAgeDialog by remember { mutableStateOf(false) }
    var ageInput by remember(currentSource.age) { mutableStateOf((currentSource.age ?: 0).toString()) }
    var isSavingAge by remember { mutableStateOf(false) }
    var showAutoTagDialog by remember { mutableStateOf(false) }
    var autoTagInput by remember(currentSource.auto_tag) { mutableStateOf(currentSource.auto_tag) }
    var isSavingAutoTag by remember { mutableStateOf(false) }

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
            onDismissRequest = {
                showDeleteDialog = false
                deleteEntriesWithSource = false
            },
            title = { Text("Delete Source") },
            text = {
                Column {
                    Text("Are you sure you want to delete source '${source.title.ifBlank { "Untitled" }}'?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteEntriesWithSource = !deleteEntriesWithSource }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteEntriesWithSource,
                            onCheckedChange = { deleteEntriesWithSource = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Also delete all entries from this source",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shouldDeleteEntries = deleteEntriesWithSource
                        showDeleteDialog = false
                        deleteEntriesWithSource = false
                        onDelete(shouldDeleteEntries)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleteEntriesWithSource = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAgeDialog && currentSource.id != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingAge) {
                    showAgeDialog = false
                }
            },
            title = { Text("Default Entry Age") },
            text = {
                Column {
                    Text(
                        text = "Enter the age that will be applied to entries fetched from this source (default is 0):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                ageInput = input
                            }
                        },
                        label = { Text("Default Entry Age") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedAge = ageInput.toIntOrNull() ?: 0
                        val finalAge = if (parsedAge >= 0) parsedAge else 0
                        val sourceId = currentSource.id
                        val dbState = activeDbState
                        if (sourceId != null && dbState != null) {
                            scope.launch {
                                isSavingAge = true
                                val (success, err) = SourceRepository.updateSourceAge(
                                    context = context,
                                    activeDatabaseState = dbState,
                                    id = sourceId,
                                    age = finalAge
                                )
                                isSavingAge = false
                                if (success) {
                                    val updated = currentSource.copy(age = finalAge)
                                    currentSource = updated
                                    onSourceUpdated?.invoke(updated)
                                    showAgeDialog = false
                                    Toast.makeText(context, "Default entry age updated", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, err ?: "Failed to update default entry age", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isSavingAge
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAgeDialog = false },
                    enabled = !isSavingAge
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    fun getTagInput(tag: String): String {
        return tag.trim().lowercase()
    }

    if (showAutoTagDialog && currentSource.id != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingAutoTag) {
                    showAutoTagDialog = false
                }
            },
            title = { Text("Define Auto Tag") },
            text = {
                Column {
                    Text(
                        text = "Enter tags separated by comma (e.g. news, tech, android):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoTagInput,
                        onValueChange = { autoTagInput = it },
                        label = { Text("Auto Tag") },
                        placeholder = { Text("news, tech, android") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sourceId = currentSource.id
                        val dbState = activeDbState
                        if (sourceId != null && dbState != null) {
                            scope.launch {
                                isSavingAutoTag = true
                                val (success, err) = SourceRepository.updateSourceAutoTag(
                                    context = context,
                                    activeDatabaseState = dbState,
                                    id = sourceId,
                                    autoTag = getTagInput(autoTagInput)
                                )
                                isSavingAutoTag = false
                                if (success) {
                                    val updated = currentSource.copy(auto_tag = getTagInput(autoTagInput))
                                    currentSource = updated
                                    onSourceUpdated?.invoke(updated)
                                    showAutoTagDialog = false
                                    Toast.makeText(context, "Auto tags updated", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, err ?: "Failed to update auto tags", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isSavingAutoTag
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAutoTagDialog = false },
                    enabled = !isSavingAutoTag
                ) {
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

            // Source properties displayed as a unified properties list
            PropertiesPane(
                properties = buildList {
                    if (currentSource.url.isNotBlank()) {
                        add(
                            PropertyItem(
                                label = "URL",
                                value = currentSource.url,
                                type = PropertyType.LINK,
                                toastMessage = "Source URL copied to clipboard"
                            )
                        )
                    }
                    add(PropertyItem(label = "ID", value = currentSource.id?.toString() ?: "N/A"))
                    add(PropertyItem(label = "Status", value = if (currentSource.enabled) "Enabled" else "Disabled"))
                    add(PropertyItem(label = "Type", value = currentSource.source_type?.takeIf { it.isNotBlank() } ?: SourceRepository.SOURCE_TYPE_RSS))
                    add(PropertyItem(label = "Default Entry Age", value = (currentSource.age ?: 0).toString()))
                    add(PropertyItem(label = "Favicon", value = currentSource.favicon.takeIf { it.isNotBlank() } ?: "", type= PropertyType.LINK))
                    add(PropertyItem(label = "Last Fetched", value = operationalData?.date_fetched ?: "Never"))
                    add(PropertyItem(label = "Import Duration", value = operationalData?.import_seconds?.let { "${it}s" } ?: "N/A"))
                    add(PropertyItem(label = "Number of Entries", value = operationalData?.number_of_entries?.toString() ?: "N/A"))
                    add(PropertyItem(label = "Consecutive Errors", value = operationalData?.consecutive_errors?.toString() ?: "0"))
                    add(PropertyItem(label = "Page Hash", value = operationalData?.page_hash?.joinToString("") { "%02x".format(it) }?.takeIf { it.isNotBlank() } ?: "N/A"))
                    add(PropertyItem(label = "Body Hash", value = operationalData?.body_hash?.joinToString("") { "%02x".format(it) }?.takeIf { it.isNotBlank() } ?: "N/A"))
                }
            )

            if (isEditable && currentSource.id != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        ageInput = (currentSource.age ?: 0).toString()
                        showAgeDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Define Default Entry Age")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        autoTagInput = currentSource.auto_tag
                        showAutoTagDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Define Auto Tag")
                }
            }

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
