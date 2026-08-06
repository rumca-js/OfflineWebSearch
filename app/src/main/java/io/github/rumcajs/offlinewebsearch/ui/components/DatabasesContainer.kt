package io.github.rumcajs.offlinewebsearch.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabasesContainer(
    onNavigateToDatabaseDetail: (String?, DatabaseState) -> Unit = { _, _ -> }
) {
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddDialogMode by remember { mutableStateOf<String?>(null) } // "url", "preset"
    var editingUrl by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    var presetUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingPresets by remember { mutableStateOf(false) }
    var selectedPresetUrl by remember { mutableStateOf("") }

    suspend fun handleSaveDatabaseLocal(
        urlInput: String,
        editingUrl: String?,
        selectedFileUri: Uri?
    ) {
        if (selectedFileUri != null) {
            try {
                AppConfigManager.saveDatabaseLocal(context, urlInput, selectedFileUri, editingUrl)
            } catch (e: Exception) {
                verificationError = e.message ?: "Failed to read file"
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context, uri) ?: "local_db.json"
            if (config.isSupportedFileName(fileName)) {
                val url = DatabaseState.toLocalUrl(fileName)
                scope.launch {
                    handleSaveDatabaseLocal(url, null, uri)
                }
            } else {
                Toast.makeText(
                    context,
                    "Unsupported file extension. Supported: ${config.supportedDatabasesExtensions.joinToString(", ")}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    suspend fun handleSaveDatabaseFromInternet(
        urlInput: String,
        editingUrl: String?
    ) {
        try {
            AppConfigManager.saveDatabaseFromInternet(context, urlInput, editingUrl)
        } catch (e: Exception) {
            verificationError = e.message ?: "Failed to download database files"
        }
    }

    suspend fun handleSaveDatabase(
        urlInput: String,
        editingUrl: String?,
        selectedFileUri: Uri?
    ) {
        isVerifying = true
        verificationError = null

        val state = DatabaseState.fromUrl(urlInput)

        if (state.isLocal) {
            handleSaveDatabaseLocal(urlInput, editingUrl, selectedFileUri)
        } else {
            handleSaveDatabaseFromInternet(urlInput, editingUrl)
        }
        isVerifying = false
    }

    LaunchedEffect(showAddDialogMode) {
        if (showAddDialogMode == "preset" && presetUrls.isEmpty()) {
            isLoadingPresets = true
            try {
                withContext(Dispatchers.IO) {
                    val response = NetworkUtils.executeRequest(config.presetDatabasesUrl)
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Databases", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DatabaseActionButton(
                text = "Preselected list",
                onClick = {
                    urlInput = ""
                    editingUrl = null
                    verificationError = null
                    selectedFileUri = null
                    selectedPresetUrl = ""
                    showAddDialogMode = "preset"
                }
            )

            DatabaseActionButton(
                text = "Add local file",
                onClick = {
                    filePickerLauncher.launch("*/*")
                }
            )

            DatabaseActionButton(
                text = "Add by URL",
                onClick = {
                    urlInput = ""
                    editingUrl = null
                    verificationError = null
                    selectedFileUri = null
                    showAddDialogMode = "url"
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        DatabaseList(
            databases = config.databases,
            onItemClick = { url, state ->
                onNavigateToDatabaseDetail(url, state)
            },
            onEdit = { url ->
                urlInput = url
                editingUrl = url
                verificationError = null
                selectedFileUri = null
                showAddDialogMode = "url"
            },
            onDelete = { url, state ->
                AppConfigManager.removeDatabase(url)
                val fileName = state.localFileName
                File(context.filesDir, fileName).delete()
            },
            onUpdate = { url, state ->
                if (!state.isLocal) {
                    scope.launch {
                        try {
                            AppConfigManager.saveDatabaseFromInternet(context, url, oldUrl = url)
                            Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Failed to update database",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )

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

@Composable
fun DatabaseList(
    databases: Map<String, DatabaseState>,
    onItemClick: ((String, DatabaseState) -> Unit)? = null,
    onEdit: (String) -> Unit,
    onDelete: (String, DatabaseState) -> Unit,
    onUpdate: (String, DatabaseState) -> Unit
) {
    databases.forEach { (url, state) ->
        DatabaseItem(
            state = state,
            onItemClick = { onItemClick?.invoke(url, state) },
            onEdit = { onEdit(url) },
            onDelete = { onDelete(url, state) },
            onUpdate = { onUpdate(url, state) }
        )
    }
}

@Composable
fun DatabaseItem(
    state: DatabaseState,
    onItemClick: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit
) {
    val isLocal = state.isLocal
    val displayName = state.displayName

    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable {
                    if (onItemClick != null) {
                        onItemClick()
                    } else {
                        isExpanded = !isExpanded
                    }
                }
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadge(state.status)
                ReadOnlyBadge(isReadOnly = state.isReadOnly)
            }

            AnimatedVisibility(visible = isExpanded) {
                if (state.url.isNotBlank()) {
                    Text(
                        text = state.url,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (state.status == io.github.rumcajs.offlinewebsearch.data.DatabaseStatus.FAILED && !state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage,
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (!isLocal) {
            IconButton(onClick = onUpdate) {
                Icon(Icons.Default.Refresh, contentDescription = "Update")
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun DatabaseActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
