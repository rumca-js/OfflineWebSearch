package io.github.rumcajs.offlinewebsearch

import io.github.rumcajs.offlinewebsearch.data.DatabasePreset
import io.github.rumcajs.offlinewebsearch.data.DatabasePresetRepository
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabasePresetTest {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testParsePresetListJson() {
        val json = """
            [
              {
                "url" : "https://rumca-js.github.io/data/feeds.db.zip",
                "title" : "Feeds"
              },
              {
                "url" : "https://rumca-js.github.io/data/top.db.zip",
                "title" : "Top domains"
              },
              {
                "url" : "https://github.com/rumca-js/rumca-js.github.io/releases/download/1.0.0/youtube.db.zip",
                "title" : "YouTube channels"
              }
            ]
        """.trimIndent()

        val presets = jsonConfig.decodeFromString<List<DatabasePreset>>(json)
        assertEquals(3, presets.size)
        assertEquals("https://rumca-js.github.io/data/feeds.db.zip", presets[0].url)
        assertEquals("Feeds", presets[0].title)
        assertEquals(null, presets[0].dateUpdated)
        assertEquals("Top domains", presets[1].title)
        assertEquals("YouTube channels", presets[2].title)
    }

    @Test
    fun testParsePresetWithDateUpdated() {
        val json = """
            [
              {
                "url" : "https://rumca-js.github.io/data/feeds.db.zip",
                "title" : "Feeds",
                "date_updated": "2026-10-04T22:36:00Z"
              },
              {
                "url" : "https://rumca-js.github.io/data/memes.db.zip",
                "title" : "Memes",
                "date_updated": "2026-08-28T22:36:00Z"
              }
            ]
        """.trimIndent()

        val presets = jsonConfig.decodeFromString<List<DatabasePreset>>(json)
        assertEquals(2, presets.size)
        assertEquals("https://rumca-js.github.io/data/feeds.db.zip", presets[0].url)
        assertEquals("Feeds", presets[0].title)
        assertEquals("2026-10-04T22:36:00Z", presets[0].dateUpdated)

        assertEquals("https://rumca-js.github.io/data/memes.db.zip", presets[1].url)
        assertEquals("2026-08-28T22:36:00Z", presets[1].dateUpdated)
    }

    @Test
    fun testParsePresetWithMissingOrExtraFields() {
        val json = """
            [
              {
                "url" : "https://rumca-js.github.io/data/custom.db.zip",
                "extraField" : 123
              },
              {
                "url" : "invalid-url",
                "title" : "Invalid URL"
              }
            ]
        """.trimIndent()

        val presets = jsonConfig.decodeFromString<List<DatabasePreset>>(json)
        assertEquals(2, presets.size)
        assertEquals("https://rumca-js.github.io/data/custom.db.zip", presets[0].url)
        assertEquals(null, presets[0].title)
        assertEquals(null, presets[0].dateUpdated)

        val filtered = presets.filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
        assertEquals(1, filtered.size)
        assertEquals("https://rumca-js.github.io/data/custom.db.zip", filtered[0].url)
    }

    @Test
    fun testIsOutdatedComparison() {
        // When dateLastRefresh is before dateUpdated -> outdated
        assertTrue(
            DatabasePresetRepository.isOutdated(
                dateLastRefresh = "2026-10-01T12:00:00Z",
                dateUpdated = "2026-10-04T22:36:00Z"
            )
        )

        // When dateLastRefresh is equal to dateUpdated -> not outdated
        assertFalse(
            DatabasePresetRepository.isOutdated(
                dateLastRefresh = "2026-10-04T22:36:00Z",
                dateUpdated = "2026-10-04T22:36:00Z"
            )
        )

        // When dateLastRefresh is newer than dateUpdated -> not outdated
        assertFalse(
            DatabasePresetRepository.isOutdated(
                dateLastRefresh = "2026-10-05T10:00:00Z",
                dateUpdated = "2026-10-04T22:36:00Z"
            )
        )

        // When dateLastRefresh is null and dateUpdated is present -> outdated
        assertTrue(
            DatabasePresetRepository.isOutdated(
                dateLastRefresh = null,
                dateUpdated = "2026-10-04T22:36:00Z"
            )
        )

        // When dateUpdated is null -> not outdated
        assertFalse(
            DatabasePresetRepository.isOutdated(
                dateLastRefresh = "2026-10-01T12:00:00Z",
                dateUpdated = null
            )
        )

        // When both are null -> not outdated
        assertFalse(
            DatabasePresetRepository.isOutdated(
                dateLastRefresh = null,
                dateUpdated = null
            )
        )
    }

    @Test
    fun testIsDatabaseOutdatedWithPresetsMap() {
        val presetsMap = mapOf(
            "https://rumca-js.github.io/data/feeds.db.zip" to DatabasePreset(
                url = "https://rumca-js.github.io/data/feeds.db.zip",
                title = "Feeds",
                dateUpdated = "2026-10-04T22:36:00Z"
            ),
            "https://rumca-js.github.io/data/top.db.zip" to DatabasePreset(
                url = "https://rumca-js.github.io/data/top.db.zip",
                title = "Top domains",
                dateUpdated = null
            )
        )

        // Outdated database
        assertTrue(
            DatabasePresetRepository.isDatabaseOutdated(
                url = "https://rumca-js.github.io/data/feeds.db.zip",
                dateLastRefresh = "2026-09-01T00:00:00Z",
                presetsMap = presetsMap
            )
        )

        // Fresh database
        assertFalse(
            DatabasePresetRepository.isDatabaseOutdated(
                url = "https://rumca-js.github.io/data/feeds.db.zip",
                dateLastRefresh = "2026-10-05T00:00:00Z",
                presetsMap = presetsMap
            )
        )

        // Preset has no dateUpdated
        assertFalse(
            DatabasePresetRepository.isDatabaseOutdated(
                url = "https://rumca-js.github.io/data/top.db.zip",
                dateLastRefresh = "2026-09-01T00:00:00Z",
                presetsMap = presetsMap
            )
        )

        // Unknown database URL
        assertFalse(
            DatabasePresetRepository.isDatabaseOutdated(
                url = "https://example.com/custom.db",
                dateLastRefresh = "2026-09-01T00:00:00Z",
                presetsMap = presetsMap
            )
        )
    }
}
