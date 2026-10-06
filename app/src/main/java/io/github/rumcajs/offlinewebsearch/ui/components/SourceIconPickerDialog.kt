package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceIcon
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceIcons

/**
 * Modal dialog that lets the user pick a predefined source icon from [SourceIcons.all].
 *
 * Each icon is rendered as a small tile showing the Material icon glyph above its label.
 * The currently selected [selectedValue] is highlighted with a primary-colour border.
 *
 * @param selectedValue  The [SourceIcon.value] that is currently selected, or blank for none.
 * @param onIconSelected Callback invoked with the chosen [SourceIcon.value] when the user confirms.
 * @param onDismiss      Callback invoked when the dialog is dismissed without selecting.
 */
@Composable
fun SourceIconPickerDialog(
    selectedValue: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember(selectedValue) { mutableStateOf(selectedValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                items(SourceIcons.all) { icon ->
                    SourceIconTile(
                        icon = icon,
                        isSelected = icon.value == current,
                        onClick = { current = icon.value }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIconSelected(current) },
                enabled = current.isNotBlank()
            ) {
                Text("Select")
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
 * Single icon tile inside [SourceIconPickerDialog].
 *
 * Renders the [SourceIcon] as a Material icon glyph above a short label.
 * A primary-colour border is drawn when [isSelected] is true.
 */
@Composable
private fun SourceIconTile(
    icon: SourceIcon,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MaterialSymbolIcon(
                name = icon.value,
                contentDescription = icon.label,
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = icon.label,
                fontSize = 9.sp,
                maxLines = 1,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
