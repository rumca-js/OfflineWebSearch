package io.github.rumcajs.offlinewebsearch.data.repositories

/**
 * Translates a user-facing search query into a structured [ParsedQuery] that both
 * the SQLite and JSON backends can consume.
 *
 * Supported syntax (evaluated in order):
 *  - `field==value`  → exact match  (e.g. `title==youtube`)
 *  - `field=value`   → LIKE / contains match (e.g. `title=youtube`)
 *  - `field LIKE '%value%'` → LIKE / contains match (SQLite-style literal)
 *  - anything else   → full-text contains search across title, description, link, tag
 *
 * Recognised field names: `title`, `link`, `description`, `tag`, `tags`,
 * `source_id`, `source_url`, `source`.
 */
object EntrySearchQueryTranslator {

    // Regex: field==value  (exact match operator)
    private val exactRegex = Regex(
        """^(title|link|description|tag|tags|source_id|source_url|source)\s*==\s*['"]?([^'"]+?)['"]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Regex: field=value  (LIKE / contains operator — PROJECT.md: "=" acts like LIKE)
    private val likeEqRegex = Regex(
        """^(title|link|description|tag|tags|source_id|source_url|source)\s*=\s*['"]?([^'"]+?)['"]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Regex: field LIKE '%value%'  (explicit SQLite LIKE syntax)
    private val likeSqlRegex = Regex(
        """^(title|link|description|tag|tags|source_id|source_url|source)\s+LIKE\s+['"]?%?([^%'"]+)%?['"]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses [rawQuery] and returns a [ParsedQuery] describing how to filter entries.
     * Returns [ParsedQuery.FullText] when [rawQuery] is blank or unrecognised.
     */
    fun parse(rawQuery: String): ParsedQuery {
        if (rawQuery.isBlank()) return ParsedQuery.FullText("")
        val query = rawQuery.trim()

        // 1. Exact match: field==value  (must be checked before single-= to avoid ambiguity)
        exactRegex.find(query)?.let { m ->
            val field = m.groupValues[1].lowercase()
            val term = m.groupValues[2].trim()
            return ParsedQuery.FieldExact(field, term)
        }

        // 2. LIKE / contains: field=value
        likeEqRegex.find(query)?.let { m ->
            val field = m.groupValues[1].lowercase()
            val term = m.groupValues[2].trim()
            return ParsedQuery.FieldContains(field, term)
        }

        // 3. Explicit SQLite LIKE syntax: field LIKE '%value%'
        likeSqlRegex.find(query)?.let { m ->
            val field = m.groupValues[1].lowercase()
            val term = m.groupValues[2].trim()
            return ParsedQuery.FieldContains(field, term)
        }

        // 4. Fallback: full-text search across all default fields
        return ParsedQuery.FullText(query)
    }
}

/**
 * Represents the result of translating a raw search string.
 */
sealed class ParsedQuery {

    /**
     * Field-level LIKE / contains search.
     * @param field  Canonical (lowercase) field name, e.g. `"title"`.
     * @param term   The value to search for (without SQL wildcards).
     */
    data class FieldContains(val field: String, val term: String) : ParsedQuery()

    /**
     * Field-level exact equality search.
     * @param field  Canonical (lowercase) field name.
     * @param term   The exact value to match.
     */
    data class FieldExact(val field: String, val term: String) : ParsedQuery()

    /**
     * Full-text search across the default set of fields (title, description, link, tag).
     * @param term  Raw query text; blank means "no filter".
     */
    data class FullText(val term: String) : ParsedQuery()
}
