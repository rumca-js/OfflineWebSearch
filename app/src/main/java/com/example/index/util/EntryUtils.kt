package com.example.index.util

import com.example.index.data.Place

object EntryUtils {
    /**
     * Returns true if the content should be restricted based on age.
     */
    fun isRestricted(place: Place, userAge: Int): Boolean {
        return (place.age ?: 0) > userAge
    }

    /**
     * Returns the title to display. Obfuscates as "xXx" if restricted.
     */
    fun getDisplayTitle(place: Place, userAge: Int): String {
        return if (isRestricted(place, userAge)) {
            "xXx"
        } else {
            place.title ?: "No Title"
        }
    }

    /**
     * Returns the description to display. Obfuscates as "xXx" if restricted.
     */
    fun getDisplayDescription(place: Place, userAge: Int): String? {
        val description = place.description ?: return null
        return if (isRestricted(place, userAge)) {
            "xXx"
        } else {
            description
        }
    }
    fun getFormattedRating(place: Place): String {
        return (place.page_rating ?: 0).toString()
    }

    /**
     * Returns a formatted votes string.
     */
    fun getFormattedVotes(place: Place): String {
        return (place.page_rating_votes ?: 0).toString()
    }

    /**
     * Returns a formatted date string or "N/A" if null.
     */
    fun getFormattedDate(date: String?): String {
        return date ?: "N/A"
    }
}
