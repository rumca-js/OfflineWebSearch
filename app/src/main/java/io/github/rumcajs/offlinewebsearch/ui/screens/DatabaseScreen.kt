package io.github.rumcajs.offlinewebsearch.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.ui.components.DatabasePropertyRow
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    url: String?,
    state: DatabaseState,
    dbConfig: DatabaseConfiguration,
    isActive: Boolean,
    onBack: () -> Unit,
    onSetActive: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isLocal && url != null) {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    AppConfigManager.saveDatabaseFromInternet(context, url, oldUrl = url)
                                    Toast.makeText(context, "Database updated", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to update database", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Database")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            if (isActive) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Currently Active Database",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = "Database State",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val isSql = state.extension == ".db"
            var rowCountStr: String? by remember { mutableStateOf(null) }

            LaunchedEffect(state) {
                if (isSql) {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val file = File(context.filesDir, state.localFileName)
                            if (file.exists()) {
                                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                                    file.absolutePath,
                                    null,
                                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                                )
                                db.use {
                                    val cursor = it.rawQuery("SELECT COUNT(*) FROM linkdatamodel", null)
                                    cursor.use { c ->
                                        if (c.moveToFirst()) {
                                            rowCountStr = c.getLong(0).toString()
                                        }
                                    }
                                }
                            } else {
                                rowCountStr = "File not found"
                            }
                        }
                    } catch (e: Exception) {
                        rowCountStr = "Error (${e.message})"
                    }
                }
            }

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
                DatabasePropertyRow(
                    label = "linkdatamodel Count",
                    value = rowCountStr ?: "Loading..."
                )
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Database Configuration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

            if (!isActive) {
                Button(
                    onClick = onSetActive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set as Active Database")
                }
            }
        }
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
