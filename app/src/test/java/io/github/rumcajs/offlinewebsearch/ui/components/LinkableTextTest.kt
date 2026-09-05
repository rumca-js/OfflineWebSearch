package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [LinkifyUtils].
 */
class LinkableTextTest {

    @Test
    fun `findUrlRanges returns empty for plain text without links`() {
        val text = "This is a plain description without any links."
        val ranges = LinkifyUtils.findUrlRanges(text)
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `findUrlRanges detects single https link`() {
        val text = "Check out https://facebook for updates."
        val ranges = LinkifyUtils.findUrlRanges(text)
        assertEquals(1, ranges.size)
        assertEquals("https://facebook", ranges[0].second)
        assertEquals("https://facebook", text.substring(ranges[0].first))
    }

    @Test
    fun `findUrlRanges detects single http link`() {
        val text = "Old site: http://example.org/path?query=1"
        val ranges = LinkifyUtils.findUrlRanges(text)
        assertEquals(1, ranges.size)
        assertEquals("http://example.org/path?query=1", ranges[0].second)
    }

    @Test
    fun `findUrlRanges detects www prefix and normalizes to https`() {
        val text = "Visit www.example.com for more info"
        val ranges = LinkifyUtils.findUrlRanges(text)
        assertEquals(1, ranges.size)
        assertEquals("https://www.example.com", ranges[0].second)
        assertEquals("www.example.com", text.substring(ranges[0].first))
    }

    @Test
    fun `findUrlRanges strips trailing punctuation attached to sentence`() {
        val text = "See https://github.com/rumca-js/OfflineWebSearch. Also check https://kotlinlang.org! And (https://google.com)?"
        val ranges = LinkifyUtils.findUrlRanges(text)
        assertEquals(3, ranges.size)
        assertEquals("https://github.com/rumca-js/OfflineWebSearch", ranges[0].second)
        assertEquals("https://kotlinlang.org", ranges[1].second)
        assertEquals("https://google.com", ranges[2].second)
    }

    @Test
    fun `findUrlRanges detects multiple URLs in sequence`() {
        val text = "First https://a.com then https://b.com/page and finally https://c.org"
        val ranges = LinkifyUtils.findUrlRanges(text)
        assertEquals(3, ranges.size)
        assertEquals("https://a.com", ranges[0].second)
        assertEquals("https://b.com/page", ranges[1].second)
        assertEquals("https://c.org", ranges[2].second)
    }

    @Test
    fun `buildLinkAnnotatedString creates annotations with matching URLs`() {
        val text = "Visit https://facebook and https://github.com for details."
        val styles = TextLinkStyles(
            style = SpanStyle(
                color = Color.Blue,
                textDecoration = TextDecoration.Underline
            )
        )
        val annotated = LinkifyUtils.buildLinkAnnotatedString(text, styles)

        assertEquals(text, annotated.text)
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(2, links.size)

        val firstLink = links[0].item as? LinkAnnotation.Url
        assertNotNull(firstLink)
        assertEquals("https://facebook", firstLink?.url)

        val secondLink = links[1].item as? LinkAnnotation.Url
        assertNotNull(secondLink)
        assertEquals("https://github.com", secondLink?.url)
    }
}
