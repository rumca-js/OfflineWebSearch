package io.github.rumcajs.offlinewebsearch

import io.github.rumcajs.offlinewebsearch.data.DatabasePreset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
        assertEquals("Top domains", presets[1].title)
        assertEquals("YouTube channels", presets[2].title)
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

        val filtered = presets.filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
        assertEquals(1, filtered.size)
        assertEquals("https://rumca-js.github.io/data/custom.db.zip", filtered[0].url)
    }
}
