package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
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
 * Unit tests for [EntryRepository.addEntryToSql].
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
                        bookmarked = c.getInt(c.getColumnIndexOrThrow("bookmarked")) == 1,
                        page_rating_votes = c.getInt(c.getColumnIndexOrThrow("page_rating_votes")),
                        page_rating_visits = c.getInt(c.getColumnIndexOrThrow("page_rating_visits")),
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

    // ── addEntryToSql: return value ───────────────────────────────────────────

    @Test
    fun `addEntryToSql returns success triple for valid entry`() = runBlocking {
        val (ok, rowId, error) = EntryRepository.addEntryToSql(context, dbState, minimalEntry())
        assertTrue("Expected success but got: $error", ok)
        assertTrue("rowId should be positive", rowId > 0)
        assertNull(error)
    }

    // ── addEntryToSql: persistence ────────────────────────────────────────────

    @Test
    fun `addEntryToSql persists link and title in linkdatamodel`() = runBlocking {
        val entry = Entry(link = "https://persist.example.com", title = "Persisted Title")
        val (ok, rowId, _) = EntryRepository.addEntryToSql(context, dbState, entry)
        assertTrue(ok)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertEquals("https://persist.example.com", stored!!.link)
        assertEquals("Persisted Title", stored.title)
    }

    @Test
    fun `addEntryToSql persists bookmarked true`() = runBlocking {
        val entry = minimalEntry().copy(bookmarked = true)
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, entry)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertTrue("bookmarked should be true", stored!!.bookmarked == true)
    }

    @Test
    fun `addEntryToSql persists bookmarked false`() = runBlocking {
        val entry = minimalEntry().copy(bookmarked = false)
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, entry)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertFalse("bookmarked should be false", stored!!.bookmarked == true)
    }

    @Test
    fun `addEntryToSql persists page rating fields`() = runBlocking {
        val entry = minimalEntry().copy(
            page_rating_votes = 42,
            page_rating_visits = 7
        )
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, entry)

        val stored = queryEntry(rowId)
        assertNotNull(stored)
        assertEquals(42, stored!!.page_rating_votes)
        assertEquals(7, stored.page_rating_visits)
    }

    // ── addEntryToSql: tags ───────────────────────────────────────────────────

    @Test
    fun `addEntryToSql inserts tags into entrycompactedtags`() = runBlocking {
        val entry = minimalEntry().copy(tags = listOf("kotlin", "android", "sqlite"))
        val (ok, rowId, _) = EntryRepository.addEntryToSql(context, dbState, entry)
        assertTrue(ok)

        val storedTags = queryTags(rowId)
        assertEquals(3, storedTags.size)
        assertTrue(storedTags.containsAll(listOf("kotlin", "android", "sqlite")))
    }

    @Test
    fun `addEntryToSql with null tags inserts no tag rows`() = runBlocking {
        val entry = minimalEntry().copy(tags = null)
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, entry)

        val storedTags = queryTags(rowId)
        assertTrue("No tags should be stored for null tags", storedTags.isEmpty())
    }

    @Test
    fun `addEntryToSql with empty tags list inserts no tag rows`() = runBlocking {
        val entry = minimalEntry().copy(tags = emptyList())
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, entry)

        val storedTags = queryTags(rowId)
        assertTrue("No tags should be stored for empty tags list", storedTags.isEmpty())
    }

    // ── addEntryToSql: guard cases ────────────────────────────────────────────

    @Test
    fun `addEntryToSql fails when database is read-only`() = runBlocking {
        val readOnlyState = dbState.copy(isReadOnly = true)
        val (ok, rowId, error) = EntryRepository.addEntryToSql(context, readOnlyState, minimalEntry())
        assertFalse(ok)
        assertEquals(-1L, rowId)
        assertNotNull(error)
    }

    @Test
    fun `addEntryToSql fails when database file does not exist`() = runBlocking {
        val missingState = dbState.copy(localFileName = "nonexistent.db")
        val (ok, rowId, error) = EntryRepository.addEntryToSql(context, missingState, minimalEntry())
        assertFalse(ok)
        assertEquals(-1L, rowId)
        assertNotNull(error)
    }

    @Test
    fun `addEntryToSql fails when database extension is not db`() = runBlocking {
        val jsonState = dbState.copy(localFileName = "some_data.json")
        val (ok, rowId, error) = EntryRepository.addEntryToSql(context, jsonState, minimalEntry())
        assertFalse(ok)
        assertEquals(-1L, rowId)
        assertNotNull(error)
    }

    // ── addEntryToSql: multiple inserts ───────────────────────────────────────

    @Test
    fun `addEntryToSql each insert gets a unique row id`() = runBlocking {
        val (_, id1, _) = EntryRepository.addEntryToSql(context, dbState, minimalEntry("https://a.com", "A"))
        val (_, id2, _) = EntryRepository.addEntryToSql(context, dbState, minimalEntry("https://b.com", "B"))
        assertTrue("Row IDs must be distinct", id1 != id2)
        assertTrue("Both row IDs should be positive", id1 > 0 && id2 > 0)
    }

    // ── Votes: addVoteInSql and adjustVotesInSql ──────────────────────────────

    @Test
    fun `addVoteInSql increments vote count by 1`() = runBlocking {
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, minimalEntry().copy(page_rating_votes = 10))
        val (ok, newVotes) = EntryRepository.addVoteInSql(context, dbState, rowId)

        assertTrue(ok)
        assertEquals(11, newVotes)

        val stored = queryEntry(rowId)
        assertEquals(11, stored?.page_rating_votes)
    }

    @Test
    fun `adjustVotesInSql clamps vote count at MAX_PAGE_RATING_VOTES 100`() = runBlocking {
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, minimalEntry().copy(page_rating_votes = 99))
        val (ok, newVotes) = EntryRepository.adjustVotesInSql(context, dbState, rowId, delta = 5)

        assertTrue(ok)
        assertEquals(EntryRepository.MAX_PAGE_RATING_VOTES, newVotes)
        assertEquals(100, newVotes)

        val stored = queryEntry(rowId)
        assertEquals(100, stored?.page_rating_votes)
    }

    @Test
    fun `adjustVotesInSql clamps vote count at MIN_PAGE_RATING_VOTES -100`() = runBlocking {
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, minimalEntry().copy(page_rating_votes = -95))
        val (ok, newVotes) = EntryRepository.adjustVotesInSql(context, dbState, rowId, delta = -10)

        assertTrue(ok)
        assertEquals(EntryRepository.MIN_PAGE_RATING_VOTES, newVotes)
        assertEquals(-100, newVotes)

        val stored = queryEntry(rowId)
        assertEquals(-100, stored?.page_rating_votes)
    }

    @Test
    fun `adjustVotesInSql fails when database is read-only`() = runBlocking {
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, minimalEntry())
        val readOnlyState = dbState.copy(isReadOnly = true)
        val (ok, newVotes) = EntryRepository.adjustVotesInSql(context, readOnlyState, rowId, delta = 1)

        assertFalse(ok)
        assertNull(newVotes)
    }

    // ── Tags: EntryCompactedTagsRepository replace and load ───────────────────

    @Test
    fun `deleteTagsForEntry and insertTag replaces tags for entry`() = runBlocking {
        val (_, rowId, _) = EntryRepository.addEntryToSql(context, dbState, minimalEntry().copy(tags = listOf("old1", "old2")))
        assertEquals(listOf("old1", "old2"), queryTags(rowId))

        // Replace tags
        val (delOk, _) = EntryCompactedTagsRepository.deleteTagsForEntry(context, dbState, rowId)
        assertTrue(delOk)

        val (ins1, _) = EntryCompactedTagsRepository.insertTag(context, dbState, "news", rowId)
        val (ins2, _) = EntryCompactedTagsRepository.insertTag(context, dbState, "tech", rowId)
        assertTrue(ins1)
        assertTrue(ins2)

        val loadedTags = EntryCompactedTagsRepository.loadTagsForEntry(context, dbState, rowId).map { it.tag }
        assertEquals(listOf("news", "tech"), loadedTags)
    }
}
