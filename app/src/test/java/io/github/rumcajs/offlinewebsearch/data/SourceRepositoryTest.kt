package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        val countBefore = SourceRepository.getAllSources(context, dbState).size

        SourceRepository.insertSource(context, dbState, "Counter Test", "https://counter.test/rss", true)

        val countAfter = SourceRepository.getAllSources(context, dbState).size
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
            put("source_obj_id", sourceObjId)
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
        assertEquals(sourceObjId, data.source_obj_id)
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
}
