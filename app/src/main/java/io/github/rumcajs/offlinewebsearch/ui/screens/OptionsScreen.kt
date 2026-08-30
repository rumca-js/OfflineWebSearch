package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.SourceRefreshState
import io.github.rumcajs.offlinewebsearch.ui.components.DatabasesContainer
import io.github.rumcajs.offlinewebsearch.ui.components.ReadOnlyBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    onNavigateToDatabases: () -> Unit = {},
    onNavigateToDatabaseDetail: (String?, DatabaseState) -> Unit = { _, _ -> },
    onNavigateToAbout: () -> Unit = {}
) {
    val config by io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState()
    val sourceRefreshProgress by SourceRefreshState.progress.collectAsState()
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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Active Database",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        var activeDbExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = activeDbExpanded,
            onExpandedChange = { activeDbExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = config.activeDatabaseDisplayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = activeDbExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = activeDbExpanded,
                onDismissRequest = { activeDbExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Default (Assets)")
                            ReadOnlyBadge(isReadOnly = true)
                        }
                    },
                    onClick = {
                        AppConfigManager.setActiveDatabase(null)
                        activeDbExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )

                config.databases.forEach { (url, state) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.displayName)
                                ReadOnlyBadge(isReadOnly = state.isReadOnly)
                            }
                        },
                        onClick = {
                            AppConfigManager.setActiveDatabase(url)
                            activeDbExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        // ── Source refresh progress bar ───────────────────────────────────────
        sourceRefreshProgress?.let { progress ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Refreshing sources: ${progress.done} / ${progress.total}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        DatabasesContainer(
            onNavigateToDatabaseDetail = onNavigateToDatabaseDetail
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateToAbout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("About")
        }
    }
}

