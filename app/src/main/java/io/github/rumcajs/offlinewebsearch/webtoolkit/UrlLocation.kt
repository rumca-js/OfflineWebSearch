package io.github.rumcajs.offlinewebsearch.webtoolkit

import java.net.URI

class UrlLocation(private val link: String?) {

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
     * Strips leading http://, https://, or ftp:// from the given link (case-insensitive).
     */
    fun getProtocolles(): String {
        if (link.isNullOrBlank()) return ""
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
            trimmed.startsWith("ftp://", ignoreCase = true) -> trimmed.substring(6)
            else -> trimmed
        }
    }
}