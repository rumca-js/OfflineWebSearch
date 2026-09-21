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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Unit tests for [SourceJsonToDatabase].
 *
 * Uses Robolectric so [Context] and the asset manager are available without a device.
 * [RepositoryTestHelper] supplies a writable copy of `table.db` for each test.
 *
 * The `example_sources.json` bundled asset is used as the reference JSON dataset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceJsonToDatabaseTest {

    private lateinit var context: Context
    private lateinit var dbState: DatabaseState
    private lateinit var dbFile: File

    /** Asset name used as the reference JSON dataset. */
    private val jsonAsset = "example_sources.json"

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

    // ── parse ─────────────────────────────────────────────────────────────

    /**
     * Parsing the bundled `example_sources.json` asset via an [InputStream] must return
     * at least one source with a non-blank URL.
     */
    @Test
    fun `parse InputStream returns non-empty sources with urls`() {
        val sources: List<Source> = context.assets.open(jsonAsset).use { stream ->
            SourceJsonToDatabase.parse(stream)
        }

        assertTrue("Expected at least one source in $jsonAsset", sources.isNotEmpty())
        assertTrue(
            "Every source must have a non-blank URL",
            sources.all { it.url.isNotBlank() }
        )
    }

    /**
     * Parsing a raw JSON string must produce the same count as parsing the equivalent stream.
     */
    @Test
    fun `parse String produces same count as InputStream parse`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val fromStream = SourceJsonToDatabase.parse(jsonText.byteInputStream())
        val fromString = SourceJsonToDatabase.parse(jsonText)

        assertEquals(
            "String and stream parse must produce the same count",
            fromStream.size, fromString.size
        )
    }

    /**
     * Parsing an empty JSON array must return an empty list without errors.
     */
    @Test
    fun `parse empty array returns empty list`() {
        val sources = SourceJsonToDatabase.parse("[]".byteInputStream())
        assertTrue("Expected empty list for empty JSON array", sources.isEmpty())
    }

    /**
     * Parsing malformed JSON must throw a serialization exception.
     */
    @Test(expected = Exception::class)
    fun `parse throws on malformed JSON`() {
        SourceJsonToDatabase.parse("{not valid json".byteInputStream())
    }

    /**
     * The parser must tolerate extra fields present in linkarchivetools exports
     * (e.g. `category_name`, `fetch_period`, `proxy_location`) without throwing.
     */
    @Test
    fun `parse tolerates extra fields from linkarchivetools export`() {
        val jsonWithExtras = """
            [
              {
                "id": 1,
                "enabled": true,
                "url": "https://example.com/feed.rss",
                "title": "Example",
                "favicon": "",
                "source_type": "BaseRssPlugin",
                "age": 0,
                "auto_tag": "",
                "language": "en",
                "category_name": "Tech",
                "subcategory_name": "News",
                "export_to_cms": true,
                "remove_after_days": 30,
                "fetch_period": 3600,
                "proxy_location": ""
              }
            ]
        """.trimIndent()

        val sources = SourceJsonToDatabase.parse(jsonWithExtras)

        assertEquals("Expected exactly one source", 1, sources.size)
        assertEquals("https://example.com/feed.rss", sources.first().url)
    }

    /**
     * Every parsed source from the asset file must have a non-blank title.
     */
    @Test
    fun `parse extracts title for each source`() {
        val sources: List<Source> = context.assets.open(jsonAsset).use { stream ->
            SourceJsonToDatabase.parse(stream)
        }

        assertTrue("Expected at least one source", sources.isNotEmpty())
        assertTrue(
            "Every parsed source must have a non-blank title",
            sources.all { it.title.isNotBlank() }
        )
    }

    // ── parseZip ──────────────────────────────────────────────────────────────

    /**
     * A ZIP archive containing a single JSON file must parse the same sources
     * as reading that JSON directly.
     */
    @Test
    fun `parseZip returns sources from JSON files inside archive`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip("sources.json" to jsonText)

        val errors = mutableListOf<String>()
        val sources = ZipInputStream(zipBytes.inputStream()).use { zis ->
            SourceJsonToDatabase.parseZip(zis, errors)
        }

        val expected = SourceJsonToDatabase.parse(jsonText)
        assertEquals(
            "ZIP parse must yield same count as direct JSON parse",
            expected.size, sources.size
        )
        assertTrue("Expected no errors for valid zip", errors.isEmpty())
    }

    /**
     * A ZIP containing multiple JSON files must merge all their sources.
     */
    @Test
    fun `parseZip merges sources from multiple JSON files`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip(
            "a.json" to jsonText,
            "b.json" to jsonText
        )

        val expected = SourceJsonToDatabase.parse(jsonText)
        val errors = mutableListOf<String>()
        val sources = ZipInputStream(zipBytes.inputStream()).use { zis ->
            SourceJsonToDatabase.parseZip(zis, errors)
        }

        assertEquals(
            "Merged count should be 2× single-file count",
            expected.size * 2, sources.size
        )
    }

    /**
     * Non-JSON entries inside the ZIP must be silently skipped.
     */
    @Test
    fun `parseZip skips non-json entries`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip(
            "readme.txt" to "ignore me",
            "sources.json" to jsonText
        )

        val expected = SourceJsonToDatabase.parse(jsonText)
        val sources = ZipInputStream(zipBytes.inputStream()).use { zis ->
            SourceJsonToDatabase.parseZip(zis)
        }

        assertEquals("Only .json files should be parsed", expected.size, sources.size)
    }

    // ── importToDatabase (suspend) ────────────────────────────────────────

    /**
     * Importing `example_sources.json` into the database must succeed and persist
     * the expected number of sources in `sourcedatamodel`.
     */
    @Test
    fun `importToDatabase inserts sources from asset JSON`() = runBlocking {
        val result: SourceJsonImportResult = context.assets.open(jsonAsset).use { stream ->
            SourceJsonToDatabase.importToDatabase(
                context = context,
                inputStream = stream,
                activeDatabaseState = dbState
            )
        }

        assertTrue("Expected no errors, got: ${result.errors}", result.errors.isEmpty())
        assertTrue("Expected at least one source", result.sources.isNotEmpty())
        assertEquals(
            "inserted count must equal parsed count",
            result.sources.size, result.inserted
        )

        // Verify through SourceRepository that rows are actually in the DB.
        val storedSources = SourceRepository.getAllSourcesWithOperationalData(context, dbState)
        assertEquals(
            "Database source count must equal imported source count",
            result.inserted, storedSources.size
        )
    }

    /**
     * Importing with a null database state must return an error and insert no sources.
     */
    @Test
    fun `importToDatabase returns error for null database state`() = runBlocking {
        val result: SourceJsonImportResult = context.assets.open(jsonAsset).use { stream ->
            SourceJsonToDatabase.importToDatabase(
                context = context,
                inputStream = stream,
                activeDatabaseState = null
            )
        }

        assertEquals("Expected 0 inserted for null state", 0, result.inserted)
        assertTrue("Expected non-empty errors list", result.errors.isNotEmpty())
    }

    /**
     * Importing malformed JSON must return an error and insert no sources.
     */
    @Test
    fun `importToDatabase returns error for malformed JSON`() = runBlocking {
        val result: SourceJsonImportResult = SourceJsonToDatabase.importToDatabase(
            context = context,
            inputStream = "{bad json".byteInputStream(),
            activeDatabaseState = dbState
        )

        assertEquals("Expected 0 inserted for malformed JSON", 0, result.inserted)
        assertTrue("Expected errors for malformed JSON", result.errors.isNotEmpty())
    }

    /**
     * Importing an empty JSON array must produce zero sources and no errors.
     */
    @Test
    fun `importToDatabase handles empty JSON array`() = runBlocking {
        val result: SourceJsonImportResult = SourceJsonToDatabase.importToDatabase(
            context = context,
            inputStream = "[]".byteInputStream(),
            activeDatabaseState = dbState
        )

        assertEquals("Expected 0 sources for empty array", 0, result.sources.size)
        assertEquals("Expected 0 inserted for empty array", 0, result.inserted)
        assertTrue("Expected no errors for empty array", result.errors.isEmpty())
    }

    // ── importZipToDatabase (suspend) ─────────────────────────────────────────

    /**
     * Importing a ZIP archive containing the asset JSON must insert all sources.
     */
    @Test
    fun `importZipToDatabase inserts sources from zip containing JSON`() = runBlocking {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip("sources.json" to jsonText)

        val result: SourceJsonImportResult = ZipInputStream(zipBytes.inputStream()).use { zis ->
            SourceJsonToDatabase.importZipToDatabase(
                context = context,
                zipInputStream = zis,
                activeDatabaseState = dbState
            )
        }

        assertTrue("Expected no errors, got: ${result.errors}", result.errors.isEmpty())
        assertTrue("Expected at least one source", result.sources.isNotEmpty())
        assertEquals(
            "inserted count must equal parsed count",
            result.sources.size, result.inserted
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds an in-memory ZIP archive from the given [nameToContent] pairs and
     * returns the raw bytes.
     */
    private fun buildZip(vararg nameToContent: Pair<String, String>): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in nameToContent) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
