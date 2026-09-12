package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryUtilsTest {

    @Test
    fun testDisplayTitleObfuscation() {
        val entryWithAge = Entry(
            title = "Adult Content",
            age = 18
        )
        val entryWithoutAge = Entry(
            title = "General Content",
            age = 0
        )
        val entryNullAge = Entry(
            title = "Unknown Age",
            age = null
        )

        // Case 1: User is younger than requirement
        assertEquals("xXx", EntryUtils.getDisplayTitle(entryWithAge, 10))
        
        // Case 2: User meets requirement exactly
        assertEquals("Adult Content", EntryUtils.getDisplayTitle(entryWithAge, 18))
        
        // Case 3: User is older than requirement
        assertEquals("Adult Content", EntryUtils.getDisplayTitle(entryWithAge, 21))

        // Case 4: No age requirement
        assertEquals("General Content", EntryUtils.getDisplayTitle(entryWithoutAge, 10))
        assertEquals("Unknown Age", EntryUtils.getDisplayTitle(entryNullAge, 10))
    }

    @Test
    fun testDescriptionObfuscation() {
        val restrictedEntry = Entry(
            description = "Secret content",
            age = 18
        )
        val normalEntry = Entry(
            description = "Public content",
            age = 0
        )

        // Restricted
        assertEquals("xXx", EntryUtils.getDisplayDescription(restrictedEntry, 10))
        
        // Allowed
        assertEquals("Secret content", EntryUtils.getDisplayDescription(restrictedEntry, 20))
        assertEquals("Public content", EntryUtils.getDisplayDescription(normalEntry, 10))
        
        // Null handling
        assertEquals(null, EntryUtils.getDisplayDescription(
            Entry(description = null), 10))
    }

    @Test
    fun testMetadataFormatting() {
        val place = Entry(
            page_rating = 85,
            page_rating_votes = 120,
            date_created = "2023-01-01",
            date_published = null
        )

        assertEquals("85", EntryUtils.getFormattedRating(place))
        assertEquals("120", EntryUtils.getFormattedVotes(place))
        assertEquals("2023-01-01", EntryUtils.getFormattedDate(place.date_created))
        assertEquals("N/A", EntryUtils.getFormattedDate(place.date_published))
    }

    @Test
    fun testIsDead() {
        // Not dead by default
        val aliveEntry = Entry(title = "Alive", date_dead_since = null, manual_status_code = 0)
        assertEquals(false, EntryUtils.isDead(aliveEntry))

        // Dead when date_dead_since is set and manual_status_code is not 200
        val deadEntry = Entry(title = "Dead", date_dead_since = "2024-01-01", manual_status_code = 404)
        assertEquals(true, EntryUtils.isDead(deadEntry))

        // NOT dead when manual_status_code is 200 (STATUS_CODE_OK) even if date_dead_since is set
        val revivedEntry = Entry(title = "Revived", date_dead_since = "2024-01-01", manual_status_code = Entry.STATUS_CODE_OK)
        assertEquals(false, EntryUtils.isDead(revivedEntry))

        val manual200Entry = Entry(title = "Manual 200", date_dead_since = null, manual_status_code = 200)
        assertEquals(false, EntryUtils.isDead(manual200Entry))
    }
}
