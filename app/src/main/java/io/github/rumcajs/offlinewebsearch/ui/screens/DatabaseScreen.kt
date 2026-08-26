package io.github.rumcajs.offlinewebsearch.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
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
                scope.launch {
                    isVerifying = true
                    verificationError = null
                    try {
                        AppConfigManager.saveDatabaseFromInternet(context, urlInput, oldUrl = url)
                        showEditDialog = false
                        Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        verificationError = e.message ?: "Failed to update database"
                    } finally {
                        isVerifying = false
                    }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

            io.github.rumcajs.offlinewebsearch.ui.components.DatabaseStatePane(state = state)

            Spacer(modifier = Modifier.height(24.dp))

            io.github.rumcajs.offlinewebsearch.ui.components.DatabaseConfigurationPane(dbConfig = dbConfig)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "History Actions",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val isSql = state.extension == ".db"
            var showClearSearchHistoryDialog by remember { mutableStateOf(false) }
            var showClearTransitionHistoryDialog by remember { mutableStateOf(false) }
            var isClearingSearch by remember { mutableStateOf(false) }
            var isClearingTransitions by remember { mutableStateOf(false) }

            val canClearHistory = isSql && !state.isReadOnly

            Button(
                onClick = { showClearSearchHistoryDialog = true },
                enabled = canClearHistory && !isClearingSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(if (isClearingSearch) "Clearing Search History..." else "Clear Search History")
            }

            Button(
                onClick = { showClearTransitionHistoryDialog = true },
                enabled = canClearHistory && !isClearingTransitions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(if (isClearingTransitions) "Clearing Entry Transition History..." else "Clear Entry Transition History")
            }

            var showClearVisitHistoryDialog by remember { mutableStateOf(false) }
            var isClearingVisits by remember { mutableStateOf(false) }

            Button(
                onClick = { showClearVisitHistoryDialog = true },
                enabled = canClearHistory && !isClearingVisits,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(if (isClearingVisits) "Clearing Entry Visit History..." else "Clear Entry Visit History")
            }

            var showClearSocialDataDialog by remember { mutableStateOf(false) }
            var isClearingSocialData by remember { mutableStateOf(false) }

            Button(
                onClick = { showClearSocialDataDialog = true },
                enabled = canClearHistory && !isClearingSocialData,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(if (isClearingSocialData) "Clearing Social Data..." else "Clear Social Data")
            }

            if (showClearSearchHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showClearSearchHistoryDialog = false },
                    title = { Text("Clear Search History") },
                    text = { Text("Are you sure you want to clear the search history table?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearSearchHistoryDialog = false
                                scope.launch {
                                    isClearingSearch = true
                                    val (success, error) = io.github.rumcajs.offlinewebsearch.data.SearchHistoryRepository.clear(context, state)
                                    isClearingSearch = false
                                    if (success) {
                                        Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to clear search history: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearSearchHistoryDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showClearTransitionHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showClearTransitionHistoryDialog = false },
                    title = { Text("Clear Entry Transition History") },
                    text = { Text("Are you sure you want to clear the entry transition history table?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearTransitionHistoryDialog = false
                                scope.launch {
                                    isClearingTransitions = true
                                    val (success, error) = io.github.rumcajs.offlinewebsearch.data.EntryTransitionHistoryRepository.clear(context, state)
                                    isClearingTransitions = false
                                    if (success) {
                                        Toast.makeText(context, "Entry transition history cleared", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to clear entry transition history: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearTransitionHistoryDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showClearVisitHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showClearVisitHistoryDialog = false },
                    title = { Text("Clear Entry Visit History") },
                    text = { Text("Are you sure you want to clear the entry visit history table?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearVisitHistoryDialog = false
                                scope.launch {
                                    isClearingVisits = true
                                    val (success, error) = io.github.rumcajs.offlinewebsearch.data.EntryVisitHistoryRepository.clear(context, state)
                                    isClearingVisits = false
                                    if (success) {
                                        Toast.makeText(context, "Entry visit history cleared", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to clear entry visit history: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearVisitHistoryDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showClearSocialDataDialog) {
                AlertDialog(
                    onDismissRequest = { showClearSocialDataDialog = false },
                    title = { Text("Clear Social Data") },
                    text = { Text("Are you sure you want to clear the social data table?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearSocialDataDialog = false
                                scope.launch {
                                    isClearingSocialData = true
                                    val (success, error) = io.github.rumcajs.offlinewebsearch.data.SocialDataRepository.clear(context, state)
                                    isClearingSocialData = false
                                    if (success) {
                                        Toast.makeText(context, "Social data cleared", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to clear social data: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearSocialDataDialog = false }) {
                            Text("Cancel")
                        }
                    }
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
