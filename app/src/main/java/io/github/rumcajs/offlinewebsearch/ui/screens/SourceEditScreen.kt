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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Source
import io.github.rumcajs.offlinewebsearch.data.SourceRepository
import kotlinx.coroutines.launch

/**
 * Source edit screen. Allows user to edit title, URL, and enabled state of a source
 * only if the active database is read-write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceEditScreen(
    source: Source,
    onSourceUpdated: (Source) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState

    val isEditable = activeDbState != null && !activeDbState.isReadOnly && activeDbState.extension == ".db"

    var title by remember { mutableStateOf(source.title) }
    var url by remember { mutableStateOf(source.url) }
    var enabled by remember { mutableStateOf(source.enabled) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Source") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditable) {
                        IconButton(
                            onClick = {
                                if (isSaving || source.id == null) return@IconButton
                                isSaving = true
                                coroutineScope.launch {
                                    val success = SourceRepository.updateSource(
                                        context = context,
                                        activeDatabaseState = activeDbState,
                                        id = source.id,
                                        title = title,
                                        url = url,
                                        enabled = enabled
                                    )
                                    isSaving = false
                                    if (success) {
                                        Toast.makeText(context, "Source updated successfully", Toast.LENGTH_SHORT).show()
                                        onSourceUpdated(
                                            source.copy(
                                                title = title,
                                                url = url,
                                                enabled = enabled
                                            )
                                        )
                                    } else {
                                        Toast.makeText(context, "Failed to update source", Toast.LENGTH_SHORT).show()
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
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { if (isEditable) url = it },
                label = { Text("URL") },
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = { if (isEditable) enabled = it },
                    enabled = isEditable
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isEditable) {
                Button(
                    onClick = {
                        if (isSaving || source.id == null) return@Button
                        isSaving = true
                        coroutineScope.launch {
                            val success = SourceRepository.updateSource(
                                context = context,
                                activeDatabaseState = activeDbState,
                                id = source.id,
                                title = title,
                                url = url,
                                enabled = enabled
                            )
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "Source updated successfully", Toast.LENGTH_SHORT).show()
                                onSourceUpdated(
                                    source.copy(
                                        title = title,
                                        url = url,
                                        enabled = enabled
                                    )
                                )
                            } else {
                                Toast.makeText(context, "Failed to update source", Toast.LENGTH_SHORT).show()
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
