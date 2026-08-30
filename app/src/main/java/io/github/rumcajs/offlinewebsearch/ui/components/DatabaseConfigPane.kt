package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.OrderBy
import io.github.rumcajs.offlinewebsearch.data.ViewStyle

/**
 * Component pane providing editable controls for a database's [DatabaseConfiguration].
 *
 * @param url The URL / key of the database whose configuration is being modified (or null for default).
 * @param dbConfig The current [DatabaseConfiguration] for this database.
 * @param modifier Optional modifier for the container Column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseConfigPane(
    url: String?,
    dbConfig: DatabaseConfiguration,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Database Configuration",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DatabaseConfigOptionItem(
            label = "Direct links",
            checked = dbConfig.directLinks,
            onCheckedChange = { checked ->
                AppConfigManager.setDatabaseConfig(url) { it.copy(directLinks = checked) }
            }
        )

        DatabaseConfigOptionItem(
            label = "Show icons",
            checked = dbConfig.showIcons,
            onCheckedChange = { checked ->
                AppConfigManager.setDatabaseConfig(url) { it.copy(showIcons = checked) }
            }
        )

        DatabaseConfigOptionItem(
            label = "Video preview",
            checked = dbConfig.videoPreview,
            onCheckedChange = { checked ->
                AppConfigManager.setDatabaseConfig(url) { it.copy(videoPreview = checked) }
            }
        )

        DatabaseConfigOptionItem(
            label = "Track user searches",
            checked = dbConfig.trackUserSearches,
            onCheckedChange = { checked ->
                AppConfigManager.setDatabaseConfig(url) { it.copy(trackUserSearches = checked) }
            }
        )

        DatabaseConfigOptionItem(
            label = "Track user navigation",
            checked = dbConfig.trackUserNavigation,
            onCheckedChange = { checked ->
                AppConfigManager.setDatabaseConfig(url) { it.copy(trackUserNavigation = checked) }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dbConfig.linksPerPage.toString(),
            onValueChange = { input ->
                val newCount = input.toIntOrNull() ?: DatabaseConfiguration.MIN_LINKS_PER_PAGE
                val validCount = kotlin.math.max(DatabaseConfiguration.MIN_LINKS_PER_PAGE, newCount)
                AppConfigManager.setDatabaseConfig(url) { it.copy(linksPerPage = validCount) }
            },
            label = { Text("Links Per Page (min ${DatabaseConfiguration.MIN_LINKS_PER_PAGE})") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Order By", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        var orderByExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = orderByExpanded,
            onExpandedChange = { orderByExpanded = !orderByExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = dbConfig.orderBy.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = orderByExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = orderByExpanded,
                onDismissRequest = { orderByExpanded = false }
            ) {
                OrderBy.values().forEach { orderByOption ->
                    DropdownMenuItem(
                        text = { Text(orderByOption.displayName) },
                        onClick = {
                            AppConfigManager.setDatabaseConfig(url) { it.copy(orderBy = orderByOption) }
                            orderByExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "View Style", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        var viewStyleExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = viewStyleExpanded,
            onExpandedChange = { viewStyleExpanded = !viewStyleExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = dbConfig.viewStyle.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewStyleExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = viewStyleExpanded,
                onDismissRequest = { viewStyleExpanded = false }
            ) {
                ViewStyle.values().forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.displayName) },
                        onClick = {
                            AppConfigManager.setDatabaseConfig(url) { it.copy(viewStyle = style) }
                            viewStyleExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseConfigOptionItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = checked,
                onClick = { onCheckedChange(!checked) },
                role = Role.Checkbox
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
