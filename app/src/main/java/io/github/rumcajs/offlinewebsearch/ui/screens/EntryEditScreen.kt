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
import io.github.rumcajs.offlinewebsearch.data.EntryListRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entry edit/add screen. When [entry] has a null id it operates in "add" mode
 * (calls [EntryListRepository.addEntryToSql]); otherwise it edits the existing
 * entry (calls [EntryListRepository.updateEntryInSql]).
 * Only enabled when the active database is read-write.
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

    val isEditable = activeDbState != null && !activeDbState.isReadOnly && activeDbState.extension == ".db"

    var title by remember { mutableStateOf(entry.title ?: "") }
    var link by remember { mutableStateOf(entry.link ?: "") }
    var description by remember { mutableStateOf(entry.description ?: "") }
    var isSaving by remember { mutableStateOf(false) }

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
                                    val success = if (activeDbState != null) {
                                        if (isAddMode) {
                                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                                            EntryListRepository.addEntryToSql(
                                                context = context,
                                                activeDatabaseState = activeDbState,
                                                entry = entry.copy(
                                                    link = link,
                                                    title = title,
                                                    description = description,
                                                    date_created = now,
                                                    date_published = now
                                                )
                                            )
                                        } else {
                                            EntryListRepository.updateEntryInSql(
                                                context = context,
                                                activeDatabaseState = activeDbState,
                                                id = entry.id,
                                                originalLink = entry.link,
                                                newTitle = title,
                                                newDescription = description
                                            )
                                        }
                                    } else false

                                    isSaving = false
                                    if (success) {
                                        val msg = if (isAddMode) "Entry added successfully" else "Entry updated successfully"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        val updatedEntry = entry.copy(
                                            link = link,
                                            title = title,
                                            description = description
                                        )
                                        onEntryUpdated(updatedEntry)
                                    } else {
                                        val msg = if (isAddMode) "Failed to add entry" else "Failed to update entry"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
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
                onValueChange = { if (isEditable) link = it },
                label = { Text("URL") },
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        coroutineScope.launch {
                            val success = if (activeDbState != null) {
                                if (isAddMode) {
                                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                                    EntryListRepository.addEntryToSql(
                                        context = context,
                                        activeDatabaseState = activeDbState,
                                        entry = entry.copy(
                                            link = link,
                                            title = title,
                                            description = description,
                                            date_created = now,
                                            date_published = now
                                        )
                                    )
                                } else {
                                    EntryListRepository.updateEntryInSql(
                                        context = context,
                                        activeDatabaseState = activeDbState,
                                        id = entry.id,
                                        originalLink = entry.link,
                                        newTitle = title,
                                        newDescription = description
                                    )
                                }
                            } else false

                            isSaving = false
                            if (success) {
                                val msg = if (isAddMode) "Entry added successfully" else "Entry updated successfully"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                val updatedEntry = entry.copy(
                                    link = link,
                                    title = title,
                                    description = description
                                )
                                onEntryUpdated(updatedEntry)
                            } else {
                                val msg = if (isAddMode) "Failed to add entry" else "Failed to update entry"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSaving,
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
