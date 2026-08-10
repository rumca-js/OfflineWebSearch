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

/**
 * Entry edit screen. Allows user to edit title and description of an entry
 * only if the active database is read-write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    entry: Entry,
    onEntryUpdated: (Entry) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState

    val isEditable = activeDbState != null && !activeDbState.isReadOnly && activeDbState.extension == ".db"

    var title by remember { mutableStateOf(entry.title ?: "") }
    var description by remember { mutableStateOf(entry.description ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Entry") },
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
                                        EntryListRepository.updateEntryInSql(
                                            context = context,
                                            activeDatabaseState = activeDbState,
                                            id = entry.id,
                                            originalLink = entry.link,
                                            newTitle = title,
                                            newDescription = description
                                        )
                                    } else false

                                    isSaving = false
                                    if (success) {
                                        Toast.makeText(context, "Entry updated successfully", Toast.LENGTH_SHORT).show()
                                        val updatedEntry = entry.copy(
                                            title = title,
                                            description = description
                                        )
                                        onEntryUpdated(updatedEntry)
                                    } else {
                                        Toast.makeText(context, "Failed to update entry", Toast.LENGTH_SHORT).show()
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
                                EntryListRepository.updateEntryInSql(
                                    context = context,
                                    activeDatabaseState = activeDbState,
                                    id = entry.id,
                                    originalLink = entry.link,
                                    newTitle = title,
                                    newDescription = description
                                )
                            } else false

                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "Entry updated successfully", Toast.LENGTH_SHORT).show()
                                val updatedEntry = entry.copy(
                                    title = title,
                                    description = description
                                )
                                onEntryUpdated(updatedEntry)
                            } else {
                                Toast.makeText(context, "Failed to update entry", Toast.LENGTH_SHORT).show()
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
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
