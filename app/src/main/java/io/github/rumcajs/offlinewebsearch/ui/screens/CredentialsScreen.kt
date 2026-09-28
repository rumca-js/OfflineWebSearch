package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Credentials
import io.github.rumcajs.offlinewebsearch.data.repositories.CredentialsRepository
import kotlinx.coroutines.launch

/**
 * Screen for managing credentials stored in the `credentials` table.
 *
 * Lists all credentials for the active database and supports adding, editing,
 * and deleting credential records. The screen is read-only when the active
 * database is not writable.
 *
 * @param onBack Callback invoked when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()
    val isWritable = config.activeDatabaseState?.isSQLite == true &&
            config.activeDatabaseState?.isReadOnly != true

    var credentials by remember { mutableStateOf<List<Credentials>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCredential by remember { mutableStateOf<Credentials?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCredential by remember { mutableStateOf<Credentials?>(null) }

    /** Reload credentials list from the database. */
    fun loadCredentials() {
        scope.launch {
            isLoading = true
            credentials = CredentialsRepository.getAllCredentials(context, config.activeDatabaseState)
            isLoading = false
        }
    }

    LaunchedEffect(config.activeDatabaseState) {
        loadCredentials()
    }

    if (showAddDialog) {
        CredentialFormDialog(
            initialCredential = null,
            onDismiss = { showAddDialog = false },
            onSave = { newCredential ->
                scope.launch {
                    val (rowId, err) = CredentialsRepository.insertCredential(
                        context, config.activeDatabaseState, newCredential
                    )
                    if (rowId != null) {
                        Toast.makeText(context, "Credential added", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        loadCredentials()
                    } else {
                        Toast.makeText(context, err ?: "Failed to add credential", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (editingCredential != null) {
        CredentialFormDialog(
            initialCredential = editingCredential,
            onDismiss = { editingCredential = null },
            onSave = { updated ->
                scope.launch {
                    val (success, err) = CredentialsRepository.updateCredential(
                        context, config.activeDatabaseState, updated
                    )
                    if (success) {
                        Toast.makeText(context, "Credential updated", Toast.LENGTH_SHORT).show()
                        editingCredential = null
                        loadCredentials()
                    } else {
                        Toast.makeText(context, err ?: "Failed to update credential", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (selectedCredential != null) {
        CredentialDetailDialog(
            credential = selectedCredential!!,
            isWritable = isWritable,
            onDismiss = { selectedCredential = null },
            onEdit = { cred ->
                selectedCredential = null
                editingCredential = cred
            },
            onDelete = { cred ->
                cred.id?.let { credId ->
                    scope.launch {
                        val (success, err) = CredentialsRepository.deleteById(
                            context, config.activeDatabaseState, credId
                        )
                        if (success) {
                            Toast.makeText(context, "Credential deleted", Toast.LENGTH_SHORT).show()
                            selectedCredential = null
                            loadCredentials()
                        } else {
                            Toast.makeText(context, err ?: "Failed to delete credential", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credentials") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isWritable) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Credential")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isWritable) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Credential")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                credentials.isEmpty() -> {
                    Text(
                        text = if (isWritable)
                            "No credentials defined. Tap + to add one."
                        else
                            "No credentials defined.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(credentials, key = { it.id ?: it.hashCode() }) { credential ->
                            CredentialCard(
                                credential = credential,
                                isWritable = isWritable,
                                onEdit = { editingCredential = credential },
                                onClick = { selectedCredential = credential }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card composable representing a single [Credentials] record.
 *
 * Displays the credential name and username. Password is intentionally
 * not shown in the list view.
 *
 * @param credential The credential to display.
 * @param isWritable True when the active database is writable.
 * @param onEdit Callback when the user taps the edit button.
 * @param onClick Callback when the user taps the card to view details.
 */
@Composable
private fun CredentialCard(
    credential: Credentials,
    isWritable: Boolean,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = credential.name.ifEmpty { "Unnamed Credential" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isWritable) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit Credential",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!credential.username.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                CredentialInfoRow(label = "Username", value = credential.username)
            }
        }
    }
}

/**
 * Helper composable for displaying a label/value pair inline.
 *
 * @param label The field label.
 * @param value The field value.
 */
@Composable
private fun CredentialInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dialog for adding or editing a [Credentials] record.
 *
 * Only exposes the fields relevant to users: name, username, and password.
 * Other fields (credential_type, secret, token) are preserved as-is on edit
 * and left null on insert.
 *
 * @param initialCredential Credential to pre-populate when editing, or null when adding.
 * @param onDismiss Callback to dismiss the dialog without saving.
 * @param onSave Callback with the new or updated credential to persist.
 */
@Composable
private fun CredentialFormDialog(
    initialCredential: Credentials?,
    onDismiss: () -> Unit,
    onSave: (Credentials) -> Unit
) {
    val isEditing = initialCredential != null

    var name by remember { mutableStateOf(initialCredential?.name ?: "") }
    var username by remember { mutableStateOf(initialCredential?.username ?: "") }
    var password by remember { mutableStateOf(initialCredential?.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Credential" else "Add Credential") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    placeholder = { Text("e.g. github, reddit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) return@Button
                    val credentialToSave = (initialCredential ?: Credentials()).copy(
                        name = trimmedName,
                        username = username.trim().ifEmpty { null },
                        password = password.trim().ifEmpty { null }
                    )
                    onSave(credentialToSave)
                }
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog displaying full details for a selected [Credentials] record.
 *
 * Shows name, username, and whether a password is set. Provides Edit and
 * Delete actions when the database is writable.
 *
 * @param credential The credential to show.
 * @param isWritable True when the active database is writable.
 * @param onDismiss Callback to dismiss the dialog.
 * @param onEdit Callback when the user decides to edit the credential.
 * @param onDelete Callback when the user confirms deletion.
 */
@Composable
private fun CredentialDetailDialog(
    credential: Credentials,
    isWritable: Boolean,
    onDismiss: () -> Unit,
    onEdit: (Credentials) -> Unit,
    onDelete: (Credentials) -> Unit
) {
    val scrollState = rememberScrollState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Credential") },
            text = {
                Text("Are you sure you want to delete credential '${credential.name.ifEmpty { "Unnamed" }}'?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(credential)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = credential.name.ifEmpty { "Credential Details" },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CredentialDetailRow("ID", credential.id?.toString() ?: "—")
                CredentialDetailRow("Name", credential.name.ifEmpty { "—" })
                CredentialDetailRow("Username", credential.username ?: "—")
                CredentialDetailRow("Password", if (!credential.password.isNullOrEmpty()) "••••••••" else "—")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isWritable && credential.id != null) {
                    TextButton(onClick = { onEdit(credential) }) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

/**
 * Helper row for the credential detail dialog showing a label and its value.
 *
 * @param label Field label text.
 * @param value Field value text.
 */
@Composable
private fun CredentialDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
