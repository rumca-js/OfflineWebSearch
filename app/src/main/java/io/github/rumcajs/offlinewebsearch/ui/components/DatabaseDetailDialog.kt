package io.github.rumcajs.offlinewebsearch.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import java.io.File
import java.text.DecimalFormat

@Composable
fun DatabaseDetailDialog(
    state: DatabaseState,
    dbConfig: DatabaseConfiguration,
    isActive: Boolean,
    onDismissRequest: () -> Unit,
    onSetActive: (() -> Unit)? = null,
    onUpdate: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(
                    text = state.displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isActive) {
                    Text(
                        text = "Currently Active Database",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Database State",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                DatabasePropertyRow(label = "Display Name", value = state.displayName)
                DatabasePropertyRow(
                    label = "URL / Source",
                    value = if (state.url.isBlank()) "Assets (Bundled)" else state.url
                )
                DatabasePropertyRow(
                    label = "Local File",
                    value = if (state.localFileName.isBlank()) "places_0.json (Assets)" else state.localFileName
                )
                DatabasePropertyRow(label = "Status", value = state.status.name, isHighlight = true)
                DatabasePropertyRow(
                    label = "Progress",
                    value = "${(state.progress * 100).toInt()}%"
                )
                DatabasePropertyRow(
                    label = "Access Mode",
                    value = if (state.isReadOnly) "READ-ONLY" else "READ-WRITE"
                )
                DatabasePropertyRow(label = "Extension", value = state.extension)
                DatabasePropertyRow(
                    label = "Is Local File",
                    value = if (state.isLocal) "Yes" else if (state.url.isBlank()) "Asset" else "No"
                )
                DatabasePropertyRow(
                    label = "File Size",
                    value = formatFileSize(context, state)
                )
                DatabasePropertyRow(
                    label = "Error Message",
                    value = state.errorMessage ?: "None"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Database Configuration",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                DatabasePropertyRow(
                    label = "Direct Links",
                    value = if (dbConfig.directLinks) "Enabled" else "Disabled"
                )
                DatabasePropertyRow(
                    label = "Show Icons",
                    value = if (dbConfig.showIcons) "Enabled" else "Disabled"
                )
                DatabasePropertyRow(
                    label = "Video Preview",
                    value = if (dbConfig.videoPreview) "Enabled" else "Disabled"
                )
                DatabasePropertyRow(
                    label = "Order By",
                    value = dbConfig.orderBy.displayName
                )
                DatabasePropertyRow(
                    label = "View Style",
                    value = dbConfig.viewStyle.displayName
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.isLocal && state.url.isNotBlank() && onUpdate != null) {
                    OutlinedButton(onClick = {
                        onUpdate()
                    }) {
                        Text("Update")
                    }
                }
                if (!isActive && onSetActive != null) {
                    Button(onClick = {
                        onSetActive()
                        onDismissRequest()
                    }) {
                        Text("Set as Active")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

private fun formatFileSize(context: Context, state: DatabaseState): String {
    val bytes = if (state.sizeInBytes > 0L) {
        state.sizeInBytes
    } else if (state.localFileName.isNotBlank()) {
        try {
            val file = File(context.filesDir, state.localFileName)
            if (file.exists()) file.length() else 0L
        } catch (e: Exception) {
            0L
        }
    } else {
        0L
    }

    if (bytes <= 0L) return "Unknown"
    val kbs = bytes / 1024.0
    val mbs = kbs / 1024.0
    val dec = DecimalFormat("#.##")
    return when {
        mbs >= 1.0 -> "${dec.format(mbs)} MB"
        kbs >= 1.0 -> "${dec.format(kbs)} KB"
        else -> "$bytes Bytes"
    }
}
