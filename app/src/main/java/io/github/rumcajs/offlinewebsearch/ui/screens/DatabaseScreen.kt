package io.github.rumcajs.offlinewebsearch.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.ui.components.DatabasePropertyRow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    url: String?,
    state: DatabaseState,
    dbConfig: DatabaseConfiguration,
    isActive: Boolean,
    onBack: () -> Unit,
    onSetActive: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showRefreshDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf(url ?: "") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream: InputStream? = if (state.localFileName.isBlank()) {
                        context.assets.open("places_0.json")
                    } else {
                        val dbFile = File(context.filesDir, state.localFileName)
                        if (dbFile.exists()) dbFile.inputStream() else null
                    }

                    if (inputStream != null) {
                        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                            inputStream.use { input ->
                                input.copyTo(outputStream)
                            }
                        }
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Database exported successfully", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Failed to find database file", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (showRefreshDialog && !state.isLocal && url != null) {
        io.github.rumcajs.offlinewebsearch.ui.components.RefreshConfirmationDialog(
            url = url,
            state = state,
            onDismiss = { showRefreshDialog = false },
            onConfirm = { targetUrl, _ ->
                showRefreshDialog = false
                val started = AppConfigManager.refreshDatabaseInBackground(context, targetUrl)
                if (started) {
                    Toast.makeText(context, "Database refresh started", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showEditDialog && url != null) {
        io.github.rumcajs.offlinewebsearch.ui.components.AddByUrlDialog(
            urlInput = urlInput,
            editingUrl = url,
            isVerifying = isVerifying,
            verificationError = verificationError,
            onUrlInputChange = {
                urlInput = it
                verificationError = null
            },
            onDismiss = { if (!isVerifying) showEditDialog = false },
            onSave = {
                val isZip = urlInput.endsWith(".db.zip", ignoreCase = true) || urlInput.endsWith(".zip", ignoreCase = true)
                val newState = DatabaseState.fromUrl(urlInput)
                if (newState.extension != ".json" && newState.extension != ".db" && !isZip) {
                    verificationError = "URL must end with .json, .db, .zip, or .db.zip"
                } else {
                    showEditDialog = false
                    AppConfigManager.refreshDatabaseInBackground(context, urlInput)
                    Toast.makeText(context, "Database update started", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showDeleteDialog && url != null) {
        io.github.rumcajs.offlinewebsearch.ui.components.RemoveConfirmationDialog(
            url = url,
            state = state,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { targetUrl, targetState ->
                showDeleteDialog = false
                AppConfigManager.removeDatabaseAndFiles(context, targetUrl, targetState.localFileName)
                onBack()
            }
        )
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0L) }

    val refreshDatabaseInfo: () -> Unit = {
        scope.launch {
            isRefreshing = true
            AppConfigManager.reloadConfig(context)
            refreshTrigger = System.currentTimeMillis()
            isRefreshing = false
            Toast.makeText(context, "Database information refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val fileNameToExport = if (state.localFileName.isNotBlank()) state.localFileName else "places_0.json"
                        exportLauncher.launch(fileNameToExport)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Database")
                    }
                    if (!state.isLocal && url != null) {
                        IconButton(onClick = { showRefreshDialog = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Database")
                        }
                    }
                    if (url != null) {
                        IconButton(onClick = {
                            urlInput = url
                            verificationError = null
                            showEditDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Database")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Database")
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = refreshDatabaseInfo,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Currently Active Database",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                io.github.rumcajs.offlinewebsearch.ui.components.DatabaseConfigPane(
                    url = url,
                    dbConfig = dbConfig
                )

                Spacer(modifier = Modifier.height(24.dp))

                io.github.rumcajs.offlinewebsearch.ui.components.DatabaseStatePane(
                    state = state,
                    refreshTrigger = refreshTrigger
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "History Actions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val canClearHistory = state.isSQLite && !state.isReadOnly

                getRepositoriesToClear().forEach { repoClearItem ->
                    ClearRepositoryButton(
                        item = repoClearItem,
                        enabled = canClearHistory,
                        context = context,
                        state = state,
                        scope = scope
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isActive) {
                    Button(
                        onClick = onSetActive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set as Active Database")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                var isDuplicating by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isDuplicating = true
                            val success = AppConfigManager.duplicateDatabase(context, state)
                            isDuplicating = false
                            if (success) {
                                Toast.makeText(context, "Database copy created", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to create database copy", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isDuplicating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isDuplicating) "Creating Copy..." else "Create a Copy")
                }
            }
        }
    }
}

/**
 * Describes a single repository that can be cleared from DatabaseScreen.
 *
 * @property label Human-readable label shown on the clear button.
 * @property repository The repository whose [clear] operation is invoked.
 */
data class RepoClearItem(
    val label: String,
    val repository: io.github.rumcajs.offlinewebsearch.data.RepositoryInterface
)

/**
 * Returns the list of repositories that the user can clear from DatabaseScreen,
 * together with their display labels.
 */
fun getRepositoriesToClear(): List<RepoClearItem> = listOf(
    RepoClearItem("Search History", io.github.rumcajs.offlinewebsearch.data.SearchHistoryRepository),
    RepoClearItem("Entry Transition History", io.github.rumcajs.offlinewebsearch.data.EntryTransitionHistoryRepository),
    RepoClearItem("Entry Visit History", io.github.rumcajs.offlinewebsearch.data.EntryVisitHistoryRepository),
    RepoClearItem("Social Data", io.github.rumcajs.offlinewebsearch.data.SocialDataRepository),
    RepoClearItem("Entry Compacted Tags", io.github.rumcajs.offlinewebsearch.data.EntryCompactedTagsRepository),
    RepoClearItem("Read later", io.github.rumcajs.offlinewebsearch.data.ReadLaterRepository)
)

/**
 * Renders a single clear button and its confirmation dialog for a given [RepoClearItem].
 *
 * @param item The repository item to clear.
 * @param enabled Whether the button should be enabled (e.g. database is writable SQLite).
 * @param context Application context used for Toast messages.
 * @param state Current [DatabaseState] passed to the repository's [clear] function.
 * @param scope Coroutine scope used to launch the clear operation.
 */
@Composable
fun ClearRepositoryButton(
    item: RepoClearItem,
    enabled: Boolean,
    context: android.content.Context,
    state: io.github.rumcajs.offlinewebsearch.data.DatabaseState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var showDialog by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        enabled = enabled && !isClearing,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(if (isClearing) "Clearing ${item.label}..." else "Clear ${item.label}")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Clear ${item.label}") },
            text = { Text("Are you sure you want to clear the ${item.label} table?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        scope.launch {
                            isClearing = true
                            val (success, error) = item.repository.clear(context, state)
                            isClearing = false
                            if (success) {
                                Toast.makeText(context, "${item.label} cleared", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to clear ${item.label}: ${error ?: "Unknown error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
