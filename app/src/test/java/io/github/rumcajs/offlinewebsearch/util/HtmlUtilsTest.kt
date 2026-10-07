package io.github.rumcajs.offlinewebsearch.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlUtilsTest {

    @Test
    fun testUnescapeHtmlBasicEntities() {
        assertEquals("Hello & World", HtmlUtils.unescapeHtml("Hello &amp; World"))
        assertEquals("\"Quotes\" and 'Apos'", HtmlUtils.unescapeHtml("&quot;Quotes&quot; and &apos;Apos&apos;"))
        assertEquals("<tag> & 'test'", HtmlUtils.unescapeHtml("&lt;tag&gt; &amp; &#39;test&#39;"))
        assertEquals("Non-breaking space", HtmlUtils.unescapeHtml("Non-breaking&nbsp;space"))
    }

    @Test
    fun testUnescapeHtmlDoubleEscapedEntities() {
        // Double-escaped entities like &amp;quot; should decode to "
        assertEquals("\"Double Quotes\"", HtmlUtils.unescapeHtml("&amp;quot;Double Quotes&amp;quot;"))
        assertEquals("&", HtmlUtils.unescapeHtml("&amp;amp;"))
    }

    @Test
    fun testUnescapeHtmlNumericEntities() {
        // Decimal &#34; is ", &#39; is '
        assertEquals("\"Hello\"", HtmlUtils.unescapeHtml("&#34;Hello&#34;"))
        assertEquals("'World'", HtmlUtils.unescapeHtml("&#39;World&#39;"))
        // Hex &#x22; is ", &#x27; is '
        assertEquals("\"Hex\"", HtmlUtils.unescapeHtml("&#x22;Hex&#x22;"))
        assertEquals("'Hex'", HtmlUtils.unescapeHtml("&#x27;Hex&#x27;"))
        // Unicode character &#8217; is ’
        assertEquals("It’s great", HtmlUtils.unescapeHtml("It&#8217;s great"))
    }

    @Test
    fun testUnescapeHtmlNamedEntities() {
        assertEquals("‘Single’ and “Double” quotes", HtmlUtils.unescapeHtml("&lsquo;Single&rsquo; and &ldquo;Double&rdquo; quotes"))
        assertEquals("Dashes: – and —", HtmlUtils.unescapeHtml("Dashes: &ndash; and &mdash;"))
        assertEquals("More…", HtmlUtils.unescapeHtml("More&hellip;"))
        assertEquals("© 2024 Example • All Rights Reserved", HtmlUtils.unescapeHtml("&copy; 2024 Example &bull; All Rights Reserved"))
        assertEquals("Prices: 10€, 5£, 100¥", HtmlUtils.unescapeHtml("Prices: 10&euro;, 5&pound;, 100&yen;"))
    }

    @Test
    fun testStripHtml() {
        val html = """
            <style>body { color: red; }</style>
            <script>alert('xss');</script>
            <p>First paragraph with <b>bold</b> and <a href="https://example.com">link</a>.</p>
            <p>Second paragraph with &quot;quotes&quot; &amp; &nbsp;spaces.</p>
            <br/>
            Final line.
        """.trimIndent()

        val plain = HtmlUtils.stripHtml(html)
        assertEquals(
            "First paragraph with bold and link.\n\nSecond paragraph with \"quotes\" & spaces.\n\nFinal line.",
            plain
        )
    }

    @Test
    fun testCleanTitle() {
        assertNull(HtmlUtils.cleanTitle(null))
        assertNull(HtmlUtils.cleanTitle(""))
        assertNull(HtmlUtils.cleanTitle("   "))

        // Unescapes &quot; and strips tags
        assertEquals(
            "\"Breaking News\": New discovery & updates",
            HtmlUtils.cleanTitle("&quot;Breaking News&quot;: <b>New discovery</b> &amp; updates")
        )

        // Double-escaped in title
        assertEquals(
            "\"Quoted Title\"",
            HtmlUtils.cleanTitle("&amp;quot;Quoted Title&amp;quot;")
        )

        // Flatten newlines and multiple spaces
        assertEquals(
            "Multi-line title with spaces",
            HtmlUtils.cleanTitle("Multi-line\n  title   with\tspaces\r\n")
        )
    }

    @Test
    fun testCleanDescription() {
        assertNull(HtmlUtils.cleanDescription(null))
        assertNull(HtmlUtils.cleanDescription(""))
        assertNull(HtmlUtils.cleanDescription("   "))

        val html = "<p>Summary of article with &quot;quotes&quot; and <a href=\"#\">links</a>.</p>"
        assertEquals(
            "Summary of article with \"quotes\" and links.",
            HtmlUtils.cleanDescription(html)
        )
    }
}
