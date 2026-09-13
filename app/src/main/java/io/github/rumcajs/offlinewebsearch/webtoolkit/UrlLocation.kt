package io.github.rumcajs.offlinewebsearch.webtoolkit

import java.net.URI

class UrlLocation(private val link: String?) {

    companion object {
        /** Protocols recognised as valid web link prefixes (case-insensitive). */
        val VALID_PREFIXES = listOf("http://", "https://", "smb://", "ftp://")

        /**
         * Normalizes a user-entered URL string:
         * - Adds a `https://` scheme prefix when none is present.
         * - Strips a trailing slash that immediately follows the host with no further path
         *   (e.g. `https://example.com/` → `https://example.com`).
         *   A slash that is part of an actual path (e.g. `/path/`) is preserved.
         *
         * @param raw The raw input string.
         * @return Normalized URL string.
         */
        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val withScheme = if (!trimmed.contains("://") && !trimmed.startsWith("//")) {
                "https://$trimmed"
            } else if (trimmed.startsWith("//")) {
                "https:$trimmed"
            } else {
                trimmed
            }
            // Strip a trailing slash only when it is directly after the host (no real path).
            // Pattern: scheme://host/ with nothing after the slash.
            val schemeEnd = withScheme.indexOf("://")
            if (schemeEnd != -1) {
                val afterScheme = withScheme.substring(schemeEnd + 3)
                val slashIndex = afterScheme.indexOf('/')
                if (slashIndex != -1 && slashIndex == afterScheme.length - 1) {
                    // The only slash is the very last character — strip it.
                    return withScheme.dropLast(1)
                }
            }
            return withScheme
        }

        /**
         * Strips query parameters (everything after '?') from a URL string,
         * preserving any trailing fragment if present.
         *
         * @param url The URL string to process.
         * @return URL string with query arguments removed.
         */
        fun clearUrlArgs(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return ""
            val questionMarkIndex = trimmed.indexOf('?')
            if (questionMarkIndex == -1) return trimmed
            val hashIndex = trimmed.indexOf('#', startIndex = questionMarkIndex)
            return if (hashIndex != -1) {
                trimmed.substring(0, questionMarkIndex) + trimmed.substring(hashIndex)
            } else {
                trimmed.substring(0, questionMarkIndex)
            }
        }
    }

    /**
     * Extracts the domain (host) from the given input link, removing the protocol.
     * For example, for "https://google.com" or "google.com" it returns "google.com".
     */
    fun getDomain(): String {
        if (link.isNullOrBlank()) return ""

        // Normalize URL protocol
        val trimmed = link.trim()
        val adjustedLink = if (!trimmed.contains("://") && !trimmed.startsWith("//")) {
            "http://$trimmed"
        } else if (trimmed.startsWith("//")) {
            "http:$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(adjustedLink)
            val host = uri.host
            if (host.isNullOrEmpty()) {
                fallbackExtractDomain(adjustedLink)
            } else {
                host
            }
        } catch (e: Exception) {
            fallbackExtractDomain(adjustedLink)
        }
    }

    private fun fallbackExtractDomain(link: String): String {
        var temp = link
        val schemeEnd = temp.indexOf("://")
        if (schemeEnd != -1) {
            temp = temp.substring(schemeEnd + 3)
        }
        val pathEnd = temp.indexOfAny(charArrayOf('/', '?', '#', ':'))
        if (pathEnd != -1) {
            temp = temp.substring(0, pathEnd)
        }
        return temp
    }

    /**
     * Strips the leading protocol from [link] using [VALID_PREFIXES] (case-insensitive).
     * Returns the link unchanged if no known prefix is found.
     */
    fun getProtocolles(): String {
        if (link.isNullOrBlank()) return ""
        val trimmed = link.trim()
        val matched = VALID_PREFIXES.firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
        return if (matched != null) trimmed.substring(matched.length) else trimmed
    }

    fun getFileName(): String {
        if (link.isNullOrBlank()) return ""

        return try {
            // Using standard URI to cleanly parse the path away from queries/fragments
            val path = URI(link).path ?: return ""

            // Get the substring after the last slash
            val fileName = path.substringAfterLast('/')

            // If the URL ends with a trailing slash, fileName will be empty
            fileName
        } catch (e: Exception) {
            // Fallback for malformed URLs: manually strip query parameters/fragments
            // and grab the last segment
            val cleanLink = link.substringBefore('?').substringBefore('#')
            cleanLink.substringAfterLast('/')
        }
    }

    /**
     * Returns true if [link] is a web link.
     *
     * A web link must:
     * - Start with one of the protocols in [VALID_PREFIXES] (case-insensitive).
     * - Have a domain that contains exactly one dot.
     * - Have a domain composed only of safe characters (letters, digits, hyphens, dots).
     */
    fun isWebLink(): Boolean {
        if (link.isNullOrBlank()) return false
        val trimmed = link.trim()
        val matchedPrefix = VALID_PREFIXES.firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
            ?: return false
        val host = extractHost(trimmed.substring(matchedPrefix.length))
        return isValidDomain(host)
    }

    /**
     * Extracts the host portion from the part of the URL that comes after the protocol.
     * Stops at the first '/', '?', '#', or ':' (port separator).
     */
    private fun extractHost(afterProtocol: String): String {
        val end = afterProtocol.indexOfAny(charArrayOf('/', '?', '#', ':'))
        return if (end != -1) afterProtocol.substring(0, end) else afterProtocol
    }

    /**
     * Returns true if [domain] looks like a valid hostname:
     * - Contains less than one dot
     * - Contains only letters, digits, hyphens, and dots (no `&`, `?`, `=`, spaces, etc.).
     * - Neither part around the dot is empty.
     */
    private fun isValidDomain(domain: String): Boolean {
        if (domain.count { it == '.' } < 1) return false
        if (!domain.all { it.isLetterOrDigit() || it == '-' || it == '.' }) return false
        val (left, right) = domain.split('.', limit = 2)
        return left.isNotEmpty() && right.isNotEmpty()
    }

    /**
     * Returns the domain portion of [link] without the leading protocol.
     *
     * For example:
     * - "https://www.google.com/search?q=foo" → "www.google.com"
     * - "ftp://files.example.org/pub"         → "files.example.org"
     * - "google.com"                          → "google.com"
     *
     * Delegates to [getDomain] which already strips the protocol.
     */
    fun getDomainOnly(): String = getDomain()
}