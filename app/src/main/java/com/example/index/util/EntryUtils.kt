package com.example.index.util

import com.example.index.data.Entry

object EntryUtils {
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
     * Returns a formatted date string or "N/A" if null.
     */
    fun getFormattedDate(date: String?): String {
        return date ?: "N/A"
    }
}
