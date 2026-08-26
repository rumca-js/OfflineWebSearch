package io.github.rumcajs.offlinewebsearch.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility functions for formatting and parsing dates and ISO 8601 timestamps.
 */
object DateUtils {

    /**
     * Generates a current UTC ISO 8601 timestamp string (e.g. "2026-08-26T10:49:00Z").
     */
    fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Parses an ISO 8601 UTC timestamp string to epoch milliseconds, or null on error.
     */
    fun parseIsoTimestamp(timestamp: String?): Long? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(timestamp)?.time
        } catch (e: Exception) {
            try {
                // Fallback for timestamps with different formatting
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(timestamp)?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Attempts to parse a date string into a [Date] using a sequence of common
     * ISO 8601 and RFC 2822 formats found in HTML meta tags and RSS feeds.
     * Returns null if the string cannot be parsed by any known format.
     */
    fun parseDateString(text: String): Date? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss zzz"
        )
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.US).apply { isLenient = false }.parse(text)
            } catch (_: Exception) { }
        }
        return null
    }

    /**
     * Formats a [Date] as a UTC ISO 8601 timestamp string (e.g. "2026-08-26T10:49:00Z").
     */
    fun toIsoString(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
