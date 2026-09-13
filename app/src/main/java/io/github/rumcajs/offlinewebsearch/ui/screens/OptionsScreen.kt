package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.ui.components.DatabasesContainer
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker

/**
 * Screen for configuring application settings, database management, and troubleshooting.
 *
 * @param onNavigateToDatabases Callback to navigate to databases list.
 * @param onNavigateToDatabaseDetail Callback to navigate to a database detail screen.
 * @param onNavigateToPreselectedList Callback to navigate to preselected database list screen.
 * @param onNavigateToAbout Callback to navigate to About screen.
 * @param onNavigateToLogs Callback to navigate to Logs screen.
 * @param onNavigateToAdvanced Callback to navigate to Advanced settings screen.
 * @param onNavigateToLinkChecker Callback to navigate to Link Checker screen.
 * @param onSetActive Callback to set an active database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    onNavigateToDatabases: () -> Unit = {},
    onNavigateToDatabaseDetail: (String?, DatabaseState) -> Unit = { _, _ -> },
    onNavigateToPreselectedList: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    onNavigateToAdvanced: () -> Unit = {},
    onNavigateToLinkChecker: () -> Unit = {},
    onSetActive: (String?) -> Unit
) {
    val config by io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState()
    val sourceRefreshProgress by SourceRefreshWorker.progress.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(text = "Options", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "User Age", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = if (config.userAge == 0) "" else config.userAge.toString(),
            onValueChange = {
                val newAge = it.toIntOrNull() ?: 0
                AppConfigManager.setUserAge(newAge)
            },
            label = { Text("Your Age") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            placeholder = { Text("0") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = config.networkConfig.disabled,
                onCheckedChange = { checked ->
                    AppConfigManager.setNetworkDisabled(checked)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Disable Network Communication",
                fontSize = 16.sp
            )
        }

        // ── Source refresh progress bar ───────────────────────────────────────
        if (sourceRefreshProgress.isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            val label = sourceRefreshProgress.currentItem?.let { " ($it)" } ?: ""
            Text(
                text = "Refreshing sources: ${sourceRefreshProgress.done} / ${sourceRefreshProgress.total}$label",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { sourceRefreshProgress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        DatabasesContainer(
            onNavigateToDatabaseDetail = onNavigateToDatabaseDetail,
            onNavigateToPreselectedList = onNavigateToPreselectedList,
            onSetActive = onSetActive
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateToLogs,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logs")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToAdvanced,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Advanced")
        }

        if (!config.networkConfig.disabled) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToLinkChecker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Link checker")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToAbout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("About")
        }
    }
}

