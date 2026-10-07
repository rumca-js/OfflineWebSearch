package io.github.rumcajs.offlinewebsearch.util

/**
 * Utility for unescaping HTML entities and cleaning HTML markup from strings.
 *
 * Safe for use in both Android runtime and JVM unit tests (no Android platform dependencies).
 */
object HtmlUtils {

    private val ENTITY_REGEX = Regex("&(#?[xX]?[0-9a-zA-Z]+);")
    private val TAG_REGEX = Regex("<[^>]+>")
    private val SCRIPT_REGEX = Regex("(?is)<script[\\s\\S]*?</script>")
    private val STYLE_REGEX = Regex("(?is)<style[\\s\\S]*?</style>")
    private val BR_REGEX = Regex("(?i)<br\\s*/?>")
    private val BLOCK_END_REGEX = Regex("(?i)</(?:p|div|h[1-6]|li|tr|blockquote|section|article)>")
    private val HR_REGEX = Regex("(?i)<hr\\s*/?>")
    private val MULTI_NEWLINE_REGEX = Regex("\n{3,}")
    private val MULTI_SPACE_REGEX = Regex("[ \\t]+")

    private val NAMED_ENTITIES: Map<String, String> = mapOf(
        "quot" to "\"",
        "amp" to "&",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "nbsp" to " ",
        "ensp" to " ",
        "emsp" to " ",
        "thinsp" to " ",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "sbquo" to "‚",
        "bdquo" to "„",
        "ndash" to "–",
        "mdash" to "—",
        "hellip" to "…",
        "bull" to "•",
        "middot" to "·",
        "prime" to "′",
        "Prime" to "″",
        "laquo" to "«",
        "raquo" to "»",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "sect" to "§",
        "para" to "¶",
        "deg" to "°",
        "plusmn" to "±",
        "times" to "×",
        "divide" to "÷",
        "cent" to "¢",
        "pound" to "£",
        "euro" to "€",
        "yen" to "¥"
    )

    /**
     * Unescapes HTML entities in [text], supporting named, decimal, and hexadecimal entities.
     * Performs a second pass to resolve double-escaped entities (e.g. `&amp;quot;` -> `"`).
     *
     * @param text The string containing HTML escape sequences.
     * @return The unescaped string.
     */
    fun unescapeHtml(text: String): String {
        if (!text.contains('&')) return text

        var result = decodeEntitiesOnce(text)
        // Perform a second pass if another entity was revealed by unescaping (e.g., &amp;quot;)
        if (result.contains('&')) {
            result = decodeEntitiesOnce(result)
        }
        return result
    }

    /**
     * Decodes all matched entities in a single pass.
     */
    private fun decodeEntitiesOnce(text: String): String {
        return ENTITY_REGEX.replace(text) { matchResult ->
            val entityContent = matchResult.groupValues[1]
            resolveEntity(entityContent) ?: matchResult.value
        }
    }

    /**
     * Resolves a single entity body (without the leading '&' and trailing ';') to its character representation.
     */
    private fun resolveEntity(content: String): String? {
        if (content.startsWith("#x", ignoreCase = true)) {
            val hex = content.substring(2)
            return parseCodePoint(hex, radix = 16)
        }
        if (content.startsWith("#")) {
            val dec = content.substring(1)
            return parseCodePoint(dec, radix = 10)
        }
        return NAMED_ENTITIES[content] ?: NAMED_ENTITIES[content.lowercase()]
    }

    /**
     * Parses a unicode codepoint string in the given [radix] and returns it as a string.
     */
    private fun parseCodePoint(value: String, radix: Int): String? {
        val codePoint = value.toIntOrNull(radix) ?: return null
        if (!Character.isValidCodePoint(codePoint)) return null
        return String(Character.toChars(codePoint))
    }

    /**
     * Strips HTML markup from [html], converting block-level tags and `<br>` into appropriate
     * line breaks, unescaping HTML entities, and trimming whitespace.
     *
     * @param html The HTML string to convert to plain text.
     * @return The plain text representation.
     */
    fun stripHtml(html: String): String {
        if (html.isBlank()) return ""

        var text = html
        text = SCRIPT_REGEX.replace(text, "")
        text = STYLE_REGEX.replace(text, "")
        text = BR_REGEX.replace(text, "\n")
        text = BLOCK_END_REGEX.replace(text, "\n\n")
        text = HR_REGEX.replace(text, "\n")
        text = TAG_REGEX.replace(text, "")
        text = unescapeHtml(text)

        // Normalize line breaks and spaces
        val lines = text.lines().map { MULTI_SPACE_REGEX.replace(it.trim(), " ") }
        text = lines.joinToString("\n")
        text = MULTI_NEWLINE_REGEX.replace(text, "\n\n")

        return text.trim()
    }

    /**
     * Cleans an entry or feed title:
     * - Strips any HTML tags
     * - Unescapes HTML entities (e.g. `&quot;`, `&amp;`, `&#39;`)
     * - Normalizes newlines and multiple spaces into a single space
     * - Trims leading and trailing whitespace
     *
     * @param title The raw title string, possibly containing HTML or entities.
     * @return Cleaned plain-text title, or null if empty/blank.
     */
    fun cleanTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null

        var text = TAG_REGEX.replace(title, "")
        text = unescapeHtml(text)
        text = text.replace('\n', ' ').replace('\r', ' ')
        text = MULTI_SPACE_REGEX.replace(text, " ").trim()

        return text.ifEmpty { null }
    }

    /**
     * Cleans an entry or feed description:
     * - Strips script and style tags
     * - Converts `<br>` and block tags to line breaks
     * - Strips all remaining HTML tags
     * - Unescapes HTML entities
     * - Trims whitespace
     *
     * @param description The raw description string, possibly containing HTML markup.
     * @return Cleaned plain-text description, or null if empty/blank.
     */
    fun cleanDescription(description: String?): String? {
        if (description.isNullOrBlank()) return null

        val cleaned = stripHtml(description)
        return cleaned.ifEmpty { null }
    }
}
