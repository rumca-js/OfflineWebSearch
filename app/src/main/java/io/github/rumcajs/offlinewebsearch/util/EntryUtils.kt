package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository

object EntryUtils {
    /**
     * Returns true if the entry is considered dead.
     * An entry is not marked dead if manual_status_code is 200 (STATUS_CODE_OK).
     */
    fun isDead(entry: Entry): Boolean {
        if (entry.manual_status_code == Entry.STATUS_CODE_OK) {
            return false
        }
        return !entry.date_dead_since.isNullOrBlank()
    }

    /**
     * Returns true if the content should be restricted based on age.
     */
    fun isRestricted(entry: Entry, userAge: Int): Boolean {
        return (entry.age ?: 0) > userAge
    }

    /**
     * Returns the title to display. Obfuscates as "xXx" if restricted.
     */
    fun getDisplayTitle(entry: Entry, userAge: Int): String {
        return if (isRestricted(entry, userAge)) {
            "xXx"
        } else {
            entry.title ?: "No Title"
        }
    }

    /**
     * Returns the description to display. Obfuscates as "xXx" if restricted.
     */
    fun getDisplayDescription(entry: Entry, userAge: Int): String? {
        val description = entry.description ?: return null
        return if (isRestricted(entry, userAge)) {
            "xXx"
        } else {
            description
        }
    }

    /**
     * Returns the author or source name to display for an entry.
     *
     * Priority:
     * 1. [entry.author] if non-blank (e.g. email sender from the "From" header).
     * 2. Source title resolved via [source_id] from `sourcedatamodel`.
     * 3. Raw [source_id] string as last resort.
     *
     * Checking [entry.author] first ensures that email entries show the actual
     * sender instead of the email source's title.
     */
    suspend fun getDisplayAuthor(
        entry: Entry,
        context: android.content.Context? = null,
        activeDatabaseState: io.github.rumcajs.offlinewebsearch.data.DatabaseState? = null
    ): String? {
        // Prefer an explicitly set author (e.g. email "From" field) over source metadata.
        entry.author?.takeIf { it.isNotBlank() }?.let { return it }

        val sourceId = entry.source_id ?: return null
        if (context != null && activeDatabaseState != null) {
            val sourceTitle = SourceRepository.getSourceById(
                context,
                activeDatabaseState,
                sourceId
            )?.title?.takeIf { it.isNotBlank() }
            if (!sourceTitle.isNullOrBlank()) {
                return sourceTitle
            }
        }
        return sourceId.toString()
    }


    fun getFormattedRating(entry: Entry): String {
        return (entry.page_rating ?: 0).toString()
    }

    /**
     * Returns a formatted votes string.
     */
    fun getFormattedVotes(entry: Entry): String {
        return (entry.page_rating_votes ?: 0).toString()
    }

    /**
     * Returns a formatted visits string.
     */
    fun getFormattedVisits(entry: Entry): String {
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
