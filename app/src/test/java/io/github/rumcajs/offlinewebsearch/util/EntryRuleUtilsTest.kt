package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [EntryRuleUtils].
 */
class EntryRuleUtilsTest {

    @Test
    fun testMatchesRuleUrlSinglePattern() {
        assertTrue(EntryRuleUtils.matchesRuleUrl("https://example.com/ads/123", ".*ads.*"))
        assertFalse(EntryRuleUtils.matchesRuleUrl("https://example.com/news/123", ".*ads.*"))
        assertTrue(EntryRuleUtils.matchesRuleUrl("https://example.com/track", "*track*"))
        assertFalse(EntryRuleUtils.matchesRuleUrl("", ".*"))
        assertFalse(EntryRuleUtils.matchesRuleUrl("https://example.com", ""))
    }

    @Test
    fun testMatchesRuleUrlMultiplePatterns() {
        val pattern = ".*bit\\.ly.*, .*\\.link\\.to.*, *tinyurl*"
        assertTrue(EntryRuleUtils.matchesRuleUrl("https://bit.ly/xyz", pattern))
        assertTrue(EntryRuleUtils.matchesRuleUrl("https://news.link.to/article", pattern))
        assertTrue(EntryRuleUtils.matchesRuleUrl("https://tinyurl.com/abc", pattern))
        assertFalse(EntryRuleUtils.matchesRuleUrl("https://regular-site.com/home", pattern))
    }

    @Test
    fun testIsLinkBlocked() {
        val rules = listOf(
            EntryRule(id = 1L, rule_name = "Block Ads", trigger_rule_url = ".*ads.*", block = true, enabled = true),
            EntryRule(id = 2L, rule_name = "Track Clicks", trigger_rule_url = ".*click.*", block = false, enabled = true),
            EntryRule(id = 3L, rule_name = "Disabled Block", trigger_rule_url = ".*spam.*", block = true, enabled = false)
        )

        // Blocked link
        assertTrue(EntryRuleUtils.isLinkBlocked("https://example.com/ads/banner", rules))

        // Non-blocking matched link
        assertFalse(EntryRuleUtils.isLinkBlocked("https://example.com/click/item", rules))

        // Link matching disabled rule
        assertFalse(EntryRuleUtils.isLinkBlocked("https://example.com/spam/item", rules))

        // Unmatched link
        assertFalse(EntryRuleUtils.isLinkBlocked("https://example.com/article", rules))

        // Blank link
        assertFalse(EntryRuleUtils.isLinkBlocked("", rules))
    }

    @Test
    fun testIsEntryBlocked() {
        val rules = listOf(
            EntryRule(id = 1L, rule_name = "Block Spam", trigger_rule_url = ".*spam.*", block = true, enabled = true)
        )

        val blockedEntry = Entry(link = "https://example.com/spam/item", title = "Spam")
        val cleanEntry = Entry(link = "https://example.com/clean/item", title = "Clean")
        val blankLinkEntry = Entry(link = null, title = "No Link")

        assertTrue(EntryRuleUtils.isEntryBlocked(blockedEntry, rules))
        assertFalse(EntryRuleUtils.isEntryBlocked(cleanEntry, rules))
        assertFalse(EntryRuleUtils.isEntryBlocked(blankLinkEntry, rules))
    }

    @Test
    fun testGetMatchingRules() {
        val rule1 = EntryRule(id = 1L, rule_name = "Rule 1", trigger_rule_url = ".*domain.*", enabled = true)
        val rule2 = EntryRule(id = 2L, rule_name = "Rule 2", trigger_rule_url = ".*test.*", enabled = true)
        val rule3 = EntryRule(id = 3L, rule_name = "Rule 3 (Disabled)", trigger_rule_url = ".*domain.*", enabled = false)
        val rules = listOf(rule1, rule2, rule3)

        val matchingRules = EntryRuleUtils.getMatchingRulesForLink("https://domain.com/test", rules)
        assertEquals(2, matchingRules.size)
        assertTrue(matchingRules.contains(rule1))
        assertTrue(matchingRules.contains(rule2))
        assertFalse(matchingRules.contains(rule3))

        val matchingEntryRules = EntryRuleUtils.getMatchingRulesForEntry(Entry(link = "https://domain.com/item"), rules)
        assertEquals(1, matchingEntryRules.size)
        assertEquals(rule1, matchingEntryRules[0])
    }
}
