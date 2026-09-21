package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.RepositoryTestHelper
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository
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
 * Unit tests for [EntryJsonToDatabase].
 *
 * Uses Robolectric for [Context] and asset manager access.
 * [RepositoryTestHelper] provides a writable copy of `table.db` for each test.
 *
 * The `places_10.json` bundled asset is used as the reference JSON dataset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryJsonToDatabaseTest {

    private lateinit var context: Context
    private lateinit var dbState: DatabaseState
    private lateinit var dbFile: File

    /** Asset name used as the reference JSON dataset. */
    private val jsonAsset = "places_10.json"

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
     * Parsing the bundled `places_10.json` asset must return at least one entry
     * and every entry must have a non-blank link.
     */
    @Test
    fun `parse InputStream returns non-empty entries with links`() {
        val entries: List<Entry> = context.assets.open(jsonAsset).use { stream ->
            EntryJsonToDatabase.parse(stream)
        }

        assertTrue("Expected at least one entry in $jsonAsset", entries.isNotEmpty())
        assertTrue(
            "Every entry must have a non-blank link",
            entries.all { !it.link.isNullOrBlank() }
        )
    }

    /**
     * Parsing a raw JSON string must produce the same count as parsing the equivalent file.
     */
    @Test
    fun `parse String produces same count as InputStream parse`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val fromStream = EntryJsonToDatabase.parse(jsonText.byteInputStream())
        val fromString = EntryJsonToDatabase.parse(jsonText)

        assertEquals("String and stream parse must produce the same count",
            fromStream.size, fromString.size)
    }

    /**
     * Parsing an empty JSON array must return an empty list without errors.
     */
    @Test
    fun `parse empty array returns empty list`() {
        val entries = EntryJsonToDatabase.parse("[]".byteInputStream())
        assertTrue("Expected empty list for empty JSON array", entries.isEmpty())
    }

    /**
     * Parsing malformed JSON must throw a serialization exception.
     */
    @Test(expected = Exception::class)
    fun `parse throws on malformed JSON`() {
        EntryJsonToDatabase.parse("{not valid json".byteInputStream())
    }

    // ── parseZip ──────────────────────────────────────────────────────────────

    /**
     * A ZIP archive containing a single JSON file must parse the same entries
     * as reading that JSON directly.
     */
    @Test
    fun `parseZip returns entries from JSON files inside archive`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip("entries.json" to jsonText)

        val errors = mutableListOf<String>()
        val entries = ZipInputStream(zipBytes.inputStream()).use { zis ->
            EntryJsonToDatabase.parseZip(zis, errors)
        }

        val expected = EntryJsonToDatabase.parse(jsonText)
        assertEquals("ZIP parse must yield same count as direct JSON parse",
            expected.size, entries.size)
        assertTrue("Expected no errors for valid zip", errors.isEmpty())
    }

    /**
     * A ZIP containing multiple JSON files must merge all their entries.
     */
    @Test
    fun `parseZip merges entries from multiple JSON files`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip(
            "a.json" to jsonText,
            "b.json" to jsonText
        )

        val expected = EntryJsonToDatabase.parse(jsonText)
        val errors = mutableListOf<String>()
        val entries = ZipInputStream(zipBytes.inputStream()).use { zis ->
            EntryJsonToDatabase.parseZip(zis, errors)
        }

        assertEquals("Merged count should be 2× single-file count",
            expected.size * 2, entries.size)
    }

    /**
     * Non-JSON entries inside the ZIP must be silently skipped.
     */
    @Test
    fun `parseZip skips non-json entries`() {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip(
            "readme.txt" to "ignore me",
            "entries.json" to jsonText
        )

        val expected = EntryJsonToDatabase.parse(jsonText)
        val entries = ZipInputStream(zipBytes.inputStream()).use { zis ->
            EntryJsonToDatabase.parseZip(zis)
        }

        assertEquals("Only .json files should be parsed", expected.size, entries.size)
    }

    // ── importToDatabase (suspend) ────────────────────────────────────────

    /**
     * Importing the bundled JSON asset must insert all parsed entries into the database
     * and the count must be verifiable via [EntrySqliteRepository].
     */
    @Test
    fun `importToDatabase inserts entries from asset JSON`() = runBlocking {
        val result: EntryJsonImportResult = context.assets.open(jsonAsset).use { stream ->
            EntryJsonToDatabase.importToDatabase(
                context = context,
                inputStream = stream,
                activeDatabaseState = dbState
            )
        }

        assertTrue("Expected no errors, got: ${result.errors}", result.errors.isEmpty())
        assertTrue("Expected at least one entry", result.entries.isNotEmpty())
        assertEquals("inserted count must equal parsed count",
            result.entries.size, result.inserted)

        val dbCount = EntrySqliteRepository.countEntries(context, dbState)
        assertEquals("DB count must match inserted count", result.inserted, dbCount)
    }

    /**
     * Importing with a null database state must return an error and zero inserts.
     */
    @Test
    fun `importToDatabase returns error for null database state`() = runBlocking {
        val result: EntryJsonImportResult = context.assets.open(jsonAsset).use { stream ->
            EntryJsonToDatabase.importToDatabase(
                context = context,
                inputStream = stream,
                activeDatabaseState = null
            )
        }

        assertEquals("Expected 0 inserted for null state", 0, result.inserted)
        assertTrue("Expected non-empty errors list", result.errors.isNotEmpty())
    }

    /**
     * Importing malformed JSON must return an error and zero inserts.
     */
    @Test
    fun `importToDatabase returns error for malformed JSON`() = runBlocking {
        val result: EntryJsonImportResult = EntryJsonToDatabase.importToDatabase(
            context = context,
            inputStream = "{bad json".byteInputStream(),
            activeDatabaseState = dbState
        )

        assertEquals("Expected 0 inserted for malformed JSON", 0, result.inserted)
        assertTrue("Expected errors for malformed JSON", result.errors.isNotEmpty())
    }

    /**
     * Importing an empty JSON array must produce zero entries and no errors.
     */
    @Test
    fun `importToDatabase handles empty JSON array`() = runBlocking {
        val result: EntryJsonImportResult = EntryJsonToDatabase.importToDatabase(
            context = context,
            inputStream = "[]".byteInputStream(),
            activeDatabaseState = dbState
        )

        assertEquals("Expected 0 entries for empty array", 0, result.entries.size)
        assertEquals("Expected 0 inserted for empty array", 0, result.inserted)
        assertTrue("Expected no errors for empty array", result.errors.isEmpty())
    }

    // ── importZipToDatabase (suspend) ─────────────────────────────────────────

    /**
     * Importing a ZIP archive containing the asset JSON must insert all entries.
     */
    @Test
    fun `importZipToDatabase inserts entries from zip containing JSON`() = runBlocking {
        val jsonText = context.assets.open(jsonAsset).bufferedReader().readText()
        val zipBytes = buildZip("entries.json" to jsonText)

        val result: EntryJsonImportResult = ZipInputStream(zipBytes.inputStream()).use { zis ->
            EntryJsonToDatabase.importZipToDatabase(
                context = context,
                zipInputStream = zis,
                activeDatabaseState = dbState
            )
        }

        assertTrue("Expected no errors, got: ${result.errors}", result.errors.isEmpty())
        assertTrue("Expected at least one entry", result.entries.isNotEmpty())
        assertEquals("inserted count must equal parsed count",
            result.entries.size, result.inserted)
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
