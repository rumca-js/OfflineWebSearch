package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.builders.DefaultDatabaseBuilder
import io.github.rumcajs.offlinewebsearch.data.builders.InternetDatabaseBuilder
import io.github.rumcajs.offlinewebsearch.data.builders.LocalDatabaseBuilder
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRepository
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
class DatabaseBuildersTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppConfigManager.initialize(context)
    }

    @After
    fun tearDown() {
        // Clean up created files in filesDir
        context.filesDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun testLocalDatabaseBuilderFromAsset() = runBlocking {
        val builder = LocalDatabaseBuilder.fromAsset(
            context = context,
            customName = "my_custom_db.db"
        )
        val state = builder.build()

        assertEquals(DatabaseStatus.READY, state.status)
        assertEquals("local://my_custom_db.db", state.url)
        assertEquals("my_custom_db.db", state.localFileName)
        assertFalse(state.isReadOnly)
        assertTrue(state.isSQLite)

        val file = File(context.filesDir, "my_custom_db.db")
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun testLocalDatabaseBuilderFromJsonBytes() = runBlocking {
        val sampleJson = """
            [
                {
                    "id": 101,
                    "link": "https://example.org/item1",
                    "title": "Item One",
                    "description": "First description",
                    "page_rating_votes": 5,
                    "tags": ["alpha", "beta"]
                },
                {
                    "id": 102,
                    "link": "https://example.org/item2",
                    "title": "Item Two",
                    "description": "Second description",
                    "page_rating_votes": 15,
                    "tags": ["gamma"]
                }
            ]
        """.trimIndent()

        val url = "local://places_sample.json"
        val builder = LocalDatabaseBuilder.fromBytes(
            context = context,
            url = url,
            content = sampleJson.toByteArray(Charsets.UTF_8)
        )

        val state = builder.build()
        assertEquals(DatabaseStatus.READY, state.status)
        assertFalse(state.isReadOnly)
        assertTrue(state.isSQLite)
        assertTrue(state.localFileName.endsWith(".db"))

        val dbFile = File(context.filesDir, state.localFileName)
        assertTrue(dbFile.exists())

        // Verify entries were populated into SQLite linkdatamodel and entrycompactedtags
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT id, title, page_rating_votes FROM linkdatamodel ORDER BY id ASC", null)
        assertTrue(cursor.moveToFirst())
        assertEquals(101L, cursor.getLong(0))
        assertEquals("Item One", cursor.getString(1))
        assertEquals(5, cursor.getInt(2))

        assertTrue(cursor.moveToNext())
        assertEquals(102L, cursor.getLong(0))
        assertEquals("Item Two", cursor.getString(1))
        assertEquals(15, cursor.getInt(2))
        cursor.close()

        val tagCursor = db.rawQuery("SELECT tag FROM entrycompactedtags WHERE entry_id = 101 ORDER BY tag ASC", null)
        assertTrue(tagCursor.moveToFirst())
        assertEquals("alpha", tagCursor.getString(0))
        assertTrue(tagCursor.moveToNext())
        assertEquals("beta", tagCursor.getString(0))
        tagCursor.close()
        db.close()

        // Also verify EntryRepository can query this database
        val count = EntryRepository.countEntries(context, state)
        assertEquals(2, count)

        val entries = EntryRepository.getEntries(context, state)
        assertEquals(2, entries.size)
        assertEquals("Item Two", entries[0].title) // Default order is votes desc: 15 > 5
        assertEquals("Item One", entries[1].title)
    }

    @Test
    fun testLocalDatabaseBuilderDuplication() = runBlocking {
        // First create a database
        val initialBuilder = LocalDatabaseBuilder.fromAsset(context, "source_db.db")
        val initialState = initialBuilder.build()

        // Duplicate it
        val duplicateBuilder = LocalDatabaseBuilder.duplicate(context, initialState)
        val duplicateState = duplicateBuilder.build()

        assertEquals(DatabaseStatus.READY, duplicateState.status)
        assertNotEquals(initialState.url, duplicateState.url)
        assertNotEquals(initialState.localFileName, duplicateState.localFileName)
        assertTrue(File(context.filesDir, duplicateState.localFileName).exists())
    }

    @Test
    fun testDefaultDatabaseBuilder() = runBlocking {
        val builder = DefaultDatabaseBuilder(
            context = context,
            assetList = listOf("places_0.json"),
            forceRebuild = true
        )
        val state = builder.build()

        assertEquals(DatabaseStatus.READY, state.status)
        assertEquals(DEFAULT_DATABASE_FILE, state.localFileName)
        assertFalse(state.isReadOnly)
        assertTrue(File(context.filesDir, DEFAULT_DATABASE_FILE).exists())

        // Verify that EntryRepository works when activeDatabaseState is null (default database)
        val count = EntryRepository.countEntries(context, null)
        assertTrue(count > 0)
    }

    @Test
    fun testLocalDatabaseBuilderFromOpmlBytes() = runBlocking {
        val opmlContent = """
            <opml version="1.0">
                <head><title>My OPML</title></head>
                <body>
                    <outline text="My Blog" type="rss" xmlUrl="https://myblog.example.com/rss"/>
                </body>
            </opml>
        """.trimIndent()

        val url = "local://feeds_sample.opml"
        val builder = LocalDatabaseBuilder.fromBytes(
            context = context,
            url = url,
            content = opmlContent.toByteArray(Charsets.UTF_8)
        )

        val state = builder.build()
        assertEquals(DatabaseStatus.READY, state.status)
        assertFalse(state.isReadOnly)
        assertTrue(state.isSQLite)
        assertTrue(state.localFileName.endsWith(".db"))

        val sources = io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository.getAllSourcesWithOperationalData(context, state)
        assertEquals(1, sources.size)
        assertEquals("My Blog", sources[0].source.title)
        assertEquals("https://myblog.example.com/rss", sources[0].source.url)
    }

    @Test
    fun testInternetDatabaseBuilderFailsWhenNetworkDisabled() = runBlocking {
        AppConfigManager.setNetworkDisabled(true)

        val builder = InternetDatabaseBuilder(
            context = context,
            url = "https://example.com/test.db"
        )

        try {
            builder.build()
            fail("Expected exception when network is disabled")
        } catch (e: Exception) {
            assertEquals(DatabaseStatus.FAILED, builder.currentStatus)
        } finally {
            AppConfigManager.setNetworkDisabled(false)
        }
    }
}
