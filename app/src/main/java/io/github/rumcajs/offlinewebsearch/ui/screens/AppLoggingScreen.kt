package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLogging
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository
import kotlinx.coroutines.launch

/**
 * Screen that displays the application log entries stored in the `applogging` table.
 *
 * Entries are loaded from the active database and presented newest-first.
 * Each row shows the log level badge, timestamp, summary text, and optional detail text.
 * Tapping a row opens a detail dialog displaying all log fields.
 *
 * @param onBack Callback invoked when the user taps the back navigation icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLoggingScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf<List<AppLogging>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedLog by remember { mutableStateOf<AppLogging?>(null) }

    // Load logs whenever the active database changes.
    LaunchedEffect(config.activeDatabaseState) {
        isLoading = true
        logs = AppLoggingRepository.getLogs(context, config.activeDatabaseState)
        isLoading = false
    }

    if (selectedLog != null) {
        LogDetailDialog(
            log = selectedLog!!,
            onDismiss = { selectedLog = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                AppLoggingRepository.clear(context, config.activeDatabaseState)
                                logs = emptyList()
                            }
                        }
                    ) {
                        Text("Clear")
                    }
                }
            )
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
                logs.isEmpty() -> {
                    Text(
                        text = "No log entries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { log ->
                            LogEntryCard(
                                log = log,
                                onClick = { selectedLog = log }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card composable that renders a single [AppLogging] entry.
 *
 * @param log The log entry to display.
 * @param onClick Callback when the card is clicked.
 */
@Composable
private fun LogEntryCard(
    log: AppLogging,
    onClick: () -> Unit
) {
    val levelColor = when (log.level) {
        AppLoggingRepository.LEVEL_ERROR -> MaterialTheme.colorScheme.errorContainer
        AppLoggingRepository.LEVEL_WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val levelLabel = when (log.level) {
        AppLoggingRepository.LEVEL_ERROR -> "ERROR"
        AppLoggingRepository.LEVEL_WARNING -> "WARN"
        else -> "INFO"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = levelColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = levelLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    log.id?.let { id ->
                        Text(
                            text = "#$id",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                log.date?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.info_text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Dialog displaying full information for a selected [AppLogging] entry.
 *
 * @param log The log entry whose details are shown.
 * @param onDismiss Callback to dismiss the dialog.
 */
@Composable
private fun LogDetailDialog(
    log: AppLogging,
    onDismiss: () -> Unit
) {
    val levelLabel = when (log.level) {
        AppLoggingRepository.LEVEL_ERROR -> "ERROR"
        AppLoggingRepository.LEVEL_WARNING -> "WARN"
        else -> "INFO"
    }

    val levelColor = when (log.level) {
        AppLoggingRepository.LEVEL_ERROR -> MaterialTheme.colorScheme.error
        AppLoggingRepository.LEVEL_WARNING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "[$levelLabel]",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                    log.date?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.info_text.ifBlank { "Log Entry #${log.id ?: ""}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ID: ",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = log.id?.toString() ?: "N/A",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Level: ",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "$levelLabel (${log.level})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Date: ",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = log.date ?: "N/A",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Description / Details:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = log.detail_text?.takeIf { it.isNotBlank() } ?: "None",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (log.detail_text.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
