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
import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation
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

    val isAddMode = source.id == null
    val isEditable = activeDbState != null && !activeDbState.isReadOnly

    var title by remember { mutableStateOf(source.title) }
    var url by remember { mutableStateOf(source.url) }
    var enabled by remember { mutableStateOf(source.enabled) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val canSave = url.isNotBlank()

    /** Runs the appropriate insert or update and returns whether it succeeded. */
    suspend fun saveSource(): Boolean {
        if (!UrlLocation(url).isWebLink()) {
            urlError = "URL must start with http://, https://, smb://, or ftp:// and have a valid domain"
            return false
        }
        urlError = null
        return if (isAddMode) {
            val (success, err) = SourceRepository.insertSource(
                context = context,
                activeDatabaseState = activeDbState,
                title = title,
                url = url,
                enabled = enabled
            )
            errorMessage = if (!success) err else null
            success
        } else {
            val (success, err) = SourceRepository.updateSourceProperties(
                context = context,
                activeDatabaseState = activeDbState,
                id = source.id!!,
                title = title,
                url = url,
                enabled = enabled
            )
            errorMessage = if (!success) err else null
            success
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAddMode) "Add Source" else "Edit Source") },
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
                                    val success = saveSource()
                                    isSaving = false
                                    if (success) {
                                        Toast.makeText(context, if (isAddMode) "Source added" else "Source updated successfully", Toast.LENGTH_SHORT).show()
                                        onSourceUpdated(
                                            source.copy(
                                                title = title,
                                                url = url,
                                                enabled = enabled
                                            )
                                        )
                                    } else {
                                        val msg = errorMessage ?: "Failed to save source"
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
                onValueChange = {
                    if (isEditable) {
                        url = it
                        urlError = null
                    }
                },
                label = { Text("URL") },
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } }
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
                // Show any SQL error inline
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
                            val success = saveSource()
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, if (isAddMode) "Source added" else "Source updated successfully", Toast.LENGTH_SHORT).show()
                                onSourceUpdated(
                                    source.copy(
                                        title = title,
                                        url = url,
                                        enabled = enabled
                                    )
                                )
                            } else {
                                val msg2 = errorMessage ?: "Failed to save source"
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
                        Text(if (isAddMode) "Add Source" else "Save Changes")
                    }
                }
            }
        }
    }
}
