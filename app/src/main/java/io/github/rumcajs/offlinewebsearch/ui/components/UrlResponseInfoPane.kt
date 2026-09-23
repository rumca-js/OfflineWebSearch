package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject
import java.util.Locale

/**
 * Formats a byte count into a human-readable representation (e.g., "1.23 MB").
 *
 * @param bytes The number of bytes to format.
 * @return Formatted string with appropriate size unit.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

/**
 * Component that displays HTTP response information (status code, content length, content type, feed entries, error details).
 *
 * @param pageResponse The HTTP response object containing status code, headers, and metadata.
 * @param modifier Modifier for container layout and sizing.
 * @param title Title text for the response info card (defaults to "Response Info").
 * @param isLoading Whether the response is currently loading (displays progress indicator in header).
 * @param entriesCount Optional count of feed entries found (for RSS/feed responses).
 * @param extraContent Optional trailing content to render inside the card column.
 */
@Composable
fun UrlResponseInfoPane(
    pageResponse: PageResponseObject?,
    modifier: Modifier = Modifier,
    title: String = "Response Info",
    isLoading: Boolean = false,
    entriesCount: Int? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (pageResponse == null && !isLoading) return

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (pageResponse != null) 16.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (pageResponse != null) {
                val statusWithText = NetworkUtils.statusCodeToText(pageResponse.statusCode)

                val (statusColor, statusText) = when {
                    pageResponse.isValid -> {
                        androidx.compose.ui.graphics.Color(0xFF2E7D32) to "Success $statusWithText"
                    }
                    pageResponse.isInvalid -> {
                        MaterialTheme.colorScheme.error to "Error $statusWithText"
                    }
                    else -> {
                        MaterialTheme.colorScheme.error to "Unknown $statusWithText"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status Code",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text(statusText) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            labelColor = statusColor
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Content Type",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = pageResponse.contentType ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                pageResponse.length?.let { len ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Response Length",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val lengthDisplay = "$len bytes (${formatBytes(len)})"
                        Text(
                            text = lengthDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                entriesCount?.let { count ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Feed Entries",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "$count entries found",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (pageResponse.error != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Error Details",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = pageResponse.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            extraContent?.invoke(this)
        }
    }
}

/**
 * Composable alias for [UrlResponseInfoPane].
 */
@Composable
fun ResponseInfoPane(
    pageResponse: PageResponseObject?,
    modifier: Modifier = Modifier,
    title: String = "Response Info",
    isLoading: Boolean = false,
    entriesCount: Int? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    UrlResponseInfoPane(
        pageResponse = pageResponse,
        modifier = modifier,
        title = title,
        isLoading = isLoading,
        entriesCount = entriesCount,
        extraContent = extraContent
    )
}
