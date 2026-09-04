package io.github.rumcajs.offlinewebsearch.ui.components

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ──────────────────────────────────────────────────────────────────────────────
// Data model
// ──────────────────────────────────────────────────────────────────────────────

/** Visual rendering type for a [PropertyItem]. */
enum class PropertyType {
    /** Plain text value — rendered with the default surface colour. */
    TEXT,

    /**
     * Hyperlink value — rendered in [MaterialTheme.colorScheme.primary] with an underline.
     * Tap opens the URL; long-press copies it to the clipboard.
     */
    LINK
}

/**
 * A single labelled property to be displayed by [PropertiesPane].
 *
 * @param label         The property name shown on the left side of the row.
 * @param value         The property value shown on the right side.
 * @param type          Controls how [value] is rendered (plain [PropertyType.TEXT] or
 *                      clickable [PropertyType.LINK]).  Defaults to [PropertyType.TEXT].
 * @param toastMessage  Toast text shown after a long-press copy action.
 *                      Only used when [type] is [PropertyType.LINK].
 * @param isRestricted  When `true` the value is replaced with "xXx" and link
 *                      interaction is disabled.
 */
data class PropertyItem(
    val label: String,
    val value: String,
    val type: PropertyType = PropertyType.TEXT,
    val toastMessage: String = "Link copied",
    val isRestricted: Boolean = false
)

// ──────────────────────────────────────────────────────────────────────────────
// Composables
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Renders a vertical list of labelled property rows from [properties].
 *
 * Each [PropertyItem] is rendered as a [PropertyRow] whose value style depends on
 * [PropertyItem.type]:
 * - [PropertyType.TEXT] — plain text.
 * - [PropertyType.LINK] — styled as a hyperlink; tap opens the URL, long-press copies it.
 *
 * Items whose [PropertyItem.value] is blank are silently skipped.
 *
 * @param properties  Ordered list of properties to display.
 * @param modifier    Optional [Modifier] applied to the enclosing [Column].
 */
@Composable
fun PropertiesPane(
    properties: List<PropertyItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        properties.forEach { item ->
            if (item.value.isNotBlank()) {
                PropertyRow(item = item)
            }
        }
    }
}

/**
 * Renders a single labelled property row.
 *
 * The label is always rendered in [MaterialTheme.colorScheme.secondary] with
 * [FontWeight.SemiBold].  The value style is determined by [PropertyItem.type].
 */
@Composable
fun PropertyRow(item: PropertyItem) {
    when (item.type) {
        PropertyType.TEXT -> TextPropertyRow(label = item.label, value = item.value)
        PropertyType.LINK -> LinkPropertyRow(
            label = item.label,
            url = item.value,
            toastMessage = item.toastMessage,
            isRestricted = item.isRestricted
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Internal row variants
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A plain-text property row.  Equivalent to the former `DetailRow`.
 */
@Composable
private fun TextPropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(text = value)
    }
}

/**
 * A hyperlink property row.  Equivalent to the former `LinkRow`.
 *
 * - **Tap** opens [url] in the system browser (unless [isRestricted]).
 * - **Long-press** copies [url] to the clipboard and shows [toastMessage].
 */
@Composable
private fun LinkPropertyRow(
    label: String,
    url: String,
    toastMessage: String,
    isRestricted: Boolean
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val displayUrl = if (isRestricted) "xXx" else url

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(url, isRestricted) {
                if (!isRestricted) {
                    detectTapGestures(
                        onTap = { uriHandler.openUri(url) },
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(url))
                            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = displayUrl,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 16.dp)
        )
    }
}
