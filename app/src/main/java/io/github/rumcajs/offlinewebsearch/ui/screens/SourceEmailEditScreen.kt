package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Credentials
import io.github.rumcajs.offlinewebsearch.data.repositories.CredentialsRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.ui.components.FaviconPickerRow
import io.github.rumcajs.offlinewebsearch.ui.components.SourceIconPickerDialog
import io.github.rumcajs.offlinewebsearch.util.TagUtils
import kotlinx.coroutines.launch

/**
 * Screen for adding or editing an Email source (IMAP).
 *
 * Prompts the user for:
 * - IMAP server URL / hostname (e.g. `imap.example.com`, `imaps://mail.example.com:993`)
 * - Credentials username
 * - Credentials password
 * - Optional title, enabled status, age, auto-tag, language, and predefined icon.
 *
 * When saved:
 * 1. Persists credentials into the `credentials` table via [CredentialsRepository].
 * 2. Saves the source with [Source.credentials_id] set to the created/updated credential ID
 *    and [Source.source_type] set to [SourceRepository.SOURCE_TYPE_EMAIL].
 *
 * @param source The source to edit, or a blank source for adding.
 * @param onSourceUpdated Callback invoked when the email source is saved/updated successfully.
 * @param onBack Callback to navigate back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceEmailEditScreen(
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

    var url by remember { mutableStateOf(source.url) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var title by remember { mutableStateOf(source.title) }
    var enabled by remember { mutableStateOf(source.enabled) }
    var ageText by remember { mutableStateOf((source.age ?: 0).toString()) }
    var autoTag by remember { mutableStateOf(source.auto_tag) }
    var language by remember { mutableStateOf(source.language) }
    var favicon by remember { mutableStateOf(source.favicon.ifBlank { "email" }) }

    var showIconPicker by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoadingCredentials by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    if (showIconPicker) {
        SourceIconPickerDialog(
            selectedValue = favicon,
            onIconSelected = { chosen ->
                favicon = chosen
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    // Load existing credentials when editing an email source
    LaunchedEffect(source.id, source.credentials_id) {
        if (source.credentials_id != null) {
            isLoadingCredentials = true
            val cred = CredentialsRepository.getCredentialById(context, activeDbState, source.credentials_id)
            if (cred != null) {
                username = cred.username ?: ""
                password = cred.password ?: ""
            }
            isLoadingCredentials = false
        } else if (source.url.isNotBlank()) {
            isLoadingCredentials = true
            val cred = CredentialsRepository.getCredentialByName(context, activeDbState, source.url)
                ?: CredentialsRepository.getCredentialByName(context, activeDbState, source.title)
            if (cred != null) {
                username = cred.username ?: ""
                password = cred.password ?: ""
            }
            isLoadingCredentials = false
        }
    }

    val canSave = url.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isSaving

    suspend fun saveEmailSource(): Boolean {
        val trimmedUrl = url.trim()
        val trimmedUsername = username.trim()
        val trimmedPassword = password

        if (trimmedUrl.isBlank()) {
            urlError = "IMAP server URL cannot be empty"
            return false
        }
        urlError = null

        if (trimmedUsername.isBlank()) {
            usernameError = "Username cannot be empty"
            return false
        }
        usernameError = null

        if (trimmedPassword.isBlank()) {
            passwordError = "Password cannot be empty"
            return false
        }
        passwordError = null

        val parsedAge = ageText.toIntOrNull() ?: 0
        val finalAge = if (parsedAge >= 0) parsedAge else 0
        val effectiveTitle = title.trim().ifBlank {
            if (trimmedUsername.isNotBlank()) trimmedUsername else trimmedUrl
        }

        return if (isAddMode) {
            // 1. Insert credentials
            val hostPart = trimmedUrl.substringAfter("://").substringBefore("/").substringBefore(":")
            val baseCredName = "email_${trimmedUsername}@${hostPart.ifBlank { "server" }}"
            val existingCred = CredentialsRepository.getCredentialByName(context, activeDbState, baseCredName)
            val uniqueCredName = if (existingCred != null) {
                "${baseCredName}_${System.currentTimeMillis()}"
            } else {
                baseCredName
            }

            val newCred = Credentials(
                name = uniqueCredName,
                credential_type = "email",
                username = trimmedUsername,
                password = trimmedPassword,
                user_id = 0L
            )
            val (credId, credErr) = CredentialsRepository.insertCredential(context, activeDbState, newCred)
            if (credId == null) {
                errorMessage = credErr ?: "Failed to save credentials"
                return false
            }

            // 2. Insert source with credentials_id and source_type
            val (srcSuccess, srcErr) = SourceRepository.insertSource(
                context = context,
                activeDatabaseState = activeDbState,
                title = effectiveTitle,
                url = trimmedUrl,
                enabled = enabled,
                age = finalAge,
                auto_tag = TagUtils.normalizeAutoTag(autoTag),
                language = language.trim(),
                credentials_id = credId,
                source_type = SourceRepository.SOURCE_TYPE_EMAIL,
                favicon = favicon
            )
            if (!srcSuccess) {
                // Clean up inserted credential on failure
                CredentialsRepository.deleteById(context, activeDbState, credId)
                errorMessage = srcErr ?: "Failed to save email source"
                return false
            }
            errorMessage = null
            true
        } else {
            // Edit mode: update credentials and source
            var credId = source.credentials_id
            if (credId != null) {
                val existingCred = CredentialsRepository.getCredentialById(context, activeDbState, credId)
                val credToUpdate = (existingCred ?: Credentials(
                    id = credId,
                    name = "email_${trimmedUsername}_${source.id}",
                    credential_type = "email"
                )).copy(
                    username = trimmedUsername,
                    password = trimmedPassword
                )
                val (credSuccess, credErr) = CredentialsRepository.updateCredential(context, activeDbState, credToUpdate)
                if (!credSuccess) {
                    errorMessage = credErr ?: "Failed to update credentials"
                    return false
                }
            } else {
                val hostPart = trimmedUrl.substringAfter("://").substringBefore("/").substringBefore(":")
                val baseCredName = "email_${trimmedUsername}@${hostPart.ifBlank { "server" }}_${source.id}"
                val newCred = Credentials(
                    name = baseCredName,
                    credential_type = "email",
                    username = trimmedUsername,
                    password = trimmedPassword,
                    user_id = 0L
                )
                val (newCredId, credErr) = CredentialsRepository.insertCredential(context, activeDbState, newCred)
                if (newCredId == null) {
                    errorMessage = credErr ?: "Failed to save credentials"
                    return false
                }
                credId = newCredId
            }

            val (srcSuccess, srcErr) = SourceRepository.updateSourceProperties(
                context = context,
                activeDatabaseState = activeDbState,
                id = source.id!!,
                title = effectiveTitle,
                url = trimmedUrl,
                enabled = enabled,
                age = finalAge,
                auto_tag = TagUtils.normalizeAutoTag(autoTag),
                language = language.trim(),
                credentials_id = credId,
                source_type = SourceRepository.SOURCE_TYPE_EMAIL,
                favicon = favicon
            )
            errorMessage = if (!srcSuccess) srcErr else null
            srcSuccess
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAddMode) "Add Email Source" else "Edit Email Source") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditable) {
                        IconButton(
                            onClick = {
                                if (isSaving) return@IconButton
                                isSaving = true
                                coroutineScope.launch {
                                    val success = saveEmailSource()
                                    isSaving = false
                                    if (success) {
                                        val parsedAge = ageText.toIntOrNull() ?: 0
                                        val finalAge = if (parsedAge >= 0) parsedAge else 0
                                        Toast.makeText(
                                            context,
                                            if (isAddMode) "Email source added" else "Email source updated successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        val createdSource = SourceRepository.getSourceByUrl(context, activeDbState, url.trim())
                                            ?: source.copy(
                                                title = title.trim().ifBlank { username.trim() },
                                                url = url.trim(),
                                                enabled = enabled,
                                                age = finalAge,
                                                auto_tag = TagUtils.normalizeAutoTag(autoTag),
                                                language = language.trim(),
                                                source_type = SourceRepository.SOURCE_TYPE_EMAIL,
                                                favicon = favicon
                                            )
                                        onSourceUpdated(createdSource)
                                    } else {
                                        val msg = errorMessage ?: "Failed to save email source"
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = canSave
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isEditable) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Database is read-only. Editing is disabled.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // IMAP Server URL field
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    urlError = null
                },
                label = { Text("IMAP Server / URL *") },
                placeholder = { Text("e.g. imap.example.com or imaps://mail.example.com:993") },
                singleLine = true,
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } },
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth()
            )

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    usernameError = null
                },
                label = { Text("Username / Email *") },
                placeholder = { Text("e.g. user@example.com") },
                singleLine = true,
                isError = usernameError != null,
                supportingText = usernameError?.let { { Text(it) } },
                enabled = isEditable && !isLoadingCredentials,
                modifier = Modifier.fillMaxWidth()
            )

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                },
                label = { Text("Password *") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                enabled = isEditable && !isLoadingCredentials,
                modifier = Modifier.fillMaxWidth()
            )

            // Title field (optional)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("e.g. Personal Email") },
                singleLine = true,
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth()
            )

            // Enabled toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    enabled = isEditable
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Auto Tag field
            OutlinedTextField(
                value = autoTag,
                onValueChange = { autoTag = it },
                label = { Text("Auto Tag (optional)") },
                placeholder = { Text("e.g. email, newsletter") },
                singleLine = true,
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth()
            )

            // Language field
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text("Language (optional)") },
                placeholder = { Text("e.g. en") },
                singleLine = true,
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth()
            )

            // Age field
            OutlinedTextField(
                value = ageText,
                onValueChange = { ageText = it },
                label = { Text("Default Entry Age (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth()
            )

            // Favicon / predefined icon
            FaviconPickerRow(
                favicon = favicon,
                isEditable = isEditable,
                onPickIconClick = { showIconPicker = true },
                onClearClick = { favicon = "" }
            )

            if (isEditable) {
                errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        coroutineScope.launch {
                            val success = saveEmailSource()
                            isSaving = false
                            if (success) {
                                val parsedAge = ageText.toIntOrNull() ?: 0
                                val finalAge = if (parsedAge >= 0) parsedAge else 0
                                Toast.makeText(
                                    context,
                                    if (isAddMode) "Email source added" else "Email source updated successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val createdSource = SourceRepository.getSourceByUrl(context, activeDbState, url.trim())
                                    ?: source.copy(
                                        title = title.trim().ifBlank { username.trim() },
                                        url = url.trim(),
                                        enabled = enabled,
                                        age = finalAge,
                                        auto_tag = TagUtils.normalizeAutoTag(autoTag),
                                        language = language.trim(),
                                        source_type = SourceRepository.SOURCE_TYPE_EMAIL,
                                        favicon = favicon
                                    )
                                onSourceUpdated(createdSource)
                            } else {
                                val msg2 = errorMessage ?: "Failed to save email source"
                                Toast.makeText(context, msg2, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isAddMode) "Add Email Source" else "Save Changes")
                    }
                }
            }
        }
    }
}
