package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation

/**
 * Reusable input pane for editing/entering URLs with automatic normalization checking
 * and suggestion chips for normalized or related URLs.
 *
 * When the input URL differs from its [UrlLocation.normalizeUrl] output, a selectable
 * suggestion chip is presented to allow the user to quickly accept the normalized URL.
 *
 * @param url The current URL text string.
 * @param onUrlChange Callback triggered when the URL text is modified.
 * @param modifier Optional layout modifier.
 * @param label The label displayed on the text field (defaults to "URL").
 * @param placeholder The placeholder text when empty.
 * @param enabled Whether input and actions are enabled.
 * @param urlError Optional error message displayed below the field.
 * @param showNormalizeSuggestion Whether to show the normalized URL suggestion option when available.
 * @param showClearArgsButton Whether to display the "Clear Url args" button when query parameters exist.
 * @param onNormalizeSelected Optional custom callback when the user selects the normalized URL.
 * @param onClearArgs Optional callback when "Clear Url args" is clicked.
 * @param additionalSuggestions Optional list of extra suggestion URLs (e.g., feed or channel links).
 * @param onSuggestionClick Optional callback when an additional suggestion chip is tapped.
 * @param trailingIcon Optional trailing composable icon inside the text field.
 * @param keyboardOptions Keyboard options for the text field.
 * @param keyboardActions Keyboard actions for the text field.
 */
@Composable
fun UrlInputPane(
    url: String,
    onUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "URL",
    placeholder: String = "https://example.com",
    enabled: Boolean = true,
    urlError: String? = null,
    showNormalizeSuggestion: Boolean = true,
    showClearArgsButton: Boolean = false,
    onNormalizeSelected: ((String) -> Unit)? = null,
    onClearArgs: (() -> Unit)? = null,
    additionalSuggestions: List<String> = emptyList(),
    onSuggestionClick: ((String) -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Default
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val normalizedUrl = remember(url) {
        if (url.isNotBlank()) UrlLocation.normalizeUrl(url) else ""
    }
    val hasNormalizationDifference = showNormalizeSuggestion &&
            enabled &&
            url.isNotBlank() &&
            normalizedUrl.isNotBlank() &&
            normalizedUrl != url

    Column(modifier = modifier) {
        OutlinedTextField(
            value = url,
            onValueChange = { if (enabled) onUrlChange(it) },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = urlError != null,
            supportingText = urlError?.let { { Text(it) } },
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )

        // URL manipulation controls (Clear Url args & Normalization suggestion)
        if (hasNormalizationDifference || (showClearArgsButton && url.contains('?'))) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showClearArgsButton && url.contains('?')) {
                    OutlinedButton(
                        onClick = {
                            if (onClearArgs != null) {
                                onClearArgs()
                            } else {
                                val cleaned = UrlLocation.clearUrlArgs(url)
                                onUrlChange(cleaned)
                            }
                        },
                        enabled = enabled
                    ) {
                        Text("Clear Url args")
                    }
                    if (hasNormalizationDifference) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                if (hasNormalizationDifference) {
                    Text(
                        text = "Normalized: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    SuggestionChip(
                        onClick = {
                            if (onNormalizeSelected != null) {
                                onNormalizeSelected(normalizedUrl)
                            } else {
                                onUrlChange(normalizedUrl)
                            }
                        },
                        enabled = enabled,
                        label = {
                            Text(
                                text = normalizedUrl,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }

        // Additional suggestion chips (e.g. RSS feeds / channel links)
        if (additionalSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                additionalSuggestions.forEach { suggestionUrl ->
                    SuggestionChip(
                        onClick = { onSuggestionClick?.invoke(suggestionUrl) ?: onUrlChange(suggestionUrl) },
                        enabled = enabled,
                        label = { Text(suggestionUrl, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}
