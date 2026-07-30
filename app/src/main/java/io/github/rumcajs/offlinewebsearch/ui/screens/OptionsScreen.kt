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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.ui.components.DatabaseDetailDialog
import io.github.rumcajs.offlinewebsearch.ui.components.ReadOnlyBadge
import io.github.rumcajs.offlinewebsearch.ui.components.StatusBadge
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    onNavigateToDatabases: () -> Unit = {},
    onNavigateToDatabaseDetail: (String?, DatabaseState) -> Unit = { _, _ -> }
) {
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

    suspend fun handleSaveDatabaseLocal(
        urlInput: String,
        editingUrl: String?,
        selectedFileUri: Uri?
    ) {
        if (selectedFileUri != null) {
            try {
                AppConfigManager.saveDatabaseLocal(context, urlInput, selectedFileUri, editingUrl)
                showDialog = false
            } catch (e: Exception) {
                verificationError = e.message ?: "Failed to read file"
            }
        } else {
            showDialog = false
        }
    }

    suspend fun handleSaveDatabaseFromInternet(
        urlInput: String,
        editingUrl: String?
    ) {
        try {
            AppConfigManager.saveDatabaseFromInternet(context, urlInput, editingUrl)
            showDialog = false
        } catch (e: Exception) {
            verificationError = e.message ?: "Failed to download database files"
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
        } else {
            handleSaveDatabaseFromInternet(urlInput, editingUrl)
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

        Text(
            text = "Active Database",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        var activeDbExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = activeDbExpanded,
            onExpandedChange = { activeDbExpanded = it }, // Let the component dictate state updates
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                // Clean display property from our AppConfiguration!
                value = config.activeDatabaseDisplayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = activeDbExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable) // M3 Best Practice for non-editable drop-downs
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = activeDbExpanded,
                onDismissRequest = { activeDbExpanded = false }
            ) {
                // 1. Default Assets Option
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Default (Assets)")
                            ReadOnlyBadge(isReadOnly = true)
                        }
                    },
                    onClick = {
                        AppConfigManager.setActiveDatabase(null)
                        activeDbExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )

                // 2. Dynamic Database Options (destructured cleanly into url and state)
                config.databases.forEach { (url, state) ->
                    DropdownMenuItem(
                        // Leverages the model's clean display name directly!
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.displayName)
                                ReadOnlyBadge(isReadOnly = state.isReadOnly)
                            }
                        },
                        onClick = {
                            AppConfigManager.setActiveDatabase(url)
                            activeDbExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OptionItem(
            label = "Direct links",
            checked = config.dbconfig.directLinks,
            onCheckedChange = {
                _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.setDirectLinks(
                    it
                )
            }
        )

        OptionItem(
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
        OptionItem(
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Databases", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        var showAddDialogMode by remember { mutableStateOf<String?>(null) } // "url", "preset"
        var presetUrls by remember { mutableStateOf<List<String>>(emptyList()) }
        var isLoadingPresets by remember { mutableStateOf(false) }
        var selectedPresetUrl by remember { mutableStateOf("") }

        LaunchedEffect(showAddDialogMode) {
            if (showAddDialogMode == "preset" && presetUrls.isEmpty()) {
                isLoadingPresets = true
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val response = NetworkUtils.getResponseFull("https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases.txt")
                        val text = if (response.isValid) response.text else null
                        if (!text.isNullOrBlank()) {
                            val lines = text.lines()
                                .map { it.trim() }
                                .filter { it.startsWith("http://") || it.startsWith("https://") }
                            presetUrls = lines
                        }
                    }
                } catch (_: Exception) { }
                isLoadingPresets = false
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch("*/*")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Add local file", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    urlInput = ""
                    editingUrl = null
                    verificationError = null
                    selectedFileUri = null
                    showAddDialogMode = "url"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Add by URL", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    urlInput = ""
                    editingUrl = null
                    verificationError = null
                    selectedFileUri = null
                    selectedPresetUrl = ""
                    showAddDialogMode = "preset"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Preselected list", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DatabaseList(
            databases = config.databases,
            onItemClick = { url, state ->
                onNavigateToDatabaseDetail(url, state)
            },
            onEdit = { url ->
                urlInput = url
                editingUrl = url
                verificationError = null
                selectedFileUri = null
                showAddDialogMode = "url"
            },
            onDelete = { url, state ->
                AppConfigManager.removeDatabase(url)
                val fileName = state.localFileName
                File(context.filesDir, fileName).delete()
            },
            onUpdate = { url, state ->
                if (!state.isLocal) {
                    scope.launch {
                        val response = NetworkUtils.getResponseFull(url)
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
                            Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT).show()
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

        // Add by URL Dialog
        if (showAddDialogMode == "url") {
            AlertDialog(
                onDismissRequest = { if (!isVerifying) showAddDialogMode = null },
                title = { Text(if (editingUrl == null) "Add Database by URL" else "Edit Database URL") },
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
                        onClick = {
                            scope.launch {
                                handleSaveDatabase(urlInput, editingUrl, null)
                                if (verificationError == null) showAddDialogMode = null
                            }
                        },
                        enabled = urlInput.isNotBlank() && !isVerifying
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialogMode = null }, enabled = !isVerifying) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add from Preselected List Dialog
        if (showAddDialogMode == "preset") {
            var dropdownExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isVerifying) showAddDialogMode = null },
                title = { Text("Preselected Databases") },
                text = {
                    Column {
                        Text("Select a database from rumca-js repository list:")
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isLoadingPresets) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (presetUrls.isEmpty()) {
                            Text("Failed to load preselected list. Please check network connection.")
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedPresetUrl.ifEmpty { "Select a database..." },
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    presetUrls.forEach { pUrl ->
                                        DropdownMenuItem(
                                            text = { Text(pUrl) },
                                            onClick = {
                                                selectedPresetUrl = pUrl
                                                urlInput = pUrl
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (verificationError != null) {
                            Text(
                                text = verificationError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
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
                                handleSaveDatabase(urlInput, null, null)
                                if (verificationError == null) showAddDialogMode = null
                            }
                        },
                        enabled = urlInput.isNotBlank() && !isVerifying
                    ) {
                        Text("Add Database")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialogMode = null }, enabled = !isVerifying) {
                        Text("Cancel")
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
    onItemClick: ((String, DatabaseState) -> Unit)? = null,
    onEdit: (String) -> Unit,
    onDelete: (String, DatabaseState) -> Unit,
    onUpdate: (String, DatabaseState) -> Unit
) {
    databases.forEach { (url, state) ->
        DatabaseItem(
            state = state,
            onItemClick = { onItemClick?.invoke(url, state) },
            onEdit = { onEdit(url) },
            onDelete = { onDelete(url, state) },
            onUpdate = { onUpdate(url, state) }
        )
    }
}



@Composable
fun DatabaseItem(
    state: DatabaseState,
    onItemClick: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit
) {
    val isLocal = state.isLocal
    val displayName = state.displayName

    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable {
                    if (onItemClick != null) {
                        onItemClick()
                    } else {
                        isExpanded = !isExpanded
                    }
                }
                .padding(vertical = 4.dp)
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

            // 3. Smoothly animate showing the URL when tapped
            AnimatedVisibility(visible = isExpanded) {
                if (state.url.isNotBlank()) {
                    Text(
                        text = state.url,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
