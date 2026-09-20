package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryCompactedTagsRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SocialData
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
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
 * Unit tests for [io.github.rumcajs.offlinewebsearch.data.repositories.EntryRepository.add].
 *
 * Uses Robolectric + [RepositoryTestHelper] to provide a writable copy of
 * `assets/table.db` for each test case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryRepositoryTest {

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

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun minimalEntry(
        link: String = "https://example.com/entry",
        title: String = "Test Title"
    ) = Entry(link = link, title = title)

    /** Reads a single linkdatamodel row by id and returns it, or null if absent. */
    private fun queryEntry(id: Long): Entry? {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            val cursor = it.rawQuery(
                "SELECT id, link, title, description, author, album, language, " +
                    "page_rating_votes, page_rating_visits, page_rating, thumbnail, " +
                    "date_created, date_published, date_dead_since, age, status_code, " +
                    "manual_status_code, bookmarked, source_id, source_url " +
                    "FROM linkdatamodel WHERE id = ?",
                arrayOf(id.toString())
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    Entry(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        link = c.getString(c.getColumnIndexOrThrow("link")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        description = c.getString(c.getColumnIndexOrThrow("description")),
                        author = c.getString(c.getColumnIndexOrThrow("author")),
                        album = c.getString(c.getColumnIndexOrThrow("album")),
                        language = c.getString(c.getColumnIndexOrThrow("language")),
                        page_rating_votes = c.getInt(c.getColumnIndexOrThrow("page_rating_votes")),
                        page_rating_visits = c.getInt(c.getColumnIndexOrThrow("page_rating_visits")),
                        page_rating = c.getInt(c.getColumnIndexOrThrow("page_rating")),
                        thumbnail = c.getString(c.getColumnIndexOrThrow("thumbnail")),
                        date_created = c.getString(c.getColumnIndexOrThrow("date_created")),
                        date_published = c.getString(c.getColumnIndexOrThrow("date_published")),
                        date_dead_since = c.getString(c.getColumnIndexOrThrow("date_dead_since")),
                        age = c.getInt(c.getColumnIndexOrThrow("age")),
                        status_code = c.getInt(c.getColumnIndexOrThrow("status_code")),
                        manual_status_code = c.getInt(c.getColumnIndexOrThrow("manual_status_code")),
                        bookmarked = c.getInt(c.getColumnIndexOrThrow("bookmarked")) == 1,
                        source_id = if (!c.isNull(c.getColumnIndexOrThrow("source_id"))) c.getLong(c.getColumnIndexOrThrow("source_id")) else null,
                        source_url = c.getString(c.getColumnIndexOrThrow("source_url")),
                    )
                } else null
            }
        }
    }

    /** Returns all tags for [entryId] from the entrycompactedtags table. */
    private fun queryTags(entryId: Long): List<String> {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            val cursor = it.rawQuery(
                "SELECT tag FROM entrycompactedtags WHERE entry_id = ?",
                arrayOf(entryId.toString())
            )
            cursor.use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(0))
                }
            }
        }
    }

    /** Returns [SocialData] row for [entryId] from socialdata table, or null if absent. */
    private fun querySocialData(entryId: Long): SocialData? {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            val cursor = it.rawQuery(
                "SELECT entry_id, thumbs_up, thumbs_down, view_count, rating, upvote_ratio, " +
                    "upvote_diff, upvote_view_ratio, stars, followers_count, date_updated " +
                    "FROM socialdata WHERE entry_id = ?",
                arrayOf(entryId.toString())
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    SocialData(
                        entryId = c.getLong(c.getColumnIndexOrThrow("entry_id")),
                        thumbsUp = c.getInt(c.getColumnIndexOrThrow("thumbs_up")),
                        thumbsDown = c.getInt(c.getColumnIndexOrThrow("thumbs_down")),
                        viewCount = c.getInt(c.getColumnIndexOrThrow("view_count")),
                        rating = c.getInt(c.getColumnIndexOrThrow("rating")),
                        upvoteRatio = c.getInt(c.getColumnIndexOrThrow("upvote_ratio")),
                        upvoteDiff = c.getInt(c.getColumnIndexOrThrow("upvote_diff")),
                        upvoteViewRatio = c.getInt(c.getColumnIndexOrThrow("upvote_view_ratio")),
                        stars = c.getInt(c.getColumnIndexOrThrow("stars")),
                        followersCount = c.getInt(c.getColumnIndexOrThrow("followers_count")),
                        dateUpdated = c.getString(c.getColumnIndexOrThrow("date_updated")),
                    )
                } else null
            }
        }
    }

    // ── addEntry: return value ───────────────────────────────────────────

    @Test
    fun `addEntry returns success triple for valid entry`() = runBlocking {
        val (ok, rowId, error) = EntryRepository.add(context, dbState, minimalEntry())
        assertTrue("Expected success but got: $error", ok)
        assertTrue("rowId should be positive", rowId > 0)
        assertNull(error)
    }

    // ── addEntry: persistence ────────────────────────────────────────────

    @Test
    fun `addEntry persists link and title in linkdatamodel`() = runBlocking {
        val entry = Entry(link = "https://persist.example.com", title = "Persisted Title")
        val (ok, rowId, _) = EntryRepository.add(context, dbState, entry)
        assertTrue(ok)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertEquals("https://persist.example.com", stored!!.link)
        assertEquals("Persisted Title", stored.title)
    }

    @Test
    fun `addEntry persists bookmarked true`() = runBlocking {
        val entry = minimalEntry().copy(bookmarked = true)
        val (_, rowId, _) = EntryRepository.add(context, dbState, entry)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertTrue("bookmarked should be true", stored!!.bookmarked == true)
    }

    @Test
    fun `addEntry persists bookmarked false`() = runBlocking {
        val entry = minimalEntry().copy(bookmarked = false)
        val (_, rowId, _) = EntryRepository.add(context, dbState, entry)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertFalse("bookmarked should be false", stored!!.bookmarked == true)
    }

    @Test
    fun `addEntry persists page rating fields`() = runBlocking {
        val entry = minimalEntry().copy(
            page_rating_votes = 42,
            page_rating_visits = 7
        )
        val (_, rowId, _) = EntryRepository.add(context, dbState, entry)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertEquals(42, stored!!.page_rating_votes)
        assertEquals(7, stored.page_rating_visits)
    }

    // ── addEntry: tags ───────────────────────────────────────────────────

    @Test
    fun `addEntry inserts tags into entrycompactedtags`() = runBlocking {
        val entry = minimalEntry().copy(tags = listOf("kotlin", "android", "sqlite"))
        val (ok, rowId, _) = EntryRepository.add(context, dbState, entry)
        assertTrue(ok)

        val storedTags = queryTags(rowId)
        assertEquals(3, storedTags.size)
        assertTrue(storedTags.containsAll(listOf("kotlin", "android", "sqlite")))
    }

    @Test
    fun `addEntry with null tags inserts no tag rows`() = runBlocking {
        val entry = minimalEntry().copy(tags = null)
        val (_, rowId, _) = EntryRepository.add(context, dbState, entry)

        val storedTags = queryTags(rowId)
        assertTrue("No tags should be stored for null tags", storedTags.isEmpty())
    }

    @Test
    fun `addEntry with empty tags list inserts no tag rows`() = runBlocking {
        val entry = minimalEntry().copy(tags = emptyList())
        val (_, rowId, _) = EntryRepository.add(context, dbState, entry)

        val storedTags = queryTags(rowId)
        assertTrue("No tags should be stored for empty tags list", storedTags.isEmpty())
    }

    // ── addEntry: guard cases ────────────────────────────────────────────

    @Test
    fun `addEntry fails when database is read-only`() = runBlocking {
        val readOnlyState = dbState.copy(isReadOnly = true)
        val (ok, rowId, error) = EntryRepository.add(context, readOnlyState, minimalEntry())
        assertFalse(ok)
        assertEquals(-1L, rowId)
        assertNotNull(error)
    }

    @Test
    fun `addEntry fails when database file does not exist`() = runBlocking {
        val missingState = dbState.copy(localFileName = "nonexistent.db")
        val (ok, rowId, error) = EntryRepository.add(context, missingState, minimalEntry())
        assertFalse(ok)
        assertEquals(-1L, rowId)
        assertNotNull(error)
    }

    @Test
    fun `addEntry fails when database extension is not db`() = runBlocking {
        val jsonState = dbState.copy(localFileName = "some_data.json")
        val (ok, rowId, error) = EntryRepository.add(context, jsonState, minimalEntry())
        assertFalse(ok)
        assertEquals(-1L, rowId)
        assertNotNull(error)
    }

    // ── addEntry: multiple inserts ───────────────────────────────────────

    @Test
    fun `addEntry each insert gets a unique row id`() = runBlocking {
        val (_, id1, _) = EntryRepository.add(context, dbState, minimalEntry("https://a.com", "A"))
        val (_, id2, _) = EntryRepository.add(context, dbState, minimalEntry("https://b.com", "B"))
        assertTrue("Row IDs must be distinct", id1 != id2)
        assertTrue("Both row IDs should be positive", id1 > 0 && id2 > 0)
    }

    // ── Votes: setVote ──────────────────────────────────────────────────

    @Test
    fun `setVote updates vote count to integer value`() = runBlocking {
        val (_, rowId, _) = EntryRepository.add(context, dbState, minimalEntry().copy(page_rating_votes = 10))
        val (ok, newVotes) = EntryRepository.setVote(context, dbState, rowId, 42)

        assertTrue(ok)
        assertEquals(42, newVotes)

        val stored = queryEntry(rowId)
        assertEquals(42, stored?.page_rating_votes)
    }

    @Test
    fun `setVote clamps vote count at MAX_PAGE_RATING_VOTES 100`() = runBlocking {
        val (_, rowId, _) = EntryRepository.add(context, dbState, minimalEntry().copy(page_rating_votes = 10))
        val (ok, newVotes) = EntryRepository.setVote(context, dbState, rowId, 150)

        assertTrue(ok)
        assertEquals(EntryRepository.MAX_PAGE_RATING_VOTES, newVotes)
        assertEquals(100, newVotes)

        val stored = queryEntry(rowId)
        assertEquals(100, stored?.page_rating_votes)
    }

    @Test
    fun `setVote clamps vote count at MIN_PAGE_RATING_VOTES -100`() = runBlocking {
        val (_, rowId, _) = EntryRepository.add(context, dbState, minimalEntry().copy(page_rating_votes = 10))
        val (ok, newVotes) = EntryRepository.setVote(context, dbState, rowId, -120)

        assertTrue(ok)
        assertEquals(EntryRepository.MIN_PAGE_RATING_VOTES, newVotes)
        assertEquals(-100, newVotes)

        val stored = queryEntry(rowId)
        assertEquals(-100, stored?.page_rating_votes)
    }

    @Test
    fun `setVote fails when database is read-only`() = runBlocking {
        val (_, rowId, _) = EntryRepository.add(context, dbState, minimalEntry())
        val readOnlyState = dbState.copy(isReadOnly = true)
        val (ok, newVotes) = EntryRepository.setVote(context, readOnlyState, rowId, 5)

        assertFalse(ok)
        assertNull(newVotes)
    }

    // ── Tags: EntryCompactedTagsRepository replace and load ───────────────────

    @Test
    fun `deleteTagsForEntry and insertTag replaces tags for entry`() = runBlocking {
        val (_, rowId, _) = EntryRepository.add(context, dbState, minimalEntry().copy(tags = listOf("old1", "old2")))
        assertEquals(listOf("old1", "old2"), queryTags(rowId))

        // Replace tags
        val (delOk, _) = EntryCompactedTagsRepository.deleteTagsForEntry(context, dbState, rowId)
        assertTrue(delOk)

        val (ins1, _) = EntryCompactedTagsRepository.insertTag(context, dbState, "news", rowId)
        val (ins2, _) = EntryCompactedTagsRepository.insertTag(context, dbState, "tech", rowId)
        assertTrue(ins1)
        assertTrue(ins2)

        val loadedTags = EntryCompactedTagsRepository.getTagsForEntry(context, dbState, rowId).map { it.tag }
        assertEquals(listOf("news", "tech"), loadedTags)
    }

    // ── EntrySqliteRepository: removeOutdatedSourceEntries & deleteEntriesForSource ──

    @Test
    fun `removeOutdatedSourceEntries removes outdated entries and associated records`() = runBlocking {
        val source = Source(id = 12345L, url = "https://source1.example.com", title = "Source 1")
        val (ok1, _) = SourceRepository.insertSourceEntries(
            context,
            dbState,
            listOf(
                Entry(link = "https://source1.example.com/keep", title = "Keep"),
                Entry(link = "https://source1.example.com/remove", title = "Remove")
            ),
            source
        )
        assertTrue(ok1)

        val (okRemove, count) = io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository.removeOutdatedSourceEntries(
            context,
            dbState,
            source,
            setOf("https://source1.example.com/keep")
        )
        assertTrue(okRemove)
        assertEquals(1, count)

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val remainingLinks = mutableListOf<String>()
        val cursor = db.rawQuery("SELECT link FROM linkdatamodel WHERE source_id = ?", arrayOf("12345"))
        cursor.use { c ->
            while (c.moveToNext()) {
                remainingLinks.add(c.getString(0))
            }
        }
        db.close()

        assertEquals(listOf("https://source1.example.com/keep"), remainingLinks)
    }

    @Test
    fun `deleteEntriesForSource deletes all entries matching sourceId and sourceUrl`() = runBlocking {
        val source = Source(id = 23456L, url = "https://source2.example.com", title = "Source 2")
        val (okInsert, _) = SourceRepository.insertSourceEntries(
            context,
            dbState,
            listOf(
                Entry(link = "https://source2.example.com/item1", title = "Item 1"),
                Entry(link = "https://source2.example.com/item2", title = "Item 2")
            ),
            source
        )
        assertTrue(okInsert)

        val (okDelete, count) = io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository.deleteEntriesForSource(
            context,
            dbState,
            source.id,
            source.url
        )
        assertTrue(okDelete)
        assertEquals(2, count)

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT COUNT(*) FROM linkdatamodel WHERE source_id = ?", arrayOf("23456"))
        val remaining = cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        db.close()

        assertEquals(0, remaining)
    }

    // ── EntrySqliteRepository: populateEntries ────────────────────────────────

    @Test
    fun `populateEntries returns 0 for empty list`() {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        val count = db.use {
            EntrySqliteRepository.populateEntries(it, emptyList())
        }
        assertEquals(0, count)
    }

    @Test
    fun `populateEntries inserts all fields, tags, and socialData into SQLite`() {
        val entry = Entry(
            id = 5001L,
            link = "https://populated.example.com",
            title = "Populated Title",
            description = "Populated Description",
            author = "Populated Author",
            album = "Populated Album",
            language = "en",
            page_rating_votes = 12,
            page_rating_visits = 34,
            page_rating = 56,
            thumbnail = "https://thumb.example.com/img.png",
            date_created = "2026-01-01 10:00:00",
            date_published = "2026-01-01 12:00:00",
            date_dead_since = "2026-02-01 00:00:00",
            age = 18,
            status_code = 200,
            manual_status_code = 200,
            bookmarked = true,
            source_id = 99L,
            source_url = "https://source.example.com/rss",
            tags = listOf("alpha", "beta", "gamma"),
            socialData = SocialData(
                thumbsUp = 10,
                thumbsDown = 2,
                viewCount = 1000,
                rating = 4,
                upvoteRatio = 83,
                upvoteDiff = 8,
                upvoteViewRatio = 1,
                stars = 5,
                followersCount = 500,
                dateUpdated = "2026-03-01 12:00:00"
            )
        )

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        val inserted = db.use {
            EntrySqliteRepository.populateEntries(it, listOf(entry))
        }
        assertEquals(1, inserted)

        val storedEntry = queryEntry(5001L)
        assertNotNull(storedEntry)
        assertEquals("https://populated.example.com", storedEntry!!.link)
        assertEquals("Populated Title", storedEntry.title)
        assertEquals("Populated Description", storedEntry.description)
        assertEquals("Populated Author", storedEntry.author)
        assertEquals("Populated Album", storedEntry.album)
        assertEquals("en", storedEntry.language)
        assertEquals(12, storedEntry.page_rating_votes)
        assertEquals(34, storedEntry.page_rating_visits)
        assertEquals(56, storedEntry.page_rating)
        assertEquals("https://thumb.example.com/img.png", storedEntry.thumbnail)
        assertEquals("2026-01-01 10:00:00", storedEntry.date_created)
        assertEquals("2026-01-01 12:00:00", storedEntry.date_published)
        assertEquals("2026-02-01 00:00:00", storedEntry.date_dead_since)
        assertEquals(18, storedEntry.age)
        assertEquals(200, storedEntry.status_code)
        assertEquals(200, storedEntry.manual_status_code)
        assertTrue(storedEntry.bookmarked == true)
        assertEquals(99L, storedEntry.source_id)
        assertEquals("https://source.example.com/rss", storedEntry.source_url)

        val storedTags = queryTags(5001L)
        assertEquals(3, storedTags.size)
        assertTrue(storedTags.containsAll(listOf("alpha", "beta", "gamma")))

        val storedSocial = querySocialData(5001L)
        assertNotNull(storedSocial)
        assertEquals(5001L, storedSocial!!.entryId)
        assertEquals(10, storedSocial.thumbsUp)
        assertEquals(2, storedSocial.thumbsDown)
        assertEquals(1000, storedSocial.viewCount)
        assertEquals(4, storedSocial.rating)
        assertEquals(83, storedSocial.upvoteRatio)
        assertEquals(8, storedSocial.upvoteDiff)
        assertEquals(1, storedSocial.upvoteViewRatio)
        assertEquals(5, storedSocial.stars)
        assertEquals(500, storedSocial.followersCount)
        assertEquals("2026-03-01 12:00:00", storedSocial.dateUpdated)
    }

    @Test
    fun `populateEntries with dbFile overload successfully populates database`() {
        val entries = listOf(
            Entry(id = 6001L, link = "https://file1.example.com", title = "File Entry 1"),
            Entry(id = 6002L, link = "https://file2.example.com", title = "File Entry 2")
        )

        val inserted = EntrySqliteRepository.populateEntries(dbFile, entries)
        assertEquals(2, inserted)

        val e1 = queryEntry(6001L)
        val e2 = queryEntry(6002L)
        assertNotNull(e1)
        assertNotNull(e2)
        assertEquals("File Entry 1", e1!!.title)
        assertEquals("File Entry 2", e2!!.title)
    }

    @Test
    fun `populateEntries links tags and social data to auto-generated rowId when id is null`() {
        val entry = Entry(
            id = null,
            link = "https://autoid.example.com",
            title = "Auto ID Entry",
            tags = listOf("autotag1", "autotag2"),
            socialData = SocialData(viewCount = 999)
        )

        val inserted = EntrySqliteRepository.populateEntries(dbFile, listOf(entry))
        assertEquals(1, inserted)

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val generatedId = db.use {
            val cursor = it.rawQuery("SELECT id FROM linkdatamodel WHERE link = ?", arrayOf("https://autoid.example.com"))
            cursor.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }
        assertTrue(generatedId > 0)

        val tags = queryTags(generatedId)
        assertEquals(listOf("autotag1", "autotag2"), tags)

        val social = querySocialData(generatedId)
        assertNotNull(social)
        assertEquals(999, social!!.viewCount)
    }

    @Test
    fun `populateEntries rolls back transaction and logs error on failure`() {
        // Pre-insert an entry with id 7001L
        val initialEntry = Entry(id = 7001L, link = "https://existing.example.com", title = "Existing")
        EntrySqliteRepository.populateEntries(dbFile, listOf(initialEntry))

        // Attempt to batch insert where second item causes primary key constraint violation
        val batch = listOf(
            Entry(id = 7002L, link = "https://batch1.example.com", title = "Batch 1"),
            Entry(id = 7001L, link = "https://duplicate.example.com", title = "Duplicate ID")
        )

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.use {
                EntrySqliteRepository.populateEntries(it, batch)
            }
            fail("Expected exception on primary key collision")
        } catch (e: Exception) {
            // Expected
        }

        // Verify transaction was rolled back: 7002L must NOT exist in linkdatamodel
        assertNull(queryEntry(7002L))

        // Verify error was logged to AppLoggingRepository table
        val logs = runBlocking {
            AppLoggingRepository.getLogs(context, dbState)
        }
        assertTrue("Log should be recorded on insertion failure", logs.isNotEmpty())
        assertTrue(logs.any { it.info_text.contains("Failed to insert entry") })
    }
}
