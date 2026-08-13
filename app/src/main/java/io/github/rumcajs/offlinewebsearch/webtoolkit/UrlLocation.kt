package io.github.rumcajs.offlinewebsearch.webtoolkit

import java.net.URI

class UrlLocation(private val link: String?) {

    companion object {
        /** Protocols recognised as valid web link prefixes (case-insensitive). */
        val VALID_PREFIXES = listOf("http://", "https://", "smb://", "ftp://")
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
     * - Contains exactly one dot.
     * - Contains only letters, digits, hyphens, and dots (no `&`, `?`, `=`, spaces, etc.).
     * - Neither part around the dot is empty.
     */
    private fun isValidDomain(domain: String): Boolean {
        if (domain.count { it == '.' } != 1) return false
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