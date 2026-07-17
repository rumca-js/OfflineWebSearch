package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen() {
    val config by io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(
                context,
                uri
            ) ?: "local_db.json"
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

    suspend fun handleSaveDatabase(
        urlInput: String,
        editingUrl: String?,
        selectedFileUri: Uri?
    ) {
        isVerifying = true
        verificationError = null

        val state = DatabaseState(url = urlInput)

        if (state.isLocal) {
            if (selectedFileUri != null) {
                try {
                    val content = context.contentResolver.openInputStream(selectedFileUri)?.use {
                        it.readBytes()
                    }
                    if (content != null) {
                        AppConfigManager.saveDatabaseSource(context, urlInput, content, editingUrl)
                        showDialog = false
                    } else {
                        verificationError = "Failed to read file"
                    }
                } catch (e: Exception) {
                    verificationError = "Error reading file: ${e.message}"
                }
            } else {
                showDialog = false
            }
        } else {
            // Check extension
            if (state.extension != ".json" && state.extension != ".db") {
                verificationError = "URL must end with .json or .db"
                isVerifying = false
                return
            }

            if (NetworkUtils.verifyUrl(urlInput)) {
                if (editingUrl == null) {
                    AppConfigManager.addDatabase(urlInput)
                } else {
                    AppConfigManager.updateDatabase(editingUrl, urlInput)
                }

                val response = NetworkUtils.getResponseFull(urlInput)
                val content = if (response.isValid) response.text?.toByteArray() else null
                if (content != null) {
                    AppConfigManager.saveDatabaseSource(context, urlInput, content, editingUrl)
                }
                showDialog = false
            } else {
                verificationError = "Invalid URL or server unreachable"
            }
        }
        isVerifying = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(text = "Options", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.OptionItem(
            label = "Direct links",
            checked = config.dbconfig.directLinks,
            onCheckedChange = {
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setDirectLinks(
                    it
                )
            }
        )

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.OptionItem(
            label = "Show icons",
            checked = config.dbconfig.showIcons,
            onCheckedChange = {
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setShowIcons(
                    it
                )
            }
        )

        /*
        Preview does not work. White frame
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.OptionItem(
            label = "Video preview",
            checked = config.videoPreview,
            onCheckedChange = {
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setVideoPreview(
                    it
                )
            }
        )
        */

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "User Age", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = if (config.userAge == 0) "" else config.userAge.toString(),
            onValueChange = {
                val newAge = it.toIntOrNull() ?: 0
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setUserAge(newAge)
            },
            label = { Text("Your Age") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            placeholder = { Text("0") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Order By", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        var orderByExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = orderByExpanded,
            onExpandedChange = { orderByExpanded = !orderByExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = config.dbconfig.orderBy.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = orderByExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = orderByExpanded,
                onDismissRequest = { orderByExpanded = false }
            ) {
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.OrderBy.values().forEach { orderByOption ->
                    DropdownMenuItem(
                        text = { Text(orderByOption.displayName) },
                        onClick = {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setOrderBy(orderByOption)
                            orderByExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "View Style", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        var viewStyleExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = viewStyleExpanded,
            onExpandedChange = { viewStyleExpanded = !viewStyleExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = config.dbconfig.viewStyle.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewStyleExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = viewStyleExpanded,
                onDismissRequest = { viewStyleExpanded = false }
            ) {
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.ViewStyle.values().forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.displayName) },
                        onClick = {
                            _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setViewStyle(style)
                            viewStyleExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Active Database",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }, // Let the component dictate state updates
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                // Clean display property from our AppConfiguration!
                value = config.activeDatabaseDisplayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable) // M3 Best Practice for non-editable drop-downs
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // 1. Default Assets Option
                DropdownMenuItem(
                    text = { Text("Default (Assets)") },
                    onClick = {
                        AppConfigManager.setActiveDatabase(null)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )

                // 2. Dynamic Database Options (destructured cleanly into url and state)
                config.databases.forEach { (url, state) ->
                    DropdownMenuItem(
                        // Leverages the model's clean display name directly!
                        text = { Text(state.displayName) },
                        onClick = {
                            AppConfigManager.setActiveDatabase(url)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Databases", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = {
                urlInput = ""
                editingUrl = null
                verificationError = null
                selectedFileUri = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Database")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        config.databases.forEach { (url, state) ->
            DatabaseItem(
                url = url,
                onEdit = {
                    urlInput = url
                    editingUrl = url
                    verificationError = null
                    selectedFileUri = null
                    showDialog = true
                },
                onDelete = {
                    AppConfigManager.removeDatabase(
                        url
                    )
                    // Optionally delete the local file
                    val fileName = state.localFileName
                    File(context.filesDir, fileName).delete()
                },
                onUpdate = {
                    if (!state.isLocal) {
                        scope.launch {
                            val response =
                                NetworkUtils.getResponseFull(
                                    url
                                )
                            val content = if (response.isValid) {
                                response.text?.toByteArray(Charsets.UTF_8)
                            } else null
                            if (content != null) {
                                val fileName = state.localFileName
                                context.openFileOutput(
                                    fileName,
                                    Context.MODE_PRIVATE
                                ).use {
                                    it.write(content)
                                }
                                Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT)
                                    .show()
                            } else {
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
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!isVerifying) showDialog = false },
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
                        scope.launch {
                            handleSaveDatabase(urlInput, editingUrl, selectedFileUri)
                        }
                    },
                    enabled = urlInput.isNotBlank() && !isVerifying
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    enabled = !isVerifying
                ) {
                    Text("Cancel")
                }
            }
        )
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
fun DatabaseItem(url: String, onEdit: () -> Unit, onDelete: () -> Unit, onUpdate: () -> Unit) {
    val isLocal = url.startsWith("local://")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isLocal) url.removePrefix("local://") else url,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
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
fun OptionItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = checked,
                onClick = { onCheckedChange(!checked) },
                role = Role.Checkbox
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null // Handled by Row selectable
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
