package io.github.rumcajs.offlinewebsearch.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatsRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.RepositoryInterface
import io.github.rumcajs.offlinewebsearch.data.repositories.RepositoryList
import java.io.File
import java.text.DecimalFormat

/**
 * Component pane displaying database state properties and row counts for SQLite databases.
 */
@Composable
fun DatabaseStatePane(
    state: DatabaseState,
    refreshTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSql = state.isSQLite
    var repoCounts by remember(state, refreshTrigger) { mutableStateOf<Map<RepositoryInterface, Long?>>(emptyMap()) }
    var isLoadingStats by remember(state, refreshTrigger) { mutableStateOf(false) }

    LaunchedEffect(state, refreshTrigger) {
        if (isSql) {
            isLoadingStats = true
            repoCounts = DatabaseStatsRepository.getRepositoryCounts(context, state)
            isLoadingStats = false
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Database State",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DatabasePropertyRow(label = "Display Name", value = state.displayName)
        DatabasePropertyRow(
            label = "URL / Source",
            value = if (state.url.isBlank()) "Assets (Bundled)" else state.url
        )
        DatabasePropertyRow(
            label = "Local File",
            value = if (state.localFileName.isBlank()) "places_0.json (Assets)" else state.localFileName
        )
        if (isSql) {
            for (repo in RepositoryList.repositories) {
                val count = repoCounts[repo]
                DatabasePropertyRow(
                    label = "${repo.getTableName()} Count",
                    value = if (isLoadingStats) "Loading..." else count?.toString() ?: "N/A"
                )
            }
        }
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
        DatabasePropertyRow(
            label = "Date Created",
            value = state.dateCreated ?: "Unknown"
        )
        DatabasePropertyRow(
            label = "Date Last Refresh",
            value = state.dateLastRefresh ?: "Never"
        )
    }
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
