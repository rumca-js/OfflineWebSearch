package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.RepositoryTestHelper
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileToDatabaseTest {

    private lateinit var context: Context
    private lateinit var dbState: DatabaseState
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val (state, file) = RepositoryTestHelper.setup(context)
        dbState = state
        dbFile = file
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun testIsSupported() {
        assertTrue(FileToDatabase.isSupported("https://example.com/data.json"))
        assertTrue(FileToDatabase.isSupported("https://example.com/feeds.opml"))
        assertTrue(FileToDatabase.isSupported("DATA.JSON"))
        assertTrue(FileToDatabase.isSupported("FEEDS.OPML"))
        assertFalse(FileToDatabase.isSupported("https://example.com/data.db"))
        assertFalse(FileToDatabase.isSupported("https://example.com/data.txt"))
    }

    @Test
    fun testImportJsonFile() {
        val jsonContent = """
            [
                {
                    "id": 1,
                    "link": "https://example.com/article1",
                    "title": "Article One",
                    "description": "Desc One"
                }
            ]
        """.trimIndent()

        val jsonFile = File(context.cacheDir, "test_entries.json")
        jsonFile.writeText(jsonContent)

        try {
            FileToDatabase.importToDatabase(jsonFile, dbFile)

            val count = runBlocking { EntryRepository.countEntries(context, dbState) }
            assertTrue(count >= 1)
        } finally {
            jsonFile.delete()
        }
    }

    @Test
    fun testImportOpmlFile() {
        val opmlContent = """
            <opml version="1.0">
                <head><title>Test Feeds</title></head>
                <body>
                    <outline text="Tech News" type="rss" xmlUrl="https://technews.example.com/rss" htmlUrl="https://technews.example.com"/>
                </body>
            </opml>
        """.trimIndent()

        val opmlFile = File(context.cacheDir, "test_feeds.opml")
        opmlFile.writeText(opmlContent)

        try {
            FileToDatabase.importToDatabase(opmlFile, dbFile)

            val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
            val match = sources.find { it.source.url == "https://technews.example.com/rss" }
            assertNotNull(match)
            assertEquals("Tech News", match!!.source.title)
        } finally {
            opmlFile.delete()
        }
    }

    @Test
    fun testImportBytesForJsonAndOpml() {
        val jsonContent = """
            [
                {
                    "id": 2,
                    "link": "https://example.com/article2",
                    "title": "Article Two"
                }
            ]
        """.trimIndent()
        FileToDatabase.importToDatabase(jsonContent.toByteArray(), "test.json", dbFile)

        val count = runBlocking { EntryRepository.countEntries(context, dbState) }
        assertTrue(count >= 1)

        val opmlContent = """
            <opml version="1.0">
                <head><title>Bytes Feeds</title></head>
                <body>
                    <outline text="Science News" type="rss" xmlUrl="https://science.example.com/rss"/>
                </body>
            </opml>
        """.trimIndent()
        FileToDatabase.importToDatabase(opmlContent.toByteArray(), "https://example.com/science.opml", dbFile)

        val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
        assertTrue(sources.any { it.source.url == "https://science.example.com/rss" })
    }

    @Test
    fun testUnsupportedExtensionThrowsException() {
        val dummyFile = File(context.cacheDir, "test.unsupported")
        dummyFile.writeText("dummy")
        try {
            FileToDatabase.importToDatabase(dummyFile, dbFile)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported") == true)
        } finally {
            dummyFile.delete()
        }
    }
}
