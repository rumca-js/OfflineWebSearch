package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable form pane for editing/adding a Source.
 * Shared between SourceEditScreen and SourceUrlEditPreviewScreen.
 */
@Composable
fun SourceFormPane(
    title: String,
    onTitleChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    isEditable: Boolean,
    urlError: String? = null,
    modifier: Modifier = Modifier
) {
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

        OutlinedTextField(
            value = url,
            onValueChange = { if (isEditable) onUrlChange(it) },
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
