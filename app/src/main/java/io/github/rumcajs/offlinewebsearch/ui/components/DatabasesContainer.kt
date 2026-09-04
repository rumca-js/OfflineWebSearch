package io.github.rumcajs.offlinewebsearch.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabasesContainer(
    onNavigateToDatabaseDetail: (String?, DatabaseState) -> Unit = { _, _ -> },
    onNavigateToPreselectedList: () -> Unit = {},
    onSetActive: (String?) -> Unit
) {
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddDialogMode by remember { mutableStateOf<String?>(null) } // "url", "asset"
    var editingUrl by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    var refreshingDb by remember { mutableStateOf<Pair<String, DatabaseState>?>(null) }
    var deletingDb by remember { mutableStateOf<Pair<String, DatabaseState>?>(null) }

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

    fun handleSaveDatabaseFromInternet(
        urlInput: String,
        editingUrl: String?
    ) {
        val isZip = urlInput.endsWith(".db.zip", ignoreCase = true) || urlInput.endsWith(".zip", ignoreCase = true)
        val state = DatabaseState.fromUrl(urlInput)
        if (state.extension != ".json" && state.extension != ".db" && !isZip) {
            verificationError = "URL must end with .json, .db, .zip, or .db.zip"
            return
        }

        // Close dialog immediately
        showAddDialogMode = null
        isVerifying = false

        // Launch download in background scope so state updates in container reactively
        AppConfigManager.refreshDatabaseInBackground(context, urlInput)
    }

    fun refreshDatabase(url: String, state: DatabaseState) {
        if (!state.isLocal) {
            val started = AppConfigManager.refreshDatabaseInBackground(context, url)
            if (started) {
                Toast.makeText(context, "Database refresh started", Toast.LENGTH_SHORT).show()
            }
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
            isVerifying = false
        } else {
            handleSaveDatabaseFromInternet(urlInput, editingUrl)
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
                onClick = onNavigateToPreselectedList
            )

            DatabaseActionButton(
                text = "New",
                onClick = {
                    urlInput = "new_database.db"
                    editingUrl = null
                    verificationError = null
                    selectedFileUri = null
                    showAddDialogMode = "asset"
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
            activeDatabaseUrl = config.activeDatabase,
            onItemClick = { url, state ->
                onNavigateToDatabaseDetail(url, state)
            },
            onSetActive = { url ->
                onSetActive(url)
            },
            onDelete = { url, state ->
                deletingDb = Pair(url, state)
            },
            onUpdate = { url, state ->
                refreshingDb = Pair(url, state)
            }
        )

        // Refresh Confirmation Dialog
        refreshingDb?.let { (url, state) ->
            RefreshConfirmationDialog(
                url = url,
                state = state,
                onDismiss = { refreshingDb = null },
                onConfirm = { targetUrl, targetState ->
                    refreshingDb = null
                    refreshDatabase(targetUrl, targetState)
                }
            )
        }

        // Delete Confirmation Dialog
        deletingDb?.let { (url, state) ->
            RemoveConfirmationDialog(
                url = url,
                state = state,
                onDismiss = { deletingDb = null },
                onConfirm = { targetUrl, targetState ->
                    deletingDb = null
                    AppConfigManager.removeDatabaseAndFiles(context, targetUrl, targetState.localFileName)
                }
            )
        }

        // Add by URL Dialog
        if (showAddDialogMode == "url") {
            AddByUrlDialog(
                urlInput = urlInput,
                editingUrl = editingUrl,
                isVerifying = isVerifying,
                verificationError = verificationError,
                onUrlInputChange = {
                    urlInput = it
                    verificationError = null
                },
                onDismiss = { if (!isVerifying) showAddDialogMode = null },
                onSave = {
                    scope.launch {
                        handleSaveDatabase(urlInput, editingUrl, null)
                        if (verificationError == null) showAddDialogMode = null
                    }
                }
            )
        }

        // Create from Asset Dialog
        if (showAddDialogMode == "asset") {
            CreateFromAssetDialog(
                databaseNameInput = urlInput,
                isCreating = isVerifying,
                creationError = verificationError,
                onDatabaseNameChange = {
                    urlInput = it
                    verificationError = null
                },
                onDismiss = { if (!isVerifying) showAddDialogMode = null },
                onCreate = {
                    scope.launch {
                        isVerifying = true
                        verificationError = null
                        try {
                            AppConfigManager.createDatabaseFromAsset(context, customName = urlInput)
                            showAddDialogMode = null
                        } catch (e: Exception) {
                            verificationError = e.message ?: "Failed to create database from asset"
                        } finally {
                            isVerifying = false
                        }
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
    activeDatabaseUrl: String? = null,
    onItemClick: ((String?, DatabaseState) -> Unit)? = null,
    onSetActive: (String?) -> Unit,
    onDelete: (String, DatabaseState) -> Unit,
    onUpdate: (String, DatabaseState) -> Unit
) {
    // The "Default (Assets)" database is always available even though it has no
    // entry in the databases map. Show it as a permanent, read-only first item.
    val defaultState = DatabaseState(
        url = "",
        localFileName = "",
        status = io.github.rumcajs.offlinewebsearch.data.DatabaseStatus.READY,
        progress = 1f,
        isReadOnly = true
    )
    val isDefaultActive = activeDatabaseUrl == null

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        DefaultDatabaseItem(
            isActive = isDefaultActive,
            onItemClick = if (onItemClick != null) {
                { onItemClick(null, defaultState) }
            } else null,
            onSetActive = { onSetActive(null) }
        )

        databases.forEach { (url, state) ->
            val isActive = activeDatabaseUrl == url
            DatabaseItem(
                state = state,
                isActive = isActive,
                onItemClick = { onItemClick?.invoke(url, state) },
                onSetActive = { onSetActive(url) },
                onDelete = { onDelete(url, state) },
                onUpdate = { onUpdate(url, state) }
            )
        }
    }
}

/**
 * A fixed row representing the built-in "Default (Assets)" database.
 *
 * This database is always present (backed by bundled asset files), so it is shown
 * permanently at the top of the list regardless of the user-added database map.
 * It cannot be edited, deleted, or refreshed.
 *
 * @param isActive Whether this database is currently active.
 * @param onItemClick Optional callback to navigate to the database detail screen on long press.
 * @param onSetActive Optional callback to make this database active on single tap.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DefaultDatabaseItem(
    isActive: Boolean = false,
    onItemClick: (() -> Unit)? = null,
    onSetActive: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val borderStroke = if (isActive) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        shape = shape,
        border = borderStroke,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onSetActive,
                onLongClick = { onItemClick?.invoke() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Default (Assets)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusBadge(io.github.rumcajs.offlinewebsearch.data.DatabaseStatus.READY)
                    ReadOnlyBadge(isReadOnly = true)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DatabaseItem(
    state: DatabaseState,
    isActive: Boolean = false,
    onItemClick: (() -> Unit)? = null,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit
) {
    val isLocal = state.isLocal
    val displayName = state.displayName

    val shape = RoundedCornerShape(24.dp)
    val borderStroke = if (isActive) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        shape = shape,
        border = borderStroke,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onSetActive,
                onLongClick = { onItemClick?.invoke() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
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

/**
 * Confirmation dialog shown before refreshing a database.
 */
@Composable
fun RefreshConfirmationDialog(
    url: String,
    state: DatabaseState,
    onDismiss: () -> Unit,
    onConfirm: (String, DatabaseState) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Refresh Database") },
        text = { Text("Are you sure you want to refresh '${state.displayName}'? This will replace the current local version of the database.") },
        confirmButton = {
            Button(
                onClick = { onConfirm(url, state) }
            ) {
                Text("Refresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Confirmation dialog shown before removing/deleting a database.
 */
@Composable
fun RemoveConfirmationDialog(
    url: String,
    state: DatabaseState,
    onDismiss: () -> Unit,
    onConfirm: (String, DatabaseState) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Database") },
        text = { Text("Are you sure you want to delete '${state.displayName}'?") },
        confirmButton = {
            Button(
                onClick = { onConfirm(url, state) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Alias for RemoveConfirmationDialog.
 */
@Composable
fun DeleteConfirmationDialog(
    url: String,
    state: DatabaseState,
    onDismiss: () -> Unit,
    onConfirm: (String, DatabaseState) -> Unit
) = RemoveConfirmationDialog(url, state, onDismiss, onConfirm)

@Composable
fun AddByUrlDialog(
    urlInput: String,
    editingUrl: String?,
    isVerifying: Boolean,
    verificationError: String?,
    onUrlInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingUrl == null) "Add Database by URL" else "Edit Database URL") },
        text = {
            Column {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlInputChange,
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
                onClick = onSave,
                enabled = urlInput.isNotBlank() && !isVerifying
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateFromAssetDialog(
    databaseNameInput: String,
    isCreating: Boolean,
    creationError: String?,
    onDatabaseNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Database from Asset") },
        text = {
            Column {
                Text("Specify a filename for the new database created from assets/table.db:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = databaseNameInput,
                    onValueChange = onDatabaseNameChange,
                    label = { Text("Database Name (.db)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = creationError != null,
                    supportingText = creationError?.let { { Text(it) } }
                )
                if (isCreating) {
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
                onClick = onCreate,
                enabled = databaseNameInput.isNotBlank() && !isCreating
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Cancel")
            }
        }
    )
}


