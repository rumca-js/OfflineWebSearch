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

    // Load logs whenever the active database changes.
    LaunchedEffect(config.activeDatabaseState) {
        isLoading = true
        logs = AppLoggingRepository.getLogs(context, config.activeDatabaseState)
        isLoading = false
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
                            LogEntryCard(log)
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
 */
@Composable
private fun LogEntryCard(log: AppLogging) {
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = levelColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = levelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
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
            log.detail_text?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
