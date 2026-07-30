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
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.ui.components.DatabaseDetailDialog
import io.github.rumcajs.offlinewebsearch.ui.components.ReadOnlyBadge
import io.github.rumcajs.offlinewebsearch.ui.components.StatusBadge
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabasesScreen() {
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var selectedDetailState by remember { mutableStateOf<Pair<String?, DatabaseState>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context, uri) ?: "local_db.json"
            if (fileName.endsWith(".json", ignoreCase = true) || fileName.endsWith(".db", ignoreCase = true)) {
                selectedFileUri = uri
                urlInput = "local://$fileName"
                verificationError = null
            } else {
                verificationError = "Unsupported file extension. Please select a .json or .db file."
                urlInput = ""
                selectedFileUri = null
            }
        }
    }

    suspend fun handleSaveDatabase(url: String, editUrl: String?, fileUri: Uri?) {
        isVerifying = true
        verificationError = null
        val state = DatabaseState.fromUrl(url)
        try {
            if (state.isLocal && fileUri != null) {
                AppConfigManager.saveDatabaseLocal(context, url, fileUri, editUrl)
            } else if (!state.isLocal) {
                AppConfigManager.saveDatabaseFromInternet(context, url, editUrl)
            }
            showAddDialog = false
        } catch (e: Exception) {
            verificationError = e.message ?: "Failed to save database"
        } finally {
            isVerifying = false
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
            IconButton(onClick = {
                urlInput = ""
                editingUrl = null
                verificationError = null
                selectedFileUri = null
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Database")
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
                selectedDetailState = Pair(null, defaultState)
            },
            onSetActive = {
                AppConfigManager.setActiveDatabase(null)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Configured Databases
        config.databases.forEach { (url, state) ->
            val isActive = config.activeDatabase == url
            val dbConfig = config.dbConfigs[url] ?: config.defaultDbConfig

            DatabaseCardItem(
                name = state.displayName,
                state = state,
                isActive = isActive,
                onCardClick = {
                    selectedDetailState = Pair(url, state)
                },
                onSetActive = {
                    AppConfigManager.setActiveDatabase(url)
                },
                onEdit = {
                    urlInput = url
                    editingUrl = url
                    verificationError = null
                    selectedFileUri = null
                    showAddDialog = true
                },
                onDelete = {
                    AppConfigManager.removeDatabase(url)
                    File(context.filesDir, state.localFileName).delete()
                },
                onUpdate = if (!state.isLocal) {
                    {
                        scope.launch {
                            val response = NetworkUtils.getResponseFull(url)
                            val content = if (response.isValid) response.text?.toByteArray(Charsets.UTF_8) else null
                            if (content != null) {
                                context.openFileOutput(state.localFileName, Context.MODE_PRIVATE).use {
                                    it.write(content)
                                }
                                Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to update database", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else null
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Details Dialog when user taps a database
    selectedDetailState?.let { (url, state) ->
        val dbConfig = if (url == null) config.defaultDbConfig else config.dbConfigs[url] ?: config.defaultDbConfig
        val isActive = if (url == null) config.activeDatabase == null else config.activeDatabase == url

        DatabaseDetailDialog(
            state = state,
            dbConfig = dbConfig,
            isActive = isActive,
            onDismissRequest = { selectedDetailState = null },
            onSetActive = { AppConfigManager.setActiveDatabase(url) },
            onUpdate = if (url != null && !state.isLocal) {
                {
                    scope.launch {
                        val response = NetworkUtils.getResponseFull(url)
                        val content = if (response.isValid) response.text?.toByteArray(Charsets.UTF_8) else null
                        if (content != null) {
                            context.openFileOutput(state.localFileName, Context.MODE_PRIVATE).use {
                                it.write(content)
                            }
                            Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to update database", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else null
        )
    }

    // Add / Edit Database Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!isVerifying) showAddDialog = false },
            title = { Text(if (editingUrl == null) "Add Database" else "Edit Database") },
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
                        supportingText = verificationError?.let { { Text(it) } },
                        readOnly = urlInput.startsWith("local://")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifying
                    ) {
                        Text("Pick local file")
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
                        scope.launch { handleSaveDatabase(urlInput, editingUrl, selectedFileUri) }
                    },
                    enabled = urlInput.isNotBlank() && !isVerifying
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }, enabled = !isVerifying) {
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
