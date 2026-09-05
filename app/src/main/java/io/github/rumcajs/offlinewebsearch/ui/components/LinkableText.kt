package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Utility for detecting URLs in plain text and building [AnnotatedString] with link annotations.
 */
object LinkifyUtils {
    private val URL_REGEX = Regex(
        """(https?://|www\.)[^\s<>"'{}|\\^`]+""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', ')', ']', '}')

    /**
     * Finds URL matches in [text] and returns a list of Pair(character index range, normalized URL string).
     */
    fun findUrlRanges(text: String): List<Pair<IntRange, String>> {
        val matches = URL_REGEX.findAll(text)
        val result = mutableListOf<Pair<IntRange, String>>()

        for (match in matches) {
            var raw = match.value
            var end = match.range.last

            while (raw.isNotEmpty() && raw.last() in TRAILING_PUNCTUATION) {
                raw = raw.dropLast(1)
                end--
            }

            if (raw.isNotEmpty()) {
                val normalizedUrl = if (raw.startsWith("www.", ignoreCase = true)) {
                    "https://$raw"
                } else {
                    raw
                }
                result.add(Pair(match.range.first..end, normalizedUrl))
            }
        }
        return result
    }

    /**
     * Constructs an [AnnotatedString] with [LinkAnnotation.Url] annotations on all detected links.
     */
    fun buildLinkAnnotatedString(
        text: String,
        linkStyles: TextLinkStyles
    ): AnnotatedString {
        val urlRanges = findUrlRanges(text)
        if (urlRanges.isEmpty()) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            var currentIndex = 0
            for ((range, url) in urlRanges) {
                if (range.first > currentIndex) {
                    append(text.substring(currentIndex, range.first))
                }
                val linkText = text.substring(range.first, range.last + 1)
                val start = length
                append(linkText)
                val end = length
                addLink(
                    url = LinkAnnotation.Url(
                        url = url,
                        styles = linkStyles
                    ),
                    start = start,
                    end = end
                )
                currentIndex = range.last + 1
            }
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }
}

/**
 * Composable that displays text with clickable URLs and text selection/copying support.
 *
 * URLs found in [text] are rendered with primary link color and underline styling.
 * Clicking a link opens the URL in the browser / system handler.
 * Wrapped in [SelectionContainer] so users can select and copy any part of the text.
 *
 * @param text The plain text content to display.
 * @param modifier Optional modifier for the container / Text.
 * @param fontSize Font size for the text (default 16 sp).
 * @param lineHeight Line height for the text (default 24 sp).
 * @param color Text color (default MaterialTheme.colorScheme.onSurface).
 * @param linkColor Color used for clickable hyperlinks (default MaterialTheme.colorScheme.primary).
 */
@Composable
fun LinkableText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 24.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    val linkStyles = remember(linkColor) {
        TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            )
        )
    }

    val annotatedText = remember(text, linkStyles) {
        LinkifyUtils.buildLinkAnnotatedString(text, linkStyles)
    }

    SelectionContainer(modifier = modifier) {
        Text(
            text = annotatedText,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color
        )
    }
}
