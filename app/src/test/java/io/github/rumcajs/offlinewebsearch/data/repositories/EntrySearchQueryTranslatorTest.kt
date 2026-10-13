package io.github.rumcajs.offlinewebsearch.data.repositories

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [EntrySearchQueryTranslator].
 *
 * Verifies parsing of search expressions with and without single/double quotes,
 * exact equality (`==`), LIKE/contains (`=`), and SQLite LIKE syntax.
 */
class EntrySearchQueryTranslatorTest {

    @Test
    fun testBlankQuery() {
        val parsed = EntrySearchQueryTranslator.parse("")
        assertTrue(parsed is ParsedQuery.FullText)
        assertEquals("", (parsed as ParsedQuery.FullText).term)

        val parsedSpaces = EntrySearchQueryTranslator.parse("   ")
        assertTrue(parsedSpaces is ParsedQuery.FullText)
        assertEquals("", (parsedSpaces as ParsedQuery.FullText).term)
    }

    @Test
    fun testExactMatchWithQuotes() {
        val parsed1 = EntrySearchQueryTranslator.parse("source_id == '88'")
        assertTrue(parsed1 is ParsedQuery.FieldExact)
        assertEquals("source_id", (parsed1 as ParsedQuery.FieldExact).field)
        assertEquals("88", parsed1.term)

        val parsed2 = EntrySearchQueryTranslator.parse("source_id=='88'")
        assertTrue(parsed2 is ParsedQuery.FieldExact)
        assertEquals("source_id", (parsed2 as ParsedQuery.FieldExact).field)
        assertEquals("88", parsed2.term)

        val parsed3 = EntrySearchQueryTranslator.parse("source_id == \"88\"")
        assertTrue(parsed3 is ParsedQuery.FieldExact)
        assertEquals("source_id", (parsed3 as ParsedQuery.FieldExact).field)
        assertEquals("88", parsed3.term)

        val parsed4 = EntrySearchQueryTranslator.parse("source_url == 'https://example.com/rss'")
        assertTrue(parsed4 is ParsedQuery.FieldExact)
        assertEquals("source_url", (parsed4 as ParsedQuery.FieldExact).field)
        assertEquals("https://example.com/rss", parsed4.term)

        val parsed5 = EntrySearchQueryTranslator.parse("title == 'My Article'")
        assertTrue(parsed5 is ParsedQuery.FieldExact)
        assertEquals("title", (parsed5 as ParsedQuery.FieldExact).field)
        assertEquals("My Article", parsed5.term)
    }

    @Test
    fun testExactMatchWithoutQuotes() {
        val parsed1 = EntrySearchQueryTranslator.parse("source_id == 88")
        assertTrue(parsed1 is ParsedQuery.FieldExact)
        assertEquals("source_id", (parsed1 as ParsedQuery.FieldExact).field)
        assertEquals("88", parsed1.term)

        val parsed2 = EntrySearchQueryTranslator.parse("source_id==88")
        assertTrue(parsed2 is ParsedQuery.FieldExact)
        assertEquals("source_id", (parsed2 as ParsedQuery.FieldExact).field)
        assertEquals("88", parsed2.term)

        val parsed3 = EntrySearchQueryTranslator.parse("title == youtube")
        assertTrue(parsed3 is ParsedQuery.FieldExact)
        assertEquals("title", (parsed3 as ParsedQuery.FieldExact).field)
        assertEquals("youtube", parsed3.term)
    }

    @Test
    fun testContainsMatchWithQuotes() {
        val parsed1 = EntrySearchQueryTranslator.parse("source_id = '88'")
        assertTrue(parsed1 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed1 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed1.term)

        val parsed2 = EntrySearchQueryTranslator.parse("source_id='88'")
        assertTrue(parsed2 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed2 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed2.term)

        val parsed3 = EntrySearchQueryTranslator.parse("source_id = \"88\"")
        assertTrue(parsed3 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed3 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed3.term)

        val parsed4 = EntrySearchQueryTranslator.parse("source_url = 'https://example.com'")
        assertTrue(parsed4 is ParsedQuery.FieldContains)
        assertEquals("source_url", (parsed4 as ParsedQuery.FieldContains).field)
        assertEquals("https://example.com", parsed4.term)

        val parsed5 = EntrySearchQueryTranslator.parse("title = 'news'")
        assertTrue(parsed5 is ParsedQuery.FieldContains)
        assertEquals("title", (parsed5 as ParsedQuery.FieldContains).field)
        assertEquals("news", parsed5.term)
    }

    @Test
    fun testContainsMatchWithoutQuotes() {
        val parsed1 = EntrySearchQueryTranslator.parse("source_id = 88")
        assertTrue(parsed1 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed1 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed1.term)

        val parsed2 = EntrySearchQueryTranslator.parse("source_id=88")
        assertTrue(parsed2 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed2 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed2.term)

        val parsed3 = EntrySearchQueryTranslator.parse("title = youtube")
        assertTrue(parsed3 is ParsedQuery.FieldContains)
        assertEquals("title", (parsed3 as ParsedQuery.FieldContains).field)
        assertEquals("youtube", parsed3.term)
    }

    @Test
    fun testSqliteLikeMatch() {
        val parsed1 = EntrySearchQueryTranslator.parse("source_id LIKE '%88%'")
        assertTrue(parsed1 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed1 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed1.term)

        val parsed2 = EntrySearchQueryTranslator.parse("source_id LIKE '88'")
        assertTrue(parsed2 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed2 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed2.term)

        val parsed3 = EntrySearchQueryTranslator.parse("title LIKE '%tech%'")
        assertTrue(parsed3 is ParsedQuery.FieldContains)
        assertEquals("title", (parsed3 as ParsedQuery.FieldContains).field)
        assertEquals("tech", parsed3.term)
    }

    @Test
    fun testCaseInsensitiveFieldNames() {
        val parsed1 = EntrySearchQueryTranslator.parse("SOURCE_ID = '88'")
        assertTrue(parsed1 is ParsedQuery.FieldContains)
        assertEquals("source_id", (parsed1 as ParsedQuery.FieldContains).field)
        assertEquals("88", parsed1.term)

        val parsed2 = EntrySearchQueryTranslator.parse("Source_Id == '88'")
        assertTrue(parsed2 is ParsedQuery.FieldExact)
        assertEquals("source_id", (parsed2 as ParsedQuery.FieldExact).field)
        assertEquals("88", parsed2.term)
    }

    @Test
    fun testFullTextFallback() {
        val parsed = EntrySearchQueryTranslator.parse("android kotlin")
        assertTrue(parsed is ParsedQuery.FullText)
        assertEquals("android kotlin", (parsed as ParsedQuery.FullText).term)
    }

    @Test
    fun testWhitespaceHandling() {
        // Leading and trailing spaces around raw query
        val leadingTrailing = EntrySearchQueryTranslator.parse("   source_id = '88'   ")
        assertTrue(leadingTrailing is ParsedQuery.FieldContains)
        assertEquals("source_id", (leadingTrailing as ParsedQuery.FieldContains).field)
        assertEquals("88", leadingTrailing.term)

        // Multiple spaces and tabs around operators
        val multiSpacesEq = EntrySearchQueryTranslator.parse("source_id   =   '88'")
        assertTrue(multiSpacesEq is ParsedQuery.FieldContains)
        assertEquals("88", (multiSpacesEq as ParsedQuery.FieldContains).term)

        val tabEq = EntrySearchQueryTranslator.parse("source_id\t=\t'88'")
        assertTrue(tabEq is ParsedQuery.FieldContains)
        assertEquals("88", (tabEq as ParsedQuery.FieldContains).term)

        val multiSpacesExact = EntrySearchQueryTranslator.parse("source_id   ==   '88'")
        assertTrue(multiSpacesExact is ParsedQuery.FieldExact)
        assertEquals("88", (multiSpacesExact as ParsedQuery.FieldExact).term)

        val multiSpacesLike = EntrySearchQueryTranslator.parse("title    LIKE    '%machine learning%'")
        assertTrue(multiSpacesLike is ParsedQuery.FieldContains)
        assertEquals("machine learning", (multiSpacesLike as ParsedQuery.FieldContains).term)

        // Multi-word values inside quotes (internal spaces preserved)
        val quotedPhrase = EntrySearchQueryTranslator.parse("title = 'machine learning in android'")
        assertTrue(quotedPhrase is ParsedQuery.FieldContains)
        assertEquals("machine learning in android", (quotedPhrase as ParsedQuery.FieldContains).term)

        val exactQuotedPhrase = EntrySearchQueryTranslator.parse("title == 'Rust vs C++'")
        assertTrue(exactQuotedPhrase is ParsedQuery.FieldExact)
        assertEquals("Rust vs C++", (exactQuotedPhrase as ParsedQuery.FieldExact).term)

        // Spaces inside quotes around the term (trimmed)
        val paddedQuotes = EntrySearchQueryTranslator.parse("source_id = '  88  '")
        assertTrue(paddedQuotes is ParsedQuery.FieldContains)
        assertEquals("88", (paddedQuotes as ParsedQuery.FieldContains).term)

        // Spaces after closing quote
        val trailingQuoteSpaces = EntrySearchQueryTranslator.parse("source_id = '88'   ")
        assertTrue(trailingQuoteSpaces is ParsedQuery.FieldContains)
        assertEquals("88", (trailingQuoteSpaces as ParsedQuery.FieldContains).term)

        // Unquoted multi-word values
        val unquotedPhrase = EntrySearchQueryTranslator.parse("title = machine learning")
        assertTrue(unquotedPhrase is ParsedQuery.FieldContains)
        assertEquals("machine learning", (unquotedPhrase as ParsedQuery.FieldContains).term)

        // Whitespace-only strings return empty FullText
        val spacesOnly = EntrySearchQueryTranslator.parse("   \t  \n  ")
        assertTrue(spacesOnly is ParsedQuery.FullText)
        assertEquals("", (spacesOnly as ParsedQuery.FullText).term)
    }
}
