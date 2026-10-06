package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceIcons

/**
 * Reusable form pane for editing/adding a Source.
 * Shared between SourceEditScreen and SourceUrlEditPreviewScreen.
 *
 * @param favicon         Current favicon value stored in [Source.favicon].
 * @param onFaviconChange Callback invoked when the favicon value changes.
 */
@Composable
fun SourceFormPane(
    title: String,
    onTitleChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    age: String = "0",
    onAgeChange: (String) -> Unit = {},
    autoTag: String = "",
    onAutoTagChange: (String) -> Unit = {},
    language: String = "",
    onLanguageChange: (String) -> Unit = {},
    favicon: String = "",
    onFaviconChange: (String) -> Unit = {},
    isEditable: Boolean,
    urlError: String? = null,
    modifier: Modifier = Modifier
) {
    var showIconPicker by remember { mutableStateOf(false) }

    if (showIconPicker) {
        SourceIconPickerDialog(
            selectedValue = favicon,
            onIconSelected = { chosen ->
                onFaviconChange(chosen)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    Column(modifier = modifier) {
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
            onValueChange = { if (isEditable) onTitleChange(it) },
            label = { Text("Title") },
            enabled = isEditable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        UrlInputPane(
            url = url,
            onUrlChange = { if (isEditable) onUrlChange(it) },
            label = "URL",
            enabled = isEditable,
            urlError = urlError,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = age,
            onValueChange = { input ->
                if (isEditable && (input.isEmpty() || input.all { it.isDigit() })) {
                    onAgeChange(input)
                }
            },
            label = { Text("Age") },
            placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = isEditable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = autoTag,
            onValueChange = { if (isEditable) onAutoTagChange(it) },
            label = { Text("Auto Tag") },
            placeholder = { Text("news, tech, android") },
            supportingText = { Text("Tags separated by comma (e.g. news, tech, android)") },
            enabled = isEditable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = language,
            onValueChange = { if (isEditable) onLanguageChange(it) },
            label = { Text("Language") },
            placeholder = { Text("en") },
            enabled = isEditable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Favicon / predefined icon ────────────────────────────────────────
        // The picker is shown only when favicon is blank (no remote URL set).
        // If the user has a remote URL in favicon they typed it themselves; we
        // leave it alone and do not show the predefined picker over it.
        FaviconPickerRow(
            favicon = favicon,
            isEditable = isEditable,
            onPickIconClick = { showIconPicker = true },
            onClearClick = { onFaviconChange("") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = enabled,
                onCheckedChange = { if (isEditable) onEnabledChange(it) },
                enabled = isEditable
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Enabled",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

