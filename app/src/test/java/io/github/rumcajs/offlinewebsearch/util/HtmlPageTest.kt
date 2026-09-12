package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.webtoolkit.HtmlPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlPageTest {

    @Test
    fun testHtmlOgParsing() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Example Page Title"/>
                <meta name="og:description" content="This is an &amp; exciting example description."/>
                <meta property='og:image' content='https://example.com/image1.jpg'/>
                <meta property="og:image:secure_url" content="https://example.com/image2.jpg"/>
                <meta name="article:published_time" content="2024-01-01T12:00:00Z"/>
            </head>
            <body>
            </body>
            </html>
        """.trimIndent()

        val htmlPage = HtmlPage("https://example.com/page.html", html)

        assertEquals("Example Page Title", htmlPage.getTitle())
        assertEquals("This is an & exciting example description.", htmlPage.getDescription())
        assertEquals(2, htmlPage.getThumbnails().size)
        assertEquals("https://example.com/image1.jpg", htmlPage.getThumbnails()[0])
        assertEquals("https://example.com/image2.jpg", htmlPage.getThumbnails()[1])
        assertEquals(DateUtils.parseDateString("2024-01-01T12:00:00Z"), htmlPage.getDatePublished())
    }

    @Test
    fun testHtmlOgParsingWithUnquotedAttributesAndDifferentCasing() {
        val html = """
            <html>
            <head>
                <META PROPERTY=og:title CONTENT=UnquotedTitle>
                <meta property="OG:DESCRIPTION" content="Another description">
                <meta name="og:image" content=https://example.com/image3.jpg>
                <meta property="og:pubdate" content="2025-05-05">
            </head>
            </html>
        """.trimIndent()

        val htmlPage = HtmlPage("https://example.com/page2.html", html)

        assertEquals("UnquotedTitle", htmlPage.getTitle())
        assertEquals("Another description", htmlPage.getDescription())
        assertEquals(1, htmlPage.getThumbnails().size)
        assertEquals("https://example.com/image3.jpg", htmlPage.getThumbnails()[0])
        assertEquals(DateUtils.parseDateString("2025-05-05"), htmlPage.getDatePublished())
    }

    @Test
    fun testEmptyHtml() {
        val htmlPage = HtmlPage("https://example.com/empty.html", "")
        assertNull(htmlPage.getTitle())
        assertNull(htmlPage.getDescription())
        assertTrue(htmlPage.getThumbnails().isEmpty())
        assertNull(htmlPage.getDatePublished())
    }

    @Test
    fun testNoOgTags() {
        val html = """
            <html>
            <head>
                <title>Standard Page Title</title>
                <meta name="description" content="Standard description">
            </head>
            </html>
        """.trimIndent()
        val htmlPage = HtmlPage("https://example.com/no_og.html", html)
        assertNull(htmlPage.getTitle())
        assertNull(htmlPage.getDescription())
        assertTrue(htmlPage.getThumbnails().isEmpty())
        assertNull(htmlPage.getDatePublished())
    }

    @Test
    fun testGetFeeds() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="alternate" type="application/rss+xml" title="RSS Feed" href="https://example.com/rss.xml" />
                <link rel="alternate" type="application/atom+xml" title="Atom Feed" href="/feed.atom" />
                <link rel="stylesheet" href="/style.css" />
                <link type="application/rdf+xml" href="relative/rdf.xml" />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val htmlPage = HtmlPage("https://example.com/blog/index.html", html)
        val feeds = htmlPage.getFeeds()

        assertEquals(3, feeds.size)
        assertEquals("https://example.com/rss.xml", feeds[0])
        assertEquals("https://example.com/feed.atom", feeds[1])
        assertEquals("https://example.com/blog/relative/rdf.xml", feeds[2])
    }

    @Test
    fun testGetFeedsEmpty() {
        val html = "<html><head><link rel=\"stylesheet\" href=\"style.css\"/></head><body></body></html>"
        val htmlPage = HtmlPage("https://example.com/", html)
        assertTrue(htmlPage.getFeeds().isEmpty())

        val emptyPage = HtmlPage("https://example.com/", "")
        assertTrue(emptyPage.getFeeds().isEmpty())
    }
}
