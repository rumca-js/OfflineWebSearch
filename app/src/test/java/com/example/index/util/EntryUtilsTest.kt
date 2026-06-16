package com.example.index.util

import com.example.index.data.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryUtilsTest {

    @Test
    fun testDisplayTitleObfuscation() {
        val placeWithAge = Place(title = "Adult Content", age = 18)
        val placeWithoutAge = Place(title = "General Content", age = 0)
        val placeNullAge = Place(title = "Unknown Age", age = null)

        // Case 1: User is younger than requirement
        assertEquals("xXx", EntryUtils.getDisplayTitle(placeWithAge, 10))
        
        // Case 2: User meets requirement exactly
        assertEquals("Adult Content", EntryUtils.getDisplayTitle(placeWithAge, 18))
        
        // Case 3: User is older than requirement
        assertEquals("Adult Content", EntryUtils.getDisplayTitle(placeWithAge, 21))

        // Case 4: No age requirement
        assertEquals("General Content", EntryUtils.getDisplayTitle(placeWithoutAge, 10))
        assertEquals("Unknown Age", EntryUtils.getDisplayTitle(placeNullAge, 10))
    }

    @Test
    fun testDescriptionObfuscation() {
        val restrictedPlace = Place(description = "Secret content", age = 18)
        val normalPlace = Place(description = "Public content", age = 0)

        // Restricted
        assertEquals("xXx", EntryUtils.getDisplayDescription(restrictedPlace, 10))
        
        // Allowed
        assertEquals("Secret content", EntryUtils.getDisplayDescription(restrictedPlace, 20))
        assertEquals("Public content", EntryUtils.getDisplayDescription(normalPlace, 10))
        
        // Null handling
        assertEquals(null, EntryUtils.getDisplayDescription(Place(description = null), 10))
    }

    @Test
    fun testMetadataFormatting() {
        val place = Place(
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
}
