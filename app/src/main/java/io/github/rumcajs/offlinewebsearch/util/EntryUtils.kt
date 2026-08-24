package io.github.rumcajs.offlinewebsearch.util

object EntryUtils {
    /**
     * Returns true if the content should be restricted based on age.
     */
    fun isRestricted(entry: io.github.rumcajs.offlinewebsearch.data.Entry, userAge: Int): Boolean {
        return (entry.age ?: 0) > userAge
    }

    /**
     * Returns the title to display. Obfuscates as "xXx" if restricted.
     */
    fun getDisplayTitle(entry: io.github.rumcajs.offlinewebsearch.data.Entry, userAge: Int): String {
        return if (isRestricted(entry, userAge)) {
            "xXx"
        } else {
            entry.title ?: "No Title"
        }
    }

    /**
     * Returns the description to display. Obfuscates as "xXx" if restricted.
     */
    fun getDisplayDescription(entry: io.github.rumcajs.offlinewebsearch.data.Entry, userAge: Int): String? {
        val description = entry.description ?: return null
        return if (isRestricted(entry, userAge)) {
            "xXx"
        } else {
            description
        }
    }
    fun getFormattedRating(entry: io.github.rumcajs.offlinewebsearch.data.Entry): String {
        return (entry.page_rating ?: 0).toString()
    }

    /**
     * Returns a formatted votes string.
     */
    fun getFormattedVotes(entry: io.github.rumcajs.offlinewebsearch.data.Entry): String {
        return (entry.page_rating_votes ?: 0).toString()
    }

    /**
     * Returns a formatted visits string.
     */
    fun getFormattedVisits(entry: io.github.rumcajs.offlinewebsearch.data.Entry): String {
        return (entry.page_rating_visits ?: 0).toString()
    }

    /**
     * Formats integer/long counts into compact, human-readable strings (e.g. 1.2K, 3.4M, 1.5B).
     */
    fun formatCount(count: Long?): String {
        if (count == null) return "0"
        val abs = kotlin.math.abs(count)
        val sign = if (count < 0) "-" else ""
        return when {
            abs >= 1_000_000_000L -> {
                val value = abs / 1_000_000_000.0
                if (value >= 100) "${sign}${value.toLong()}B"
                else "${sign}${String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")}B"
            }
            abs >= 1_000_000L -> {
                val value = abs / 1_000_000.0
                if (value >= 100) "${sign}${value.toLong()}M"
                else "${sign}${String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")}M"
            }
            abs >= 1_000L -> {
                val value = abs / 1_000.0
                if (value >= 100) "${sign}${value.toLong()}K"
                else "${sign}${String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")}K"
            }
            else -> "$count"
        }
    }

    fun formatCount(count: Int?): String = formatCount(count?.toLong())

    /**
     * Returns a formatted date string or "N/A" if null.
     */
    fun getFormattedDate(date: String?): String {
        return date ?: "N/A"
    }
}
