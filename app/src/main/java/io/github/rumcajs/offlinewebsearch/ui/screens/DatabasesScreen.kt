package io.github.rumcajs.offlinewebsearch.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.ui.components.DatabaseDetailDialog
import io.github.rumcajs.offlinewebsearch.ui.components.ReadOnlyBadge
import io.github.rumcajs.offlinewebsearch.ui.components.StatusBadge
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabasesScreen(
    onNavigateToDatabaseDetail: (String?, DatabaseState) -> Unit = { _, _ -> }
) {
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    fun handleSaveDatabase(url: String, editUrl: String?, fileUri: Uri?) {
        val state = DatabaseState.fromUrl(url)
        if (state.isLocal && fileUri != null) {
            isVerifying = true
            verificationError = null
            scope.launch {
                try {
                    AppConfigManager.saveDatabaseLocal(context, url, fileUri, editUrl)
                    showAddDialog = false
                } catch (e: Exception) {
                    verificationError = e.message ?: "Failed to save database"
                } finally {
                    isVerifying = false
                }
            }
        } else if (!state.isLocal) {
            val isZip = url.endsWith(".db.zip", ignoreCase = true) || url.endsWith(".zip", ignoreCase = true)
            if (state.extension != ".json" && state.extension != ".db" && !isZip) {
                verificationError = "URL must end with .json, .db, .zip, or .db.zip"
                return
            }
            showAddDialog = false
            AppConfigManager.refreshDatabaseInBackground(context, url)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context, uri) ?: "local_db.json"
            if (config.isSupportedFileName(fileName)) {
                val url = DatabaseState.toLocalUrl(fileName)
                handleSaveDatabase(url, null, uri)
            } else {
                Toast.makeText(context, "Unsupported file extension. Supported: ${config.supportedDatabasesExtensions.joinToString(", ")}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var showAddDialogMode by remember { mutableStateOf<String?>(null) } // "local", "url", "preset"
    var presetUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingPresets by remember { mutableStateOf(false) }
    var selectedPresetUrl by remember { mutableStateOf("") }

    LaunchedEffect(showAddDialogMode) {
        if (showAddDialogMode == "preset" && presetUrls.isEmpty()) {
            isLoadingPresets = true
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = NetworkUtils.executeRequest("https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases.txt")
                    val text = if (response.isValid) response.text else null
                    if (!text.isNullOrBlank()) {
                        val lines = text.lines()
                            .map { it.trim() }
                            .filter { it.startsWith("http://") || it.startsWith("https://") }
                        presetUrls = lines
                    }
                }
            } catch (_: Exception) { }
            isLoadingPresets = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Databases", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Active: ${config.activeDatabaseDisplayName}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3 Buttons for Adding Databases
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch("*/*")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Add local file", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    urlInput = ""
                    editingUrl = null
                    verificationError = null
                    selectedFileUri = null
                    showAddDialogMode = "url"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Add by URL", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    urlInput = ""
                    editingUrl = null
                    verificationError = null
                    selectedFileUri = null
                    selectedPresetUrl = ""
                    showAddDialogMode = "preset"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Preselected list", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Default Assets Database Item
        val defaultState = DatabaseState(
            url = "",
            localFileName = "places_0.json",
            status = io.github.rumcajs.offlinewebsearch.data.DatabaseStatus.READY,
            isReadOnly = true
        )
        val defaultIsActive = config.activeDatabase == null

        DatabaseCardItem(
            name = "Default (Assets)",
            state = defaultState,
            isActive = defaultIsActive,
            onCardClick = {
                onNavigateToDatabaseDetail(null, defaultState)
            },
            onSetActive = {
                AppConfigManager.setActiveDatabase(null)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Configured Databases
        config.databases.forEach { (url, state) ->
            val isActive = config.activeDatabase == url

            DatabaseCardItem(
                name = state.displayName,
                state = state,
                isActive = isActive,
                onCardClick = {
                    onNavigateToDatabaseDetail(url, state)
                },
                onSetActive = {
                    AppConfigManager.setActiveDatabase(url)
                },
                onEdit = {
                    urlInput = url
                    editingUrl = url
                    verificationError = null
                    selectedFileUri = null
                    showAddDialogMode = "url"
                },
                onDelete = {
                    AppConfigManager.removeDatabaseAndFiles(context, url, state.localFileName)
                },
                onUpdate = if (!state.isLocal) {
                    {
                        scope.launch {
                            try {
                                AppConfigManager.saveDatabaseFromInternet(context, url, oldUrl = url)
                                Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to update database", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else null
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Add by URL Dialog
    if (showAddDialogMode == "url") {
        AlertDialog(
            onDismissRequest = { if (!isVerifying) showAddDialogMode = null },
            title = { Text(if (editingUrl == null) "Add Database by URL" else "Edit Database URL") },
            text = {
                Column {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            verificationError = null
                        },
                        label = { Text("Web URL") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = verificationError != null,
                        supportingText = verificationError?.let { { Text(it) } }
                    )
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            handleSaveDatabase(urlInput, editingUrl, null)
                            if (verificationError == null) showAddDialogMode = null
                        }
                    },
                    enabled = urlInput.isNotBlank() && !isVerifying
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialogMode = null }, enabled = !isVerifying) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add from Preselected List Dialog
    if (showAddDialogMode == "preset") {
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isVerifying) showAddDialogMode = null },
            title = { Text("Preselected Databases") },
            text = {
                Column {
                    Text("Select a database from rumca-js repository list:")
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoadingPresets) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (presetUrls.isEmpty()) {
                        Text("Failed to load preselected list. Please check network connection.")
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedPresetUrl.ifEmpty { "Select a database..." },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                presetUrls.forEach { pUrl ->
                                    DropdownMenuItem(
                                        text = { Text(pUrl) },
                                        onClick = {
                                            selectedPresetUrl = pUrl
                                            urlInput = pUrl
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (verificationError != null) {
                        Text(
                            text = verificationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            handleSaveDatabase(urlInput, null, null)
                            if (verificationError == null) showAddDialogMode = null
                        }
                    },
                    enabled = urlInput.isNotBlank() && !isVerifying
                ) {
                    Text("Add Database")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialogMode = null }, enabled = !isVerifying) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DatabaseCardItem(
    name: String,
    state: DatabaseState,
    isActive: Boolean,
    onCardClick: () -> Unit,
    onSetActive: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onUpdate: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadge(status = state.status)
                ReadOnlyBadge(isReadOnly = state.isReadOnly)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tap to view state & configuration details",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCardClick) {
                    Icon(Icons.Default.Info, contentDescription = "Properties")
                }
                if (!isActive) {
                    TextButton(onClick = onSetActive) {
                        Text("Use")
                    }
                }
                if (onUpdate != null) {
                    IconButton(onClick = onUpdate) {
                        Icon(Icons.Default.Refresh, contentDescription = "Update")
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
