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
     * Returns a formatted date string or "N/A" if null.
     */
    fun getFormattedDate(date: String?): String {
        return date ?: "N/A"
    }
}
