package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.data.EntryEnrichmentWorker
import io.github.rumcajs.offlinewebsearch.data.EntryRepository
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation

/**
 * Entry edit/add screen. When [entry] has a null id it operates in "add" mode
 * (calls [EntryRepository.addEntryToSql]); otherwise it edits the existing
 * entry (calls [EntryRepository.updateEntryInSql]).
 * Only enabled when the active database is read-write (.db).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    entry: Entry,
    onEntryUpdated: (Entry) -> Unit,
    onBack: () -> Unit
) {
    val isAddMode = entry.id == null
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState

    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    var title by remember { mutableStateOf(entry.title ?: "") }
    var link by remember { mutableStateOf(entry.link ?: "") }
    var description by remember { mutableStateOf(entry.description ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val canSave = link.isNotBlank()

    /** Runs the appropriate insert or update and returns whether it succeeded. */
    suspend fun saveEntry(): Boolean {
        if (isAddMode && !UrlLocation(link).isWebLink()) {
            linkError = "URL must start with http://, https://, smb://, or ftp:// and have a valid domain"
            return false
        }
        linkError = null
        return if (activeDbState == null) {
            errorMessage = "No active database selected"
            false
        } else if (isAddMode) {
            val now = DateUtils.getCurrentTimestamp() // TODO - not ISO?
            val (success, rowId, err) = EntryRepository.add(
                context = context,
                activeDatabaseState = activeDbState,
                entry = entry.copy(
                    link = link,
                    title = title.trim(),
                    description = description.trim(),
                    date_created = now,
                    date_published = now
                )
            )
            errorMessage = if (!success) err else null

            // Capture as non-null local – smart-cast does not survive the lambda boundary.
            val dbState = activeDbState
            // Fire-and-forget background enrichment when network is available.
            if (success && rowId != -1L && !config.networkConfig.disabled && dbState != null) {
                coroutineScope.launch {
                    EntryEnrichmentWorker.enrich(
                        context = context,
                        activeDbState = dbState,
                        entryId = rowId,
                        link = link,
                        currentTitle = title.trim(),
                        currentDescription = description.trim()
                    )
                }
            }

            success
        } else {
            val success = EntryRepository.update(
                context = context,
                activeDatabaseState = activeDbState,
                id = entry.id,
                originalLink = entry.link,
                newTitle = title,
                newDescription = description
            )
            errorMessage = if (!success) "Failed to update entry" else null
            success
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAddMode) "Add Entry" else "Edit Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditable) {
                        IconButton(
                            onClick = {
                                if (isSaving) return@IconButton
                                isSaving = true
                                coroutineScope.launch {
                                    val success = saveEntry()
                                    isSaving = false
                                    if (success) {
                                        val msg = if (isAddMode) "Entry added successfully" else "Entry updated successfully"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        onEntryUpdated(
                                            entry.copy(link = link, title = title, description = description)
                                        )
                                    } else {
                                        val msg = errorMessage ?: if (isAddMode) "Failed to add entry" else "Failed to update entry"
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
            // ── SQLite source info banner ──────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = if (activeDbState != null)
                        "Source: ${activeDbState.displayName} (${activeDbState.localFileName})"
                    else
                        "Source: Default (Assets) – read-only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // ── Read-only warning ─────────────────────────────────────────────
            if (!isEditable) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Database is read-only. Editing is disabled.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ── Fields ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = title,
                onValueChange = { if (isEditable) title = it },
                label = { Text("Title") },
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = link,
                onValueChange = {
                    if (isEditable) {
                        link = it
                        linkError = null
                    }
                },
                label = { Text("URL") },
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = linkError != null,
                supportingText = linkError?.let { { Text(it) } }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { if (isEditable) description = it },
                label = { Text("Description") },
                enabled = isEditable,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isEditable) {
                // ── Inline SQL error message ───────────────────────────────────
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
                            val success = saveEntry()
                            isSaving = false
                            if (success) {
                                val msg = if (isAddMode) "Entry added successfully" else "Entry updated successfully"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onEntryUpdated(
                                    entry.copy(link = link, title = title, description = description)
                                )
                            } else {
                                val msg2 = errorMessage ?: if (isAddMode) "Failed to add entry" else "Failed to update entry"
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
                        Text(if (isAddMode) "Add Entry" else "Save Changes")
                    }
                }
            }
        }
    }
}
