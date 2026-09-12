package io.github.rumcajs.offlinewebsearch.util

/**
 * Utility functions for tag handling.
 */
object TagUtils {

    /**
     * Parses a comma-separated tags input string into a list of tag strings.
     *
     * Each element is trimmed of surrounding whitespace and converted to lowercase.
     * Empty elements (after trimming) are discarded.
     *
     * @param input Raw user input, e.g. "Kotlin, Android , JAVA"
     * @return Parsed list, e.g. ["kotlin", "android", "java"]
     */
    fun parseTagsInput(input: String): List<String> =
        input.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

    /**
     * Normalises a comma-separated auto-tag string.
     *
     * Applies the same rules as [parseTagsInput] (trim, lowercase, drop blanks)
     * and then rejoins the result with ", " so it can be stored as a single string.
     *
     * @param input Raw auto-tag value, e.g. "Kotlin, Android , JAVA"
     * @return Normalised string, e.g. "kotlin, android, java"
     */
    fun normalizeAutoTag(input: String): String =
        parseTagsInput(input).joinToString(", ")
}
