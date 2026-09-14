package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/**
 * Shared publication date component for entry details and page previews.
 *
 * Renders "Published: <date>" in 12sp onSurfaceVariant color.
 *
 * @param date Formatted date string to display.
 * @param modifier Optional modifier.
 * @param isRestricted When true, displays "Published: xXx".
 */
@Composable
fun DetailPublishedDate(
    date: String,
    modifier: Modifier = Modifier,
    isRestricted: Boolean = false
) {
    Text(
        text = "Published: ${if (isRestricted) "xXx" else date}",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
