package io.github.rumcajs.offlinewebsearch.ui.components

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared title component for entry details and page previews.
 *
 * Renders bold 24sp title text with 30sp line height. When [link] is provided, the title
 * is styled with primary color and underline; tapping opens [link] in the browser and
 * long-pressing copies [link] to the clipboard.
 *
 * @param title The title text to display.
 * @param modifier Optional modifier applied to the title container row.
 * @param link Optional target URL. If present, activates hyperlink styling and click gestures.
 * @param isRestricted When true, disables long-press link copying.
 * @param trailingContent Optional trailing composable slot (e.g. vote or rating badge).
 */
@Composable
fun DetailTitle(
    title: String,
    modifier: Modifier = Modifier,
    link: String? = null,
    isRestricted: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp,
            color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (link != null) TextDecoration.Underline else TextDecoration.None,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
                .pointerInput(link, isRestricted) {
                    if (link != null) {
                        detectTapGestures(
                            onTap = {
                                uriHandler.openUri(link)
                            },
                            onLongPress = {
                                if (!isRestricted) {
                                    clipboardManager.setText(AnnotatedString(link))
                                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
        )
        trailingContent?.invoke()
    }
}
