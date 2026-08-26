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
}
