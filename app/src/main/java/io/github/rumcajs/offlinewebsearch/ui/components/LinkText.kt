package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A [Text] styled as a hyperlink: uses [MaterialTheme.colorScheme.primary] colour and
 * a configurable [fontSize] (default 14 sp). Suitable for displaying URLs and other
 * link-like values throughout the app.
 *
 * @param text      The text to display.
 * @param modifier  Optional [Modifier].
 * @param fontSize  Font size for the text. Defaults to 14 sp.
 * @param maxLines  Maximum number of lines before truncation. Defaults to [Int.MAX_VALUE].
 * @param overflow  How visual overflow is handled. Defaults to [TextOverflow.Clip].
 */
@Composable
fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        fontSize = fontSize,
        color = MaterialTheme.colorScheme.primary,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}
