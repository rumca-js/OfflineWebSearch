package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration

/**
 * Component pane displaying database configuration settings.
 */
@Composable
fun DatabaseConfigurationPane(
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
        DatabasePropertyRow(
            label = "Track User Searches",
            value = if (dbConfig.trackUserSearches) "Enabled" else "Disabled"
        )
        DatabasePropertyRow(
            label = "Track User Navigation",
            value = if (dbConfig.trackUserNavigation) "Enabled" else "Disabled"
        )
    }
}
