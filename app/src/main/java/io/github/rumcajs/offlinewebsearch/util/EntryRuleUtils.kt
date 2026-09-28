package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import java.util.regex.PatternSyntaxException

/**
 * Utility functions for evaluating and filtering entries and URLs against [EntryRule] objects.
 */
object EntryRuleUtils {

    /**
     * Matches [link] against a [pattern] using regular expression matching.
     * Supports comma-separated patterns (e.g. `".*bit\\.ly.*, .*\\.link\\.to.*"`),
     * standard regex patterns, with fallback to wildcard and substring matches.
     *
     * @param link The entry URL string to test.
     * @param pattern Regular expression pattern(s) from [EntryRule.trigger_rule_url].
     * @return True if the link matches any pattern.
     */
    fun matchesRuleUrl(link: String, pattern: String): Boolean {
        if (pattern.isBlank() || link.isBlank()) return false
        val patterns = pattern.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return patterns.any { singlePattern -> matchesSinglePattern(link, singlePattern) }
    }

    private fun matchesSinglePattern(link: String, pattern: String): Boolean {
        if (pattern.isBlank() || link.isBlank()) return false
        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            regex.containsMatchIn(link) || regex.matches(link)
        } catch (e: PatternSyntaxException) {
            val wildcardRegex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
            try {
                Regex(wildcardRegex, RegexOption.IGNORE_CASE).containsMatchIn(link)
            } catch (e2: Exception) {
                link.contains(pattern, ignoreCase = true)
            }
        } catch (e: Exception) {
            link.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * Checks if a [link] is blocked by any enabled rule in [rules].
     *
     * @param link URL string to check.
     * @param rules List of [EntryRule]s to evaluate against.
     * @return True if at least one enabled rule with `block == true` matches [link].
     */
    fun isLinkBlocked(link: String, rules: List<EntryRule>): Boolean {
        if (link.isBlank()) return false
        return rules.any { rule ->
            rule.enabled && rule.block && rule.trigger_rule_url.isNotBlank() && matchesRuleUrl(link, rule.trigger_rule_url)
        }
    }

    /**
     * Checks if an [entry] is blocked by any enabled rule in [rules].
     *
     * @param entry [Entry] to check.
     * @param rules List of [EntryRule]s to evaluate against.
     * @return True if the entry has a non-blank link that is blocked by an active rule.
     */
    fun isEntryBlocked(entry: Entry, rules: List<EntryRule>): Boolean {
        val link = entry.link ?: return false
        return isLinkBlocked(link, rules)
    }

    /**
     * Returns all enabled rules in [rules] that match the given [link].
     *
     * @param link URL string to test.
     * @param rules List of candidate [EntryRule]s.
     * @return List of matching enabled [EntryRule]s.
     */
    fun getMatchingRulesForLink(link: String, rules: List<EntryRule>): List<EntryRule> {
        if (link.isBlank()) return emptyList()
        return rules.filter { rule ->
            rule.enabled && rule.trigger_rule_url.isNotBlank() && matchesRuleUrl(link, rule.trigger_rule_url)
        }
    }

    /**
     * Returns all enabled rules in [rules] that match the given [entry].
     *
     * @param entry [Entry] to test.
     * @param rules List of candidate [EntryRule]s.
     * @return List of matching enabled [EntryRule]s.
     */
    fun getMatchingRulesForEntry(entry: Entry, rules: List<EntryRule>): List<EntryRule> {
        val link = entry.link ?: return emptyList()
        return getMatchingRulesForLink(link, rules)
    }
}
