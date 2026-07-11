package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.webtoolkit.HtmlPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.PageBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBuilderTest {

    @Test
    fun testIdentifyHtmlInputType() {
        val page = PageBuilder.build("https://example.com/page", "<html></html>", "html")
        assertTrue(page is HtmlPage)
    }

    @Test
    fun testIdentifyRssInputType() {
        val page = PageBuilder.build("https://example.com/feed", "<rss></rss>", "rss")
        assertTrue(page is RssPage)
    }

    @Test
    fun testIdentifyFallbackHtmlByContent() {
        val page = PageBuilder.build("https://example.com/page", "  <!DOCTYPE html><html></html>", "unknown")
        assertTrue(page is HtmlPage)
    }

    @Test
    fun testIdentifyFallbackRssByContent() {
        val page = PageBuilder.build("https://example.com/feed", "<rss version=\"2.0\"></rss>", "unknown")
        assertTrue(page is RssPage)
    }
}
