package io.github.rumcajs.offlinewebsearch.data.repositories

/**
 * Translates a user-facing search query for sources into a structured [ParsedQuery].
 *
 * Supported syntax (evaluated in order):
 *  - `field==value`  → exact match  (e.g. `title==youtube`, `url==https://...`)
 *  - `field=value`   → LIKE / contains match (e.g. `title=youtube`)
 *  - `field LIKE '%value%'` → LIKE / contains match (SQLite-style literal)
 *  - anything else   → full-text contains search across title and url
 *
 * Recognised field names: `title`, `url`, `source_type`, `language`, `auto_tag`, `id`.
 */
object SourceSearchQueryTranslator {

    // Regex: field==value (exact match operator)
    private val exactRegex = Regex(
        """^(title|url|source_type|language|auto_tag|id)\s*==\s*['"]?([^'"]+?)['"]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Regex: field=value (LIKE / contains operator — PROJECT.md: "=" acts like LIKE)
    private val likeEqRegex = Regex(
        """^(title|url|source_type|language|auto_tag|id)\s*=\s*['"]?([^'"]+?)['"]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Regex: field LIKE '%value%' (explicit SQLite LIKE syntax)
    private val likeSqlRegex = Regex(
        """^(title|url|source_type|language|auto_tag|id)\s+LIKE\s+['"]?%?([^%'"]+)%?['"]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses [rawQuery] and returns a [ParsedQuery] describing how to filter sources.
     * Returns [ParsedQuery.FullText] when [rawQuery] is blank or unrecognised.
     */
    fun parse(rawQuery: String): ParsedQuery {
        if (rawQuery.isBlank()) return ParsedQuery.FullText("")
        val query = rawQuery.trim()

        // 1. Exact match: field==value (must be checked before single-= to avoid ambiguity)
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

        // 4. Fallback: full-text search across default fields (title, url)
        return ParsedQuery.FullText(query)
    }
}
