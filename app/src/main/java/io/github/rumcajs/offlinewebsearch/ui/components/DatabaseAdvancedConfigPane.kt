package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import java.util.Locale

/**
 * Component pane providing editable controls for advanced database configuration,
 * including visual alpha settings (visited and dead entries alpha).
 *
 * @param url The URL / key of the database whose configuration is being modified (or null for default).
 * @param dbConfig The current [DatabaseConfiguration] for this database.
 * @param modifier Optional modifier for the container Column.
 */
@Composable
fun DatabaseAdvancedConfigPane(
    url: String?,
    dbConfig: DatabaseConfiguration,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Advanced Configuration",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Visited Entries Alpha
        val visitPercent = (dbConfig.entriesVisitAlpha * 100).toInt()
        Text(
            text = "Visited Entries Alpha: $visitPercent% (${String.format(Locale.US, "%.2f", dbConfig.entriesVisitAlpha)})",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = dbConfig.entriesVisitAlpha,
            onValueChange = { newAlpha ->
                AppConfigManager.setDatabaseConfig(url) { it.copy(entriesVisitAlpha = newAlpha.coerceIn(0f, 1f)) }
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Dead Entries Alpha
        val deadPercent = (dbConfig.entriesDeadAlpha * 100).toInt()
        Text(
            text = "Dead Entries Alpha: $deadPercent% (${String.format(Locale.US, "%.2f", dbConfig.entriesDeadAlpha)})",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = dbConfig.entriesDeadAlpha,
            onValueChange = { newAlpha ->
                AppConfigManager.setDatabaseConfig(url) { it.copy(entriesDeadAlpha = newAlpha.coerceIn(0f, 1f)) }
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
