package io.github.rumcajs.offlinewebsearch.data.repositories

import org.junit.Assert.*
import org.junit.Test

class SourceSearchQueryTranslatorTest {

    @Test
    fun testBlankQuery() {
        val parsed = SourceSearchQueryTranslator.parse("")
        assertTrue(parsed is ParsedQuery.FullText)
        assertEquals("", (parsed as ParsedQuery.FullText).term)
    }

    @Test
    fun testExactMatch() {
        val parsed1 = SourceSearchQueryTranslator.parse("title==youtube")
        assertTrue(parsed1 is ParsedQuery.FieldExact)
        assertEquals("title", (parsed1 as ParsedQuery.FieldExact).field)
        assertEquals("youtube", parsed1.term)

        val parsed2 = SourceSearchQueryTranslator.parse("url == 'https://example.com/feed.xml'")
        assertTrue(parsed2 is ParsedQuery.FieldExact)
        assertEquals("url", (parsed2 as ParsedQuery.FieldExact).field)
        assertEquals("https://example.com/feed.xml", parsed2.term)
    }

    @Test
    fun testContainsMatch() {
        val parsed1 = SourceSearchQueryTranslator.parse("title=news")
        assertTrue(parsed1 is ParsedQuery.FieldContains)
        assertEquals("title", (parsed1 as ParsedQuery.FieldContains).field)
        assertEquals("news", parsed1.term)

        val parsed2 = SourceSearchQueryTranslator.parse("url LIKE '%tech%'")
        assertTrue(parsed2 is ParsedQuery.FieldContains)
        assertEquals("url", (parsed2 as ParsedQuery.FieldContains).field)
        assertEquals("tech", parsed2.term)
    }

    @Test
    fun testFullTextFallback() {
        val parsed = SourceSearchQueryTranslator.parse("android kotlin")
        assertTrue(parsed is ParsedQuery.FullText)
        assertEquals("android kotlin", (parsed as ParsedQuery.FullText).term)
    }
}
