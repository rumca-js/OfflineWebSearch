package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalDataRepository
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

/**
 * Unit tests for [io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository.insertSource].
 *
 * Uses Robolectric to provide an Android [Context] and [RepositoryTestHelper] to supply a
 * writable copy of `assets/table.db` for each test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceRepositoryTest {

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

    // ── insertSource: success cases ───────────────────────────────────────────

    @Test
    fun `insertSource returns success for valid input`() = runBlocking {
        val (ok, error) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Test Source",
            url = "https://example.com/feed.rss",
            enabled = true
        )
        assertTrue("Expected success but got: $error", ok)
        assertNull(error)
    }

    @Test
    fun `insertSource persists the new source in the database`() = runBlocking {
        val url = "https://persist-test.com/feed.rss"
        val title = "Persist Test"

        val (ok, _) = SourceRepository.insertSource(context, dbState, title, url, enabled = true)
        assertTrue(ok)

        val found = SourceRepository.getSourceByUrl(context, dbState, url)
        assertNotNull("Source should be findable by URL after insert", found)
        assertEquals(title, found!!.title)
        assertEquals(url, found.url)
        assertTrue(found.enabled)
    }

    @Test
    fun `insertSource with enabled false stores enabled as false`() = runBlocking {
        val url = "https://disabled-source.com/feed.rss"
        SourceRepository.insertSource(context, dbState, "Disabled Source", url, enabled = false)

        val found = SourceRepository.getSourceByUrl(context, dbState, url)
        assertNotNull(found)
        assertFalse("enabled should be false", found!!.enabled)
    }

    @Test
    fun `insertSource allows blank title`() = runBlocking {
        val (ok, error) = SourceRepository.insertSource(
            context, dbState, title = "", url = "https://notitle.example.com/rss", enabled = true
        )
        assertTrue("Insert with blank title should succeed: $error", ok)
    }

    @Test
    fun `insertSource allows blank url`() = runBlocking {
        val (ok, error) = SourceRepository.insertSource(
            context, dbState, title = "No URL source", url = "", enabled = true
        )
        assertTrue("Insert with blank url should succeed: $error", ok)
    }

    @Test
    fun `insertSource increments row count`() = runBlocking {
        val countBefore = SourceRepository.count(context, dbState)

        SourceRepository.insertSource(context, dbState, "Counter Test", "https://counter.test/rss", true)

        val countAfter = SourceRepository.count(context, dbState)
        assertEquals("Row count should increase by 1", countBefore + 1, countAfter)
    }

    // ── insertSource: guard cases ─────────────────────────────────────────────

    @Test
    fun `insertSource fails when database state is null`() = runBlocking {
        val (ok, error) = SourceRepository.insertSource(
            context, activeDatabaseState = null, title = "T", url = "https://x.com", enabled = true
        )
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `insertSource fails when database is read-only`() = runBlocking {
        val readOnlyState = dbState.copy(isReadOnly = true)
        val (ok, error) = SourceRepository.insertSource(
            context, readOnlyState, "T", "https://x.com", true
        )
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `insertSource fails when database file does not exist`() = runBlocking {
        val missingState = dbState.copy(localFileName = "nonexistent_db.db")
        val (ok, error) = SourceRepository.insertSource(
            context, missingState, "T", "https://x.com", true
        )
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `insertSource fails when database extension is not db`() = runBlocking {
        val jsonState = dbState.copy(localFileName = "some_db.json")
        val (ok, error) = SourceRepository.insertSource(
            context, jsonState, "T", "https://x.com", true
        )
        assertFalse(ok)
        assertNotNull(error)
    }

    // ── updateSourceMetaAndEntries: skip disabled & recent fetch ──────────────

    @Test
    fun `updateSourceMetaAndEntries skips disabled source`() = runBlocking {
        val disabledSource =
            Source(id = 1L, title = "Disabled", url = "https://example.com/rss", enabled = false)
        val (ok, reason) = SourceRepository.updateSourceMetaAndEntries(context, dbState, disabledSource)
        assertFalse(ok)
        assertEquals("Source is disabled", reason)
    }

    @Test
    fun `updateSourceMetaAndEntries skips source fetched less than an hour ago`() = runBlocking {
        val url = "https://recent.test/rss"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Recent Source", url, enabled = true)
        assertTrue(okInsert)
        val inserted = SourceRepository.getSourceByUrl(context, dbState, url)
        assertNotNull(inserted)

        // Set fetch timestamp to current time (less than 1 hour ago)
        val nowIso = SourceOperationalDataRepository.getCurrentIsoTimestamp()
        SourceOperationalDataRepository.setSourceFetch(context, dbState, inserted!!.id!!, nowIso)

        val (ok, reason) = SourceRepository.updateSourceMetaAndEntries(context, dbState, inserted)
        assertFalse(ok)
        assertEquals("Source was fetched recently (less than 1 hour ago)", reason)
    }

    @Test
    fun `getOperationalDataBySourceId reads all table columns`() = runBlocking {
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        SourceOperationalDataRepository.ensureTableExists(db)
        val sourceObjId = 9999L
        val values = android.content.ContentValues().apply {
            put("date_fetched", "2026-09-03T18:00:00Z")
            put("source_id", sourceObjId)
            put("import_seconds", 42)
            put("number_of_entries", 150)
            put("page_hash", byteArrayOf(1, 2, 3, 4))
            put("body_hash", byteArrayOf(5, 6, 7, 8))
            put("consecutive_errors", 3)
        }
        db.insert("sourceoperationaldata", null, values)
        db.close()

        val data = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, sourceObjId)
        assertNotNull(data)
        assertEquals("2026-09-03T18:00:00Z", data!!.date_fetched)
        assertEquals(sourceObjId, data.source_id)
        assertEquals(42, data.import_seconds)
        assertEquals(150, data.number_of_entries)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), data.page_hash)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), data.body_hash)
        assertEquals(3, data.consecutive_errors)
    }

    @Test
    fun `fetchAndInsertSourceEntries removes entries not present in new RSS feed`() = runBlocking {
        val sourceId = 77777L
        val sourceUrl = "https://unique-feed.com/feed.xml"
        val source = Source(id = sourceId, title = "Feed Source", url = sourceUrl, enabled = true)

        // Insert initial entries using insertSourceEntries
        val (okInit, countInit) = SourceRepository.insertSourceEntries(
            context,
            dbState,
            listOf(
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://unique-feed.com/outdated_item",
                    title = "Outdated Item"
                ),
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://unique-feed.com/kept_item",
                    title = "Kept Item"
                )
            ),
            source
        )
        assertTrue(okInit)
        assertEquals(2, countInit)

        val otherSource = Source(id = 99999L, title = "Other", url = "https://other.com/feed.xml", enabled = true)
        val (okOther, countOther) = SourceRepository.insertSourceEntries(
            context,
            dbState,
            listOf(
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://other.com/different_source_item",
                    title = "Other Source Item"
                )
            ),
            otherSource
        )
        assertTrue(okOther)
        assertEquals(1, countOther)

        // Create a fake Url returning RSS containing kept_item and new_item (but NOT outdated_item)
        val rssXml = """
            <rss version="2.0">
              <channel>
                <title>Feed Source</title>
                <link>https://unique-feed.com</link>
                <item>
                  <title>Kept Item</title>
                  <link>https://unique-feed.com/kept_item</link>
                </item>
                <item>
                  <title>New Item</title>
                  <link>https://unique-feed.com/new_item</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val fakeUrl = object : io.github.rumcajs.offlinewebsearch.webtoolkit.Url(sourceUrl) {
            override suspend fun getResponse(acceptHeader: String?): io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to listOf("application/rss+xml"))
                )
            }
            override suspend fun getPage(): io.github.rumcajs.offlinewebsearch.webtoolkit.Page {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage(sourceUrl, rssXml)
            }
        }

        val (ok, _) = SourceRepository.fetchAndInsertSourceEntries(context, dbState, fakeUrl, source)
        assertTrue(ok)

        val dbRead = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        val sourceEntries = mutableListOf<String>()
        val cursor = dbRead.rawQuery("SELECT link FROM linkdatamodel WHERE source_id = ?", arrayOf(sourceId.toString()))
        cursor.use { c ->
            while (c.moveToNext()) {
                sourceEntries.add(c.getString(0))
            }
        }

        val otherCursor = dbRead.rawQuery("SELECT COUNT(*) FROM linkdatamodel WHERE link = ?", arrayOf("https://other.com/different_source_item"))
        val otherCount = otherCursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        dbRead.close()

        // Outdated item was removed
        assertFalse("outdated_item should have been removed", sourceEntries.contains("https://unique-feed.com/outdated_item"))
        // Kept item is still present
        assertTrue("kept_item should be kept", sourceEntries.contains("https://unique-feed.com/kept_item"))
        // New item was added
        assertTrue("new_item should be inserted", sourceEntries.contains("https://unique-feed.com/new_item"))
        // Entry from another source was not deleted
        assertEquals(1, otherCount)
    }

    @Test
    fun `removeOutdatedSourceEntries removes only entries not in validLinks for matching source`() = runBlocking {
        val sourceId = 88888L
        val sourceUrl = "https://example-unique2.com/feed.xml"
        val source = Source(id = sourceId, title = "Source 2", url = sourceUrl, enabled = true)

        val (okInit, countInit) = SourceRepository.insertSourceEntries(
            context,
            dbState,
            listOf(
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://example-unique2.com/item_a",
                    title = "Item A"
                ),
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://example-unique2.com/item_b",
                    title = "Item B"
                )
            ),
            source
        )
        assertTrue(okInit)
        assertEquals(2, countInit)

        // Only keep item_a
        val (ok, deletedCount) = SourceRepository.removeOutdatedSourceEntries(
            context,
            dbState,
            source,
            setOf("https://example-unique2.com/item_a")
        )
        assertTrue(ok)
        assertEquals(1, deletedCount)

        val dbRead = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        val remaining = mutableListOf<String>()
        val cursor = dbRead.rawQuery("SELECT link FROM linkdatamodel WHERE source_id = ?", arrayOf(sourceId.toString()))
        cursor.use { c ->
            while (c.moveToNext()) {
                remaining.add(c.getString(0))
            }
        }
        dbRead.close()

        assertEquals(listOf("https://example-unique2.com/item_a"), remaining)
    }

    @Test
    fun `insertSourceEntries inserts new entries and skips existing duplicates`() = runBlocking {
        val sourceUrl = "https://example3.com/feed.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Source 3", sourceUrl, enabled = true)
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!
        val sourceId = source.id!!

        val entries = listOf(
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://example3.com/entry1",
                title = "Entry 1",
                description = "Desc 1"
            ),
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://example3.com/entry2",
                title = "Entry 2",
                description = "Desc 2"
            )
        )

        // First insert: 2 entries inserted
        val (ok1, count1) = SourceRepository.insertSourceEntries(context, dbState, entries, source)
        assertTrue(ok1)
        assertEquals(2, count1)

        // Second insert with one duplicate and one new entry
        val entriesWithDuplicate = listOf(
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://example3.com/entry1",
                title = "Entry 1 Duplicate"
            ),
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://example3.com/entry3",
                title = "Entry 3"
            )
        )
        val (ok2, count2) = SourceRepository.insertSourceEntries(context, dbState, entriesWithDuplicate, source)
        assertTrue(ok2)
        assertEquals(1, count2)

        val dbRead = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        val storedLinks = mutableListOf<String>()
        val cursor = dbRead.rawQuery("SELECT link FROM linkdatamodel WHERE source_id = ? ORDER BY link", arrayOf(sourceId.toString()))
        cursor.use { c ->
            while (c.moveToNext()) {
                storedLinks.add(c.getString(0))
            }
        }
        dbRead.close()

        assertEquals(listOf("https://example3.com/entry1", "https://example3.com/entry2", "https://example3.com/entry3"), storedLinks)
    }

    @Test
    fun `deleteSource with deleteEntries true removes source and all its entries`() = runBlocking {
        val sourceId = 55555L
        val sourceUrl = "https://delete-entries-test.com/rss"
        val source = Source(id = sourceId, title = "To Delete", url = sourceUrl, enabled = true)

        val (okInsert, _) = SourceRepository.insertSource(context, dbState, source.title, source.url, source.enabled)
        assertTrue(okInsert)
        val insertedSource = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!

        val (okEntries, count) = SourceRepository.insertSourceEntries(
            context,
            dbState,
            listOf(
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://delete-entries-test.com/item1",
                    title = "Item 1"
                ),
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://delete-entries-test.com/item2",
                    title = "Item 2"
                )
            ),
            insertedSource
        )
        assertTrue(okEntries)
        assertEquals(2, count)

        // Delete source with deleteEntries = true
        val (okDelete, error) = SourceRepository.deleteSource(
            context,
            dbState,
            insertedSource.id!!,
            deleteEntries = true
        )
        assertTrue(okDelete)
        assertNull(error)

        // Source should be deleted
        val foundSource = SourceRepository.getSourceById(context, dbState, insertedSource.id!!)
        assertNull(foundSource)

        // Associated entries should be deleted
        val dbRead = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        val cursor = dbRead.rawQuery(
            "SELECT COUNT(*) FROM linkdatamodel WHERE source_id = ? OR source_url = ?",
            arrayOf(insertedSource.id.toString(), sourceUrl)
        )
        val remainingEntries = cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        dbRead.close()

        assertEquals(0, remainingEntries)
    }

    @Test
    fun `deleteSource with deleteEntries false removes source but preserves entries`() = runBlocking {
        val sourceUrl = "https://keep-entries-test.com/rss"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Keep Entries Source", sourceUrl, true)
        assertTrue(okInsert)
        val insertedSource = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!

        val (okEntries, count) = SourceRepository.insertSourceEntries(
            context,
            dbState,
            listOf(
                io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                    link = "https://keep-entries-test.com/item1",
                    title = "Item 1"
                )
            ),
            insertedSource
        )
        assertTrue(okEntries)
        assertEquals(1, count)

        // Delete source with deleteEntries = false
        val (okDelete, _) = SourceRepository.deleteSource(
            context,
            dbState,
            insertedSource.id!!,
            deleteEntries = false
        )
        assertTrue(okDelete)

        // Source is deleted
        assertNull(SourceRepository.getSourceById(context, dbState, insertedSource.id!!))

        // Entry is preserved
        val dbRead = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        val cursor = dbRead.rawQuery(
            "SELECT COUNT(*) FROM linkdatamodel WHERE link = ?",
            arrayOf("https://keep-entries-test.com/item1")
        )
        val entryCount = cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        dbRead.close()

        assertEquals(1, entryCount)
    }

    @Test
    fun `insertSourceEntries sets ageDesignation from entry age or falls back to source age`() = runBlocking {
        val sourceWithAge = Source(
            id = 100L,
            url = "https://age-test.com/rss",
            title = "Age Test Source",
            age = 12
        )
        val sourceWithoutAge = Source(
            id = 200L,
            url = "https://no-age-test.com/rss",
            title = "No Age Source",
            age = null
        )

        val entries = listOf(
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://age-test.com/entry-override",
                title = "Entry Override",
                age = 18
            ),
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://age-test.com/entry-fallback",
                title = "Entry Fallback",
                age = 0
            ),
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://no-age-test.com/entry-specific",
                title = "Entry Specific",
                age = 16
            ),
            io.github.rumcajs.offlinewebsearch.data.repositories.Entry(
                link = "https://no-age-test.com/entry-default",
                title = "Entry Default",
                age = null
            )
        )

        val dbWrite = android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        SourceRepository.insertSourceEntries(dbWrite, listOf(entries[0], entries[1]), sourceWithAge)
        SourceRepository.insertSourceEntries(dbWrite, listOf(entries[2], entries[3]), sourceWithoutAge)
        dbWrite.close()

        val dbRead = android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )

        fun getAgeForLink(link: String): Int {
            val cursor = dbRead.rawQuery("SELECT age FROM linkdatamodel WHERE link = ?", arrayOf(link))
            return cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
        }

        assertEquals(18, getAgeForLink("https://age-test.com/entry-override"))
        assertEquals(12, getAgeForLink("https://age-test.com/entry-fallback"))
        assertEquals(16, getAgeForLink("https://no-age-test.com/entry-specific"))
        assertEquals(0, getAgeForLink("https://no-age-test.com/entry-default"))

        dbRead.close()
    }

    @Test
    fun `insertSource with age sets age column and defaults to 0 when not provided`() = runBlocking {
        val (okDefault, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Default Age Source",
            url = "https://default-age.com/feed.xml",
            enabled = true
        )
        assertTrue(okDefault)
        val defaultSource = SourceRepository.getSourceByUrl(context, dbState, "https://default-age.com/feed.xml")
        assertNotNull(defaultSource)
        assertEquals(0, defaultSource!!.age)

        val (okCustom, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Custom Age Source",
            url = "https://custom-age.com/feed.xml",
            enabled = true,
            age = 18
        )
        assertTrue(okCustom)
        val customSource = SourceRepository.getSourceByUrl(context, dbState, "https://custom-age.com/feed.xml")
        assertNotNull(customSource)
        assertEquals(18, customSource!!.age)
    }

    @Test
    fun `updateSourceAge updates age column in database`() = runBlocking {
        val (okInsert, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Update Age Source",
            url = "https://update-age.com/feed.xml",
            enabled = true
        )
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, "https://update-age.com/feed.xml")!!
        assertEquals(0, source.age)

        val (okUpdate, err) = SourceRepository.updateSourceAge(
            context = context,
            activeDatabaseState = dbState,
            id = source.id!!,
            age = 21
        )
        assertTrue(err ?: "", okUpdate)

        val updated = SourceRepository.getSourceById(context, dbState, source.id!!)
        assertNotNull(updated)
        assertEquals(21, updated!!.age)
    }

    @Test
    fun `insertSource with language sets language column and defaults to empty string`() = runBlocking {
        val (okDefault, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Default Lang Source",
            url = "https://default-lang.com/feed.xml",
            enabled = true
        )
        assertTrue(okDefault)
        val defaultSource = SourceRepository.getSourceByUrl(context, dbState, "https://default-lang.com/feed.xml")
        assertNotNull(defaultSource)
        assertEquals("", defaultSource!!.language)

        val (okCustom, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Custom Lang Source",
            url = "https://custom-lang.com/feed.xml",
            enabled = true,
            language = "pl"
        )
        assertTrue(okCustom)
        val customSource = SourceRepository.getSourceByUrl(context, dbState, "https://custom-lang.com/feed.xml")
        assertNotNull(customSource)
        assertEquals("pl", customSource!!.language)
    }

    @Test
    fun `updateSourceLanguage updates language column in database`() = runBlocking {
        val (okInsert, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Update Lang Source",
            url = "https://update-lang.com/feed.xml",
            enabled = true
        )
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, "https://update-lang.com/feed.xml")!!
        assertEquals("", source.language)

        val (okUpdate, err) = SourceRepository.updateSourceLanguage(
            context = context,
            activeDatabaseState = dbState,
            id = source.id!!,
            language = "de"
        )
        assertTrue(err ?: "", okUpdate)

        val updated = SourceRepository.getSourceById(context, dbState, source.id!!)
        assertNotNull(updated)
        assertEquals("de", updated!!.language)
    }

    @Test
    fun `updateSourceProperties updates language column in database`() = runBlocking {
        val (okInsert, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Update Props Lang Source",
            url = "https://update-props-lang.com/feed.xml",
            enabled = true
        )
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, "https://update-props-lang.com/feed.xml")!!

        val (okUpdate, err) = SourceRepository.updateSourceProperties(
            context = context,
            activeDatabaseState = dbState,
            id = source.id!!,
            title = "New Title",
            url = "https://update-props-lang.com/feed.xml",
            enabled = true,
            language = "fr"
        )
        assertTrue(err ?: "", okUpdate)

        val updated = SourceRepository.getSourceById(context, dbState, source.id!!)
        assertNotNull(updated)
        assertEquals("fr", updated!!.language)
    }

    @Test
    fun `updateFetchData increments consecutive_errors when response isInvalid`() = runBlocking {
        val url = "https://error-feed.com/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Error Feed", url, enabled = true)
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!
        val sourceId = source.id!!

        val invalidUrlObj = object : io.github.rumcajs.offlinewebsearch.webtoolkit.Url(url) {
            override suspend fun getResponse(acceptHeader: String?): io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject(
                    statusCode = 500,
                    headers = emptyMap(),
                    error = "HTTP 500"
                )
            }
        }

        // First error: consecutive_errors should become 1
        val (ok1, _) = SourceRepository.updateFetchData(context, dbState, invalidUrlObj, source)
        assertFalse(ok1)
        var opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, sourceId)
        assertNotNull(opData)
        assertEquals(1, opData!!.consecutive_errors)

        // Second error: consecutive_errors should become 2
        val (ok2, _) = SourceRepository.updateFetchData(context, dbState, invalidUrlObj, source)
        assertFalse(ok2)
        opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, sourceId)
        assertNotNull(opData)
        assertEquals(2, opData!!.consecutive_errors)
    }

    @Test
    fun `updateFetchData resets consecutive_errors to 0 when response isValid`() = runBlocking {
        val url = "https://valid-feed.com/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Valid Feed", url, enabled = true)
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!
        val sourceId = source.id!!

        // First simulate prior errors
        SourceOperationalDataRepository.setSourceFetch(
            context = context,
            activeDatabaseState = dbState,
            sourceObjId = sourceId,
            isError = true
        )
        SourceOperationalDataRepository.setSourceFetch(
            context = context,
            activeDatabaseState = dbState,
            sourceObjId = sourceId,
            isError = true
        )
        var opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, sourceId)
        assertNotNull(opData)
        assertEquals(2, opData!!.consecutive_errors)

        // Now perform a valid fetch
        val rssXml = """
            <rss version="2.0">
              <channel>
                <title>Valid Feed</title>
                <link>https://valid-feed.com</link>
                <item>
                  <title>Valid Item</title>
                  <link>https://valid-feed.com/item1</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val validUrlObj = object : io.github.rumcajs.offlinewebsearch.webtoolkit.Url(url) {
            override suspend fun getResponse(acceptHeader: String?): io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to listOf("application/rss+xml")),
                    text = rssXml
                )
            }
            override suspend fun getPage(): io.github.rumcajs.offlinewebsearch.webtoolkit.Page {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage(url, rssXml)
            }
        }

        val (ok, _) = SourceRepository.updateFetchData(context, dbState, validUrlObj, source)
        assertTrue(ok)

        opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, sourceId)
        assertNotNull(opData)
        assertEquals(0, opData!!.consecutive_errors)
        assertEquals(1, opData!!.number_of_entries)
    }

    @Test
    fun `updateFetchData by url increments and resets consecutive_errors when source id is not set`() = runBlocking {
        val url = "https://no-id-feed.com/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "No ID Feed", url, enabled = true)
        assertTrue(okInsert)
        val sourceInDb = SourceRepository.getSourceByUrl(context, dbState, url)!!
        val sourceWithoutId = Source(id = null, title = "No ID Feed", url = url, enabled = true)

        val invalidUrlObj = object : io.github.rumcajs.offlinewebsearch.webtoolkit.Url(url) {
            override suspend fun getResponse(acceptHeader: String?): io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject(
                    statusCode = 404,
                    headers = emptyMap(),
                    error = "HTTP 404"
                )
            }
        }

        val (okErr, _) = SourceRepository.updateFetchData(context, dbState, invalidUrlObj, sourceWithoutId)
        assertFalse(okErr)

        var opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, sourceInDb.id!!)
        assertNotNull(opData)
        assertEquals(1, opData!!.consecutive_errors)

        val rssXml = """
            <rss version="2.0">
              <channel>
                <title>No ID Feed</title>
                <link>https://no-id-feed.com</link>
              </channel>
            </rss>
        """.trimIndent()

        val validUrlObj = object : io.github.rumcajs.offlinewebsearch.webtoolkit.Url(url) {
            override suspend fun getResponse(acceptHeader: String?): io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject(
                    statusCode = 200,
                    headers = mapOf("Content-Type" to listOf("application/rss+xml")),
                    text = rssXml
                )
            }
            override suspend fun getPage(): io.github.rumcajs.offlinewebsearch.webtoolkit.Page {
                return io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage(url, rssXml)
            }
        }

        val (okSuccess, _) = SourceRepository.updateFetchData(context, dbState, validUrlObj, sourceWithoutId)
        assertTrue(okSuccess)

        opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, sourceInDb.id!!)
        assertNotNull(opData)
        assertEquals(0, opData!!.consecutive_errors)
    }

    @Test
    fun `isFetchRequired returns false when source is disabled`() = runBlocking {
        val disabledSource = Source(id = 1L, url = "https://example.com/rss", title = "Disabled", enabled = false)
        val required = SourceRepository.isFetchRequired(context, dbState, disabledSource)
        assertFalse(required)
    }

    @Test
    fun `isFetchRequired returns false when source url is blank`() = runBlocking {
        val blankUrlSource = Source(id = 1L, url = "", title = "Blank URL", enabled = true)
        val required = SourceRepository.isFetchRequired(context, dbState, blankUrlSource)
        assertFalse(required)
    }

    @Test
    fun `isFetchRequired returns true when source has never been fetched`() = runBlocking {
        val url = "https://never-fetched.com/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Never Fetched", url, enabled = true)
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!

        val required = SourceRepository.isFetchRequired(context, dbState, source)
        assertTrue(required)
    }

    @Test
    fun `isFetchRequired returns false when source was fetched recently`() = runBlocking {
        val url = "https://recently-fetched.com/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Recently Fetched", url, enabled = true)
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!

        val nowIso = SourceOperationalDataRepository.getCurrentIsoTimestamp()
        SourceOperationalDataRepository.setSourceFetch(context, dbState, source.id!!, fetchTime = nowIso)

        val required = SourceRepository.isFetchRequired(context, dbState, source)
        assertFalse(required)
    }

    @Test
    fun `isFetchRequired returns true when source fetch timestamp is older than 1 hour`() = runBlocking {
        val url = "https://old-fetched.com/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Old Fetched", url, enabled = true)
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!

        // 2 hours ago
        val twoHoursAgoIso = "2020-01-01T00:00:00Z"
        SourceOperationalDataRepository.setSourceFetch(context, dbState, source.id!!, fetchTime = twoHoursAgoIso)

        val required = SourceRepository.isFetchRequired(context, dbState, source)
        assertTrue(required)
    }

    // ── hasOutdatedSources ───────────────────────────────────────────────────

    @Test
    fun `hasOutdatedSources returns false when there are no sources`() = runBlocking {
        // Clear all sources first
        SourceRepository.clear(context, dbState)
        val hasOutdated = SourceRepository.hasOutdatedSources(context, dbState)
        assertFalse(hasOutdated)
    }

    @Test
    fun `hasOutdatedSources returns true when an enabled source was never fetched`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url = "https://never-fetched-test.com/rss.xml"
        val (ok, _) = SourceRepository.insertSource(context, dbState, "Never Fetched", url, enabled = true)
        assertTrue(ok)

        val hasOutdated = SourceRepository.hasOutdatedSources(context, dbState)
        assertTrue(hasOutdated)
    }

    @Test
    fun `hasOutdatedSources returns true when an enabled source has outdated fetch timestamp`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url = "https://outdated-fetch-test.com/rss.xml"
        val (ok, _) = SourceRepository.insertSource(context, dbState, "Outdated Fetch", url, enabled = true)
        assertTrue(ok)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!

        val oldIso = "2020-01-01T00:00:00Z"
        SourceOperationalDataRepository.setSourceFetch(context, dbState, source.id!!, fetchTime = oldIso)

        val hasOutdated = SourceRepository.hasOutdatedSources(context, dbState)
        assertTrue(hasOutdated)
    }

    @Test
    fun `hasOutdatedSources returns false when all enabled sources were fetched recently`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url = "https://fresh-source-test.com/rss.xml"
        val (ok, _) = SourceRepository.insertSource(context, dbState, "Fresh Source", url, enabled = true)
        assertTrue(ok)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!

        val nowIso = SourceOperationalDataRepository.getCurrentIsoTimestamp()
        SourceOperationalDataRepository.setSourceFetch(context, dbState, source.id!!, fetchTime = nowIso)

        val hasOutdated = SourceRepository.hasOutdatedSources(context, dbState)
        assertFalse(hasOutdated)
    }

    @Test
    fun `hasOutdatedSources returns false when outdated source is disabled`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url = "https://disabled-outdated.com/rss.xml"
        val (ok, _) = SourceRepository.insertSource(context, dbState, "Disabled Outdated", url, enabled = false)
        assertTrue(ok)

        val hasOutdated = SourceRepository.hasOutdatedSources(context, dbState)
        assertFalse(hasOutdated)
    }

    @Test
    fun `hasOutdatedSources returns false when database is read-only`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url = "https://readonly-test.com/rss.xml"
        val (ok, _) = SourceRepository.insertSource(context, dbState, "ReadOnly Source", url, enabled = true)
        assertTrue(ok)

        val readOnlyState = dbState.copy(isReadOnly = true)
        val hasOutdated = SourceRepository.hasOutdatedSources(context, readOnlyState)
        assertFalse(hasOutdated)
    }

    // ── getAllSourcesWithOperationalData ──────────────────────────────────────

    @Test
    fun `getAllSourcesWithOperationalData returns sources with null operationalData when no operational record exists`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url = "https://no-op-data.com/feed.xml"
        val (ok, _) = SourceRepository.insertSource(context, dbState, "No Op Data", url, enabled = true)
        assertTrue(ok)

        val results = SourceRepository.getAllSourcesWithOperationalData(context, dbState)
        assertEquals(1, results.size)
        assertEquals(url, results[0].source.url)
        assertEquals("No Op Data", results[0].source.title)
        assertNull(results[0].operationalData)
    }

    @Test
    fun `getAllSourcesWithOperationalData returns sources with populated operationalData when operational record exists`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url = "https://with-op-data.com/feed.xml"
        val (ok, _) = SourceRepository.insertSource(context, dbState, "With Op Data", url, enabled = true)
        assertTrue(ok)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!

        val fetchTime = "2026-09-17T20:00:00Z"
        val pageHash = byteArrayOf(10, 20, 30)
        val bodyHash = byteArrayOf(40, 50, 60)
        SourceOperationalDataRepository.setSourceFetch(
            context = context,
            activeDatabaseState = dbState,
            sourceObjId = source.id!!,
            fetchTime = fetchTime,
            numberOfEntries = 42,
            pageHash = pageHash,
            bodyHash = bodyHash,
            isError = false
        )

        val results = SourceRepository.getAllSourcesWithOperationalData(context, dbState)
        assertEquals(1, results.size)
        assertEquals(url, results[0].source.url)
        assertEquals("With Op Data", results[0].source.title)
        assertNotNull(results[0].operationalData)
        assertEquals(fetchTime, results[0].operationalData!!.date_fetched)
        assertEquals(source.id, results[0].operationalData!!.source_id)
        assertEquals(42, results[0].operationalData!!.number_of_entries)
        assertArrayEquals(pageHash, results[0].operationalData!!.page_hash)
        assertArrayEquals(bodyHash, results[0].operationalData!!.body_hash)
        assertEquals(0, results[0].operationalData!!.consecutive_errors)
    }

    @Test
    fun `getAllSourcesWithOperationalData handles multiple sources with mixed operational data`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url1 = "https://a-source.com/feed.xml"
        val url2 = "https://b-source.com/feed.xml"
        SourceRepository.insertSource(context, dbState, "Source A", url1, enabled = true)
        SourceRepository.insertSource(context, dbState, "Source B", url2, enabled = true)
        val sourceA = SourceRepository.getSourceByUrl(context, dbState, url1)!!

        SourceOperationalDataRepository.setSourceFetch(
            context = context,
            activeDatabaseState = dbState,
            sourceObjId = sourceA.id!!,
            fetchTime = "2026-09-17T12:00:00Z",
            numberOfEntries = 15,
            isError = false
        )

        val results = SourceRepository.getAllSourcesWithOperationalData(context, dbState)
        assertEquals(2, results.size)

        val itemA = results.find { it.source.url == url1 }
        val itemB = results.find { it.source.url == url2 }

        assertNotNull(itemA)
        assertNotNull(itemA!!.operationalData)
        assertEquals(15, itemA.operationalData!!.number_of_entries)

        assertNotNull(itemB)
        assertNull(itemB!!.operationalData)
    }

    @Test
    fun `getAllSourcesWithOperationalData respects orderBy argument`() = runBlocking {
        val url1 = "https://bbb.com/feed.xml"
        val url2 = "https://aaa.com/feed.xml"
        SourceRepository.insertSource(context, dbState, title = "Zebra Title", url = url1, enabled = true)
        SourceRepository.insertSource(context, dbState, title = "Alpha Title", url = url2, enabled = true)

        val src1 = SourceRepository.getSourceByUrl(context, dbState, url1)!!
        val src2 = SourceRepository.getSourceByUrl(context, dbState, url2)!!

        SourceOperationalDataRepository.setSourceFetch(context, dbState, src1.id!!, "2026-09-01T10:00:00Z")
        SourceOperationalDataRepository.setSourceFetch(context, dbState, src2.id!!, "2026-09-02T10:00:00Z")

        // Order by Url: aaa before bbb
        val byUrl = SourceRepository.getAllSourcesWithOperationalData(context, dbState, io.github.rumcajs.offlinewebsearch.data.repositories.SourceOrder.ByUrl)
        assertEquals(url2, byUrl[0].source.url)
        assertEquals(url1, byUrl[1].source.url)

        // Order by Title: Alpha before Zebra
        val byTitle = SourceRepository.getAllSourcesWithOperationalData(context, dbState, io.github.rumcajs.offlinewebsearch.data.repositories.SourceOrder.ByTitle)
        assertEquals("Alpha Title", byTitle[0].source.title)
        assertEquals("Zebra Title", byTitle[1].source.title)

        // Order by FetchTime: 2026-09-01 before 2026-09-02
        val byFetch = SourceRepository.getAllSourcesWithOperationalData(context, dbState, io.github.rumcajs.offlinewebsearch.data.repositories.SourceOrder.ByFetchTime)
        assertEquals(src1.id, byFetch[0].source.id)
        assertEquals(src2.id, byFetch[1].source.id)
    }

    @Test
    fun `getAllSourcesWithOperationalData filters results using searchQuery`() = runBlocking {
        SourceRepository.clear(context, dbState)
        val url1 = "https://tech-news.com/feed.xml"
        val url2 = "https://cooking-blog.com/rss"
        val url3 = "https://tech-crunch.com/feed.xml"
        SourceRepository.insertSource(context, dbState, title = "Tech Daily", url = url1, enabled = true, language = "en")
        SourceRepository.insertSource(context, dbState, title = "Grandma Cooking", url = url2, enabled = true, language = "pl")
        SourceRepository.insertSource(context, dbState, title = "Tech Crunch", url = url3, enabled = true, language = "en")

        // Full text search: "tech" matches url1 and url3
        val techResults = SourceRepository.getAllSourcesWithOperationalData(context, dbState, searchQuery = "tech")
        assertEquals(2, techResults.size)
        assertTrue(techResults.all { it.source.title.contains("Tech") || it.source.url.contains("tech") })

        // Exact match by title
        val exactTitle = SourceRepository.getAllSourcesWithOperationalData(context, dbState, searchQuery = "title==Grandma Cooking")
        assertEquals(1, exactTitle.size)
        assertEquals("Grandma Cooking", exactTitle[0].source.title)

        // Exact match by language
        val langResults = SourceRepository.getAllSourcesWithOperationalData(context, dbState, searchQuery = "language==pl")
        assertEquals(1, langResults.size)
        assertEquals("Grandma Cooking", langResults[0].source.title)

        // Contains match by url
        val urlContains = SourceRepository.getAllSourcesWithOperationalData(context, dbState, searchQuery = "url=cooking")
        assertEquals(1, urlContains.size)
        assertEquals("https://cooking-blog.com/rss", urlContains[0].source.url)

        // Search with no match
        val noMatch = SourceRepository.getAllSourcesWithOperationalData(context, dbState, searchQuery = "astronomy")
        assertEquals(0, noMatch.size)
    }

    @Test
    fun `getAllSourcesWithOperationalData returns empty list on null or invalid dbState`() = runBlocking {
        val nullResult = SourceRepository.getAllSourcesWithOperationalData(context, null)
        assertTrue(nullResult.isEmpty())

        val nonSqlite = dbState.copy(localFileName = "test.json")
        val jsonResult = SourceRepository.getAllSourcesWithOperationalData(context, nonSqlite)
        assertTrue(jsonResult.isEmpty())
    }

    // ── populateSources ───────────────────────────────────────────────────────

    @Test
    fun `populateSources returns 0 for empty list`() {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        val count = db.use {
            SourceRepository.populateSources(it, emptyList())
        }
        assertEquals(0, count)
    }

    @Test
    fun `populateSources inserts all source fields into SQLite`() = runBlocking {
        val source = Source(
            id = 8001L,
            enabled = true,
            url = "https://populated-source.example.com/rss",
            title = "Populated Source",
            favicon = "https://populated-source.example.com/favicon.ico",
            source_type = "RSS",
            age = 7,
            auto_tag = "news,tech",
            language = "pl"
        )

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        val inserted = db.use {
            SourceRepository.populateSources(it, listOf(source))
        }
        assertEquals(1, inserted)

        val stored = SourceRepository.getSourceById(context, dbState, 8001L)
        assertNotNull(stored)
        assertEquals(8001L, stored!!.id)
        assertTrue(stored.enabled)
        assertEquals("https://populated-source.example.com/rss", stored.url)
        assertEquals("Populated Source", stored.title)
        assertEquals("https://populated-source.example.com/favicon.ico", stored.favicon)
        assertEquals("RSS", stored.source_type)
        assertEquals(7, stored.age)
        assertEquals("news,tech", stored.auto_tag)
        assertEquals("pl", stored.language)
    }

    @Test
    fun `populateSources with dbFile overload successfully populates database`() = runBlocking {
        val sources = listOf(
            Source(id = 8002L, url = "https://src1.example.com", title = "Source 1"),
            Source(id = 8003L, url = "https://src2.example.com", title = "Source 2")
        )

        val count = SourceRepository.populateSources(dbFile, sources)
        assertEquals(2, count)

        val s1 = SourceRepository.getSourceById(context, dbState, 8002L)
        val s2 = SourceRepository.getSourceById(context, dbState, 8003L)
        assertNotNull(s1)
        assertNotNull(s2)
        assertEquals("Source 1", s1!!.title)
        assertEquals("Source 2", s2!!.title)
    }

    @Test
    fun `populateSources with activeDatabaseState coroutine overload populates database`() = runBlocking {
        val sources = listOf(
            Source(url = "https://coroutine-src.example.com", title = "Coroutine Source")
        )

        val (ok, count) = SourceRepository.populateSources(context, dbState, sources)
        assertTrue(ok)
        assertEquals(1, count)

        val stored = SourceRepository.getSourceByUrl(context, dbState, "https://coroutine-src.example.com")
        assertNotNull(stored)
        assertEquals("Coroutine Source", stored!!.title)
    }

    @Test
    fun `populateSources with activeDatabaseState fails gracefully for read-only db`() = runBlocking {
        val readOnlyState = dbState.copy(isReadOnly = true)
        val sources = listOf(
            Source(url = "https://readonly-src.example.com", title = "Read Only Source")
        )

        val (ok, count) = SourceRepository.populateSources(context, readOnlyState, sources)
        assertFalse(ok)
        assertEquals(0, count)
    }

    @Test
    fun `populateSources rolls back transaction and logs error on failure`() = runBlocking {
        // Pre-insert a source with id 9001L
        val initialSource = Source(id = 9001L, url = "https://existing-src.example.com", title = "Existing")
        SourceRepository.populateSources(dbFile, listOf(initialSource))

        // Attempt batch insert where second item causes primary key collision
        val batch = listOf(
            Source(id = 9002L, url = "https://batch-src.example.com", title = "Batch Source"),
            Source(id = 9001L, url = "https://duplicate-src.example.com", title = "Duplicate ID Source")
        )

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.use {
                SourceRepository.populateSources(it, batch)
            }
            fail("Expected exception on primary key collision")
        } catch (e: Exception) {
            // Expected
        }

        // Verify transaction rollback: 9002L must NOT exist in database
        val s2 = SourceRepository.getSourceById(context, dbState, 9002L)
        assertNull(s2)

        // Verify error logged to AppLoggingRepository
        val logs = AppLoggingRepository.getLogs(context, dbState)
        assertTrue("Log should be recorded on insertion failure", logs.isNotEmpty())
        assertTrue(logs.any { it.info_text.contains("Failed to insert source") })
    }
}



