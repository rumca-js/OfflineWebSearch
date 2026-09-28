package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.RepositoryTestHelper
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRulesRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalDataRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.webtoolkit.Page
import io.github.rumcajs.offlinewebsearch.webtoolkit.PageResponseObject
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url
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
 * Unit tests for [SourceUpdater].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceUpdaterTest {

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
    fun `update skips empty url source`() = runBlocking {
        val blankSource = Source(id = 1L, title = "Blank", url = "", enabled = true)
        val (ok, reason) = SourceUpdater.updateSource(context, dbState, blankSource)
        assertFalse(ok)
        assertEquals("Source URL is empty", reason)
    }

    @Test
    fun `update skips disabled source`() = runBlocking {
        val disabledSource = Source(id = 1L, title = "Disabled", url = "https://example.com/rss", enabled = false)
        val (ok, reason) = SourceUpdater.updateSource(context, dbState, disabledSource)
        assertFalse(ok)
        assertEquals("Source is disabled", reason)
    }

    @Test
    fun `update skips source fetched recently`() = runBlocking {
        val url = "https://recent.test/rss"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Recent Source", url, enabled = true)
        assertTrue(okInsert)
        val inserted = SourceRepository.getSourceByUrl(context, dbState, url)
        assertNotNull(inserted)

        val nowIso = SourceOperationalDataRepository.getCurrentIsoTimestamp()
        SourceOperationalDataRepository.setSourceFetch(context, dbState, inserted!!.id!!, nowIso)

        val (ok, reason) = SourceUpdater.updateSource(context, dbState, inserted)
        assertFalse(ok)
        assertEquals("Source was fetched recently (less than 1 hour ago)", reason)
    }

    @Test
    fun `update with force=true proceeds even when source was fetched recently`() = runBlocking {
        val url = "https://forced.test/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Forced Source", url, enabled = true)
        assertTrue(okInsert)
        val inserted = SourceRepository.getSourceByUrl(context, dbState, url)
        assertNotNull(inserted)

        val nowIso = SourceOperationalDataRepository.getCurrentIsoTimestamp()
        SourceOperationalDataRepository.setSourceFetch(context, dbState, inserted!!.id!!, nowIso)

        val rssXml = """
            <rss version="2.0">
              <channel>
                <title>Forced Feed Title</title>
                <link>https://forced.test</link>
                <item>
                  <title>Forced Item 1</title>
                  <link>https://forced.test/item1</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val fakeResponse = PageResponseObject(
            statusCode = 200,
            headers = mapOf("Content-Type" to listOf("application/rss+xml")),
            text = rssXml
        )
        val rssPage = RssPage(url, rssXml)

        val fakeUrl = object : Url(url) {
            override suspend fun getResponse(acceptHeader: String?): PageResponseObject = fakeResponse
            override fun getCachedResponse(): PageResponseObject = fakeResponse
            override suspend fun getPage(): Page = rssPage
            override suspend fun getEntries(): List<Entry> = rssPage.getEntries()
            override suspend fun getTitle(): String? = "Forced Feed Title"
        }

        val (ok, msg) = SourceUpdater(
            context = context,
            activeDatabaseState = dbState,
            source = inserted,
            urlFactory = { fakeUrl },
            force = true
        ).update()

        assertTrue(msg, ok)
        assertTrue(msg.contains("1 entries"))
    }

    @Test
    fun `update successfully fetches and inserts entries with rules filtering`() = runBlocking {
        val url = "https://feed.test/rss.xml"
        val (okInsert, _) = SourceRepository.insertSource(context, dbState, "Test Feed", url, enabled = true)
        assertTrue(okInsert)
        val source = SourceRepository.getSourceByUrl(context, dbState, url)!!

        // Add a block rule for spam links
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        EntryRulesRepository.ensureTableExists(db)
        db.close()

        EntryRulesRepository.insertRule(
            context = context,
            activeDatabaseState = dbState,
            rule = EntryRule(
                rule_name = "Block Spam",
                trigger_rule_url = ".*spam.*",
                block = true,
                enabled = true
            )
        )

        val rssXml = """
            <rss version="2.0">
              <channel>
                <title>Updated Feed Title</title>
                <link>https://feed.test</link>
                <item>
                  <title>Valid Item 1</title>
                  <link>https://feed.test/item1</link>
                </item>
                <item>
                  <title>Spam Item</title>
                  <link>https://feed.test/spam/item2</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val fakeResponse = PageResponseObject(
            statusCode = 200,
            headers = mapOf("Content-Type" to listOf("application/rss+xml")),
            text = rssXml
        )
        val rssPage = RssPage(url, rssXml)

        val fakeUrl = object : Url(url) {
            override suspend fun getResponse(acceptHeader: String?): PageResponseObject {
                return fakeResponse
            }
            override fun getCachedResponse(): PageResponseObject {
                return fakeResponse
            }
            override suspend fun getPage(): Page {
                return rssPage
            }
            override suspend fun getEntries(): List<Entry> {
                return rssPage.getEntries()
            }
            override suspend fun getTitle(): String? = "Updated Feed Title"
        }

        val (ok, msg) = SourceUpdater(
            context = context,
            activeDatabaseState = dbState,
            source = source,
            urlFactory = { fakeUrl }
        ).update()

        assertTrue(msg, ok)
        assertTrue(msg.contains("1 entries"))

        // Verify source metadata updated
        val updatedSource = SourceRepository.getSourceById(context, dbState, source.id!!)
        assertEquals("Updated Feed Title", updatedSource?.title)

        // Verify blocked rule triggered hit counter
        val rules = EntryRulesRepository.getRules(context, dbState)
        val blockRule = rules.first { it.rule_name == "Block Spam" }
        assertEquals(1, blockRule.trigger_text_hits)
    }

    @Test
    fun `matchesRuleUrl tests regex and wildcard matching`() {
        assertTrue(SourceUpdater.matchesRuleUrl("https://example.com/ads/123", ".*ads.*"))
        assertFalse(SourceUpdater.matchesRuleUrl("https://example.com/news/123", ".*ads.*"))
        assertTrue(SourceUpdater.matchesRuleUrl("https://example.com/track", "*track*"))
        assertFalse(SourceUpdater.matchesRuleUrl("", ".*"))
        assertFalse(SourceUpdater.matchesRuleUrl("https://example.com", ""))
    }
}
