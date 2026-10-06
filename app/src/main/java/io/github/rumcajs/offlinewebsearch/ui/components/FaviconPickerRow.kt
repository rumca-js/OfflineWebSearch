package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceIcons

/**
 * Row that shows the currently chosen predefined icon (if any) and a button
 * to open the [SourceIconPickerDialog].
 *
 * The picker button is shown only when [favicon] is blank (i.e. no remote URL
 * has already been stored) or when [favicon] contains a predefined icon value.
 * A clear button lets the user remove the chosen icon.
 *
 * @param favicon Current favicon string value.
 * @param isEditable Whether editing is enabled.
 * @param onPickIconClick Callback to open the icon picker dialog.
 * @param onClearClick Callback to clear the selected icon.
 * @param modifier Layout modifier.
 */
@Composable
fun FaviconPickerRow(
    favicon: String,
    isEditable: Boolean,
    onPickIconClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFaviconEmpty = favicon.isBlank()
    val predefinedIcon = if (!isFaviconEmpty) {
        SourceIcons.all.firstOrNull { it.value == favicon }
    } else null

    // Show the picker row only when editable and favicon is empty OR it's a predefined icon.
    // If favicon holds a remote URL (not in our list) we leave it untouched.
    val showRow = isEditable && (isFaviconEmpty || predefinedIcon != null)
    if (!showRow) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (predefinedIcon != null) {
            // Preview the chosen icon; clicking it reopens the picker dialog
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onPickIconClick)
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                MaterialSymbolIcon(
                    name = predefinedIcon.value,
                    contentDescription = predefinedIcon.label,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = predefinedIcon.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(onClick = onClearClick) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear icon",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // No icon chosen yet — offer picker
            OutlinedButton(
                onClick = onPickIconClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose predefined icon")
            }
        }
    }
}
