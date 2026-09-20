package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.RepositoryTestHelper
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
 * Unit tests for [OpmlToDatabase].
 *
 * Uses Robolectric so [Context] and the asset manager are available without a device.
 * [RepositoryTestHelper] supplies a writable copy of `table.db` for each test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpmlToDatabaseTest {

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

    // ── parse() ───────────────────────────────────────────────────────────────

    /**
     * Opening `assets/example.opml` via the Robolectric asset manager and parsing it
     * must produce at least one [Source] with a non-blank URL.
     */
    @Test
    fun `parse returns non-empty source list for example opml asset`() {
        val sources: List<Source> = context.assets.open("example.opml").use { stream ->
            OpmlToDatabase.parse(stream)
        }

        assertTrue("Expected at least one source in example.opml", sources.isNotEmpty())
        assertTrue(
            "Every parsed source must have a non-blank URL",
            sources.all { it.url.isNotBlank() }
        )
    }

    /**
     * Every parsed source should have a non-blank title (the `text` attribute).
     */
    @Test
    fun `parse extracts title for each source`() {
        val sources: List<Source> = context.assets.open("example.opml").use { stream ->
            OpmlToDatabase.parse(stream)
        }

        assertTrue("Expected at least one source", sources.isNotEmpty())
        assertTrue(
            "Every parsed source must have a non-blank title",
            sources.all { it.title.isNotBlank() }
        )
    }

    /**
     * RSS-type outlines must be mapped to [SourceRepository.SOURCE_TYPE_RSS].
     */
    @Test
    fun `parse maps rss type outlines to SOURCE_TYPE_RSS`() {
        val sources: List<Source> = context.assets.open("example.opml").use { stream ->
            OpmlToDatabase.parse(stream)
        }

        assertTrue("Expected at least one source", sources.isNotEmpty())
        val rssOutlines = sources.filter { it.source_type == SourceRepository.SOURCE_TYPE_RSS }
        assertTrue(
            "At least one source should be typed as RSS",
            rssOutlines.isNotEmpty()
        )
    }

    /**
     * All URLs returned by the parser must use http or https.
     */
    @Test
    fun `parse returns only http or https urls`() {
        val sources: List<Source> = context.assets.open("example.opml").use { stream ->
            OpmlToDatabase.parse(stream)
        }

        val invalidUrls = sources.filter { source ->
            !source.url.startsWith("http://") && !source.url.startsWith("https://")
        }
        assertTrue(
            "All source URLs must be http/https, but found: ${invalidUrls.map { it.url }}",
            invalidUrls.isEmpty()
        )
    }

    /**
     * Parsing an OPML file with nested group outlines should propagate the group name
     * into each child source's [Source.auto_tag].
     */
    @Test
    fun `parse propagates group outline text into auto_tag`() {
        val opmlXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <opml version="1.0">
              <head><title>Test</title></head>
              <body>
                <outline text="Technology">
                  <outline type="rss" text="Tech Feed" xmlUrl="https://tech.example.com/feed.rss"/>
                </outline>
                <outline type="rss" text="Ungrouped" xmlUrl="https://alone.example.com/feed.rss"/>
              </body>
            </opml>
        """.trimIndent()

        val sources = OpmlToDatabase.parse(opmlXml.byteInputStream())

        assertEquals("Expected 2 sources", 2, sources.size)
        val tech = sources.first { it.url.contains("tech") }
        val ungrouped = sources.first { it.url.contains("alone") }

        assertEquals("Technology", tech.auto_tag)
        assertTrue(
            "Ungrouped source should have blank auto_tag, got '${ungrouped.auto_tag}'",
            ungrouped.auto_tag.isBlank()
        )
    }

    // ── importToDatabase (suspend) ────────────────────────────────────────────

    /**
     * Importing `example.opml` into the database must succeed and persist
     * the expected number of sources in `sourcedatamodel`.
     */
    @Test
    fun `importToDatabase inserts sources from example opml into database`() = runBlocking {
        val result: OpmlImportResult = context.assets.open("example.opml").use { stream ->
            OpmlToDatabase.importToDatabase(
                context = context,
                inputStream = stream,
                activeDatabaseState = dbState
            )
        }

        assertTrue(
            "Import should produce no errors, but got: ${result.errors}",
            result.errors.isEmpty()
        )
        assertTrue("Parsed source list must not be empty", result.sources.isNotEmpty())
        assertEquals(
            "Inserted count must match parsed count",
            result.sources.size,
            result.inserted
        )

        // Verify through SourceRepository that rows are actually in the DB.
        val storedSources = SourceRepository.getAllSourcesWithOperationalData(context, dbState)
        assertEquals(
            "Database source count must equal imported source count",
            result.inserted,
            storedSources.size
        )
    }

    /**
     * Importing with a null (or read-only) database state must return an error and
     * insert no sources.
     */
    @Test
    fun `importToDatabase returns error for null database state`() = runBlocking {
        val result: OpmlImportResult = context.assets.open("example.opml").use { stream ->
            OpmlToDatabase.importToDatabase(
                context = context,
                inputStream = stream,
                activeDatabaseState = null
            )
        }

        assertEquals("Expected 0 inserted for null state", 0, result.inserted)
        assertTrue("Expected errors list to be non-empty", result.errors.isNotEmpty())
    }

    /**
     * Importing malformed XML must return an error and insert no sources.
     */
    @Test
    fun `importToDatabase returns error for malformed XML`() = runBlocking {
        val malformed = "<opml><body><outline broken".byteInputStream()

        val result: OpmlImportResult = OpmlToDatabase.importToDatabase(
            context = context,
            inputStream = malformed,
            activeDatabaseState = dbState
        )

        assertEquals("Expected 0 inserted for malformed XML", 0, result.inserted)
        assertTrue("Expected errors for malformed XML", result.errors.isNotEmpty())
    }

    /**
     * Importing an OPML with no feed outlines (empty body) must produce zero sources
     * and zero inserts with no errors.
     */
    @Test
    fun `importToDatabase handles opml with no feed outlines`() = runBlocking {
        val emptyOpml = """
            <?xml version="1.0" encoding="utf-8"?>
            <opml version="1.0">
              <head><title>Empty</title></head>
              <body/>
            </opml>
        """.trimIndent().byteInputStream()

        val result: OpmlImportResult = OpmlToDatabase.importToDatabase(
            context = context,
            inputStream = emptyOpml,
            activeDatabaseState = dbState
        )

        assertEquals("Expected 0 sources for empty OPML", 0, result.sources.size)
        assertEquals("Expected 0 inserted for empty OPML", 0, result.inserted)
        assertTrue("Expected no errors for empty OPML", result.errors.isEmpty())
    }
}
