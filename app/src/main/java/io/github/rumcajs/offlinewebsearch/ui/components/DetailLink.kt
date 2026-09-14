package io.github.rumcajs.offlinewebsearch.ui.components

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Shared clickable URL link component for entry details and page previews.
 *
 * Renders 14sp primary-colored text with underline. Single tap opens the URL in the
 * browser; long press copies the URL to the clipboard.
 *
 * @param link The URL string to display and open.
 * @param modifier Optional modifier.
 * @param isRestricted When true, displays "xXx" and disables click/copy gestures.
 */
@Composable
fun DetailLink(
    link: String,
    modifier: Modifier = Modifier,
    isRestricted: Boolean = false
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Text(
        text = if (isRestricted) "xXx" else link,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(link, isRestricted) {
                if (!isRestricted) {
                    detectTapGestures(
                        onTap = { uriHandler.openUri(link) },
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(link))
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
    )
}
