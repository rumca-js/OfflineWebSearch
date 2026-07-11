package io.github.rumcajs.offlinewebsearch.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssPageTest {

    @Test
    fun testRss20Parsing() {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Feed Title Example</title>
                <description>Feed Description Example</description>
                <link>https://example.com</link>
                <item>
                  <title>Item 1 Title</title>
                  <link>https://example.com/item1</link>
                  <description>Description for Item 1</description>
                </item>
                <item>
                  <title>Item 2 Title</title>
                  <link>https://example.com/item2</link>
                  <description>Description for Item 2</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val rssPage = RssPage("https://example.com/feed.xml", xml)

        assertEquals("Feed Title Example", rssPage.getTitle())
        assertEquals("Feed Description Example", rssPage.getDescription())

        val entries = rssPage.getEntries()
        assertEquals(2, entries.size)
        assertEquals("https://example.com/item1", entries[0].link)
        assertEquals("Description for Item 1", entries[0].description)
        assertEquals("https://example.com/item2", entries[1].link)
        assertEquals("Description for Item 2", entries[1].description)
    }

    @Test
    fun testAtomParsing() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed Title</title>
              <subtitle>Atom Subtitle</subtitle>
              <entry>
                <title>Atom Entry 1</title>
                <link href="https://example.com/atom1" />
                <summary>Atom Summary 1</summary>
              </entry>
            </feed>
        """.trimIndent()

        val rssPage = RssPage("https://example.com/atom.xml", xml)

        assertEquals("Atom Feed Title", rssPage.getTitle())
        assertEquals("Atom Subtitle", rssPage.getDescription())

        val entries = rssPage.getEntries()
        assertEquals(1, entries.size)
        assertEquals("https://example.com/atom1", entries[0].link)
        assertEquals("Atom Summary 1", entries[0].description)
    }

    @Test
    fun testInvalidXmlParsing() {
        val rssPage = RssPage("https://example.com/invalid.xml", "invalid xml contents")
        assertNull(rssPage.getTitle())
        assertNull(rssPage.getDescription())
        assertTrue(rssPage.getEntries().isEmpty())
    }

    @Test
    fun testEmptyXmlParsing() {
        val rssPage = RssPage("https://example.com/empty.xml", "")
        assertNull(rssPage.getTitle())
        assertNull(rssPage.getDescription())
        assertTrue(rssPage.getEntries().isEmpty())
    }
}
