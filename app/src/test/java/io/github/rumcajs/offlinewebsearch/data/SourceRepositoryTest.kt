package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
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
 * Unit tests for [SourceRepository.insertSource].
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
        val disabledSource = Source(id = 1L, title = "Disabled", url = "https://example.com/rss", enabled = false)
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
}
