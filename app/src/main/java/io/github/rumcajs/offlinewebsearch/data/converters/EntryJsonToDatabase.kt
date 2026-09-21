package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Result of a JSON-to-database import operation.
 *
 * @property entries  Parsed [Entry] objects from the JSON source.
 * @property inserted Number of rows written to `linkdatamodel` (-1 if the DB step was skipped).
 * @property errors   Human-readable error messages accumulated during parsing or import.
 */
data class EntryJsonImportResult(
    val entries: List<Entry>,
    val inserted: Int,
    val errors: List<String>
)

/**
 * Converts a JSON entry list (the format used by linkarchivetools exports and the application's
 * own asset files) into [Entry] records and optionally persists them into a SQLite database
 * via [EntrySqliteRepository].
 *
 * ### Supported input formats
 * - **Plain JSON** – a JSON array of entry objects (`[{…}, …]`), readable from a [File] or [InputStream].
 * - **ZIP archive** – a `.zip` file whose entries are `.json` files in the array format above.
 *   Every `.json` file inside the archive is parsed and merged into a single result.
 *
 * ### Relationship to [InternetDatabaseBuilder]
 * This converter extracts and centralises the parsing logic that was previously inlined inside
 * `InternetDatabaseBuilder.onPopulatingTable` and `AbstractDatabaseBuilder.unzipAndPopulateJsonToDb`,
 * making it reusable and independently testable.
 *
 * @see FileToDatabaseInterface
 */
object EntryJsonToDatabase : FileToDatabaseInterface<Entry, EntryJsonImportResult> {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a JSON array of entries and returns the resulting list.
     *
     * @param inputStream Readable JSON stream; **not** closed by this function.
     * @return List of [Entry] objects decoded from the stream.
     * @throws kotlinx.serialization.SerializationException if the JSON is malformed.
     */
    override fun parse(inputStream: InputStream): List<Entry> {
        val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
        return jsonConfig.decodeFromString(text)
    }

    /**
     * Parses [file] as a JSON array of entries and returns the resulting list.
     *
     * @param file File containing a JSON array of entry objects.
     * @return List of [Entry] objects decoded from the file.
     */
    override fun parse(file: File): List<Entry> =
        file.inputStream().use { parse(it) }

    /**
     * Parses [jsonText] as a JSON array of entries and returns the resulting list.
     *
     * @param jsonText Raw JSON string containing an array of entry objects.
     * @return List of [Entry] objects decoded from the string.
     */
    fun parse(jsonText: String): List<Entry> =
        jsonConfig.decodeFromString(jsonText)

    /**
     * Parses all `.json` entries inside [zipInputStream] and returns a merged list of entries.
     * Files that fail to parse are skipped and their errors are collected in [errors].
     *
     * @param zipInputStream Open [ZipInputStream]; **not** closed by this function.
     * @param errors         Mutable list that receives any per-file error messages.
     * @return Merged list of [Entry] objects from all successfully parsed JSON files.
     */
    fun parseZip(zipInputStream: ZipInputStream, errors: MutableList<String> = mutableListOf()): List<Entry> {
        val results = mutableListOf<Entry>()
        var entry = zipInputStream.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                try {
                    // Read without closing the ZipInputStream between entries.
                    val text = zipInputStream.bufferedReader(Charsets.UTF_8).readText()
                    results.addAll(jsonConfig.decodeFromString<List<Entry>>(text))
                } catch (e: Exception) {
                    errors.add("Failed to parse zip entry '${entry.name}': ${e.message}")
                }
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
        return results
    }

    /**
     * Parses all `.json` entries inside [zipFile] and returns a merged list of entries.
     *
     * @param zipFile  ZIP file to read.
     * @param errors   Mutable list that receives any per-file error messages.
     * @return Merged list of [Entry] objects from all successfully parsed JSON files.
     */
    fun parseZip(zipFile: File, errors: MutableList<String> = mutableListOf()): List<Entry> =
        ZipInputStream(zipFile.inputStream().buffered()).use { parseZip(it, errors) }

    // ── Import to database ────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a JSON array and inserts the entries into [db].
     *
     * @param inputStream Readable JSON stream; **not** closed by this function.
     * @param db          Open, writable [SQLiteDatabase].
     * @return [EntryJsonImportResult] with parsed entries, insert count, and errors.
     */
    override fun importToDatabase(inputStream: InputStream, db: SQLiteDatabase): EntryJsonImportResult {
        val errors = mutableListOf<String>()
        val entries: List<Entry> = try {
            parse(inputStream)
        } catch (e: Exception) {
            errors.add("Failed to parse JSON: ${e.message}")
            return EntryJsonImportResult(emptyList(), 0, errors)
        }
        if (entries.isEmpty()) return EntryJsonImportResult(emptyList(), 0, errors)

        val inserted = insertEntries(db, entries, errors)
        return EntryJsonImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    /**
     * Parses [file] and inserts the entries into [dbFile].
     *
     * @param file   JSON file to read.
     * @param dbFile SQLite database file to write into.
     * @return [EntryJsonImportResult] with parsed entries, insert count, and errors.
     */
    override fun importToDatabase(file: File, dbFile: File): EntryJsonImportResult {
        val errors = mutableListOf<String>()
        val entries: List<Entry> = try {
            parse(file)
        } catch (e: Exception) {
            errors.add("Failed to parse JSON: ${e.message}")
            return EntryJsonImportResult(emptyList(), 0, errors)
        }
        if (entries.isEmpty()) return EntryJsonImportResult(emptyList(), 0, errors)

        val inserted = insertEntriesToFile(dbFile, entries, errors)
        return EntryJsonImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    /**
     * Parses all `.json` files inside [zipFile] and inserts the merged entry list into [dbFile].
     *
     * @param zipFile  ZIP archive containing one or more JSON files.
     * @param dbFile   SQLite database file to write into.
     * @return [EntryJsonImportResult] with parsed entries, insert count, and errors.
     */
    fun importZipToDatabase(zipFile: File, dbFile: File): EntryJsonImportResult {
        val errors = mutableListOf<String>()
        val entries = parseZip(zipFile, errors)
        if (entries.isEmpty()) return EntryJsonImportResult(emptyList(), 0, errors)

        val inserted = insertEntriesToFile(dbFile, entries, errors)
        return EntryJsonImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    /**
     * Parses [inputStream] as a JSON array and inserts the entries into the database
     * referenced by [activeDatabaseState].
     *
     * @param context               Application context.
     * @param inputStream           Readable JSON stream; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be writable SQLite.
     * @return [EntryJsonImportResult] with parsed entries, insert count, and errors.
     */
    override suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        activeDatabaseState: DatabaseState?
    ): EntryJsonImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        val dbFile = resolveDbFile(context, activeDatabaseState, errors)
            ?: return@withContext EntryJsonImportResult(emptyList(), 0, errors)

        val entries: List<Entry> = try {
            parse(inputStream)
        } catch (e: Exception) {
            errors.add("Failed to parse JSON: ${e.message}")
            return@withContext EntryJsonImportResult(emptyList(), 0, errors)
        }

        if (entries.isEmpty()) return@withContext EntryJsonImportResult(emptyList(), 0, errors)

        val inserted = insertEntriesToFile(dbFile, entries, errors)
        EntryJsonImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    /**
     * Parses all `.json` files inside [zipInputStream] and inserts the entries into the
     * database referenced by [activeDatabaseState].
     *
     * @param context               Application context.
     * @param zipInputStream        Open [ZipInputStream]; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be writable SQLite.
     * @return [EntryJsonImportResult] with parsed entries, insert count, and errors.
     */
    suspend fun importZipToDatabase(
        context: Context,
        zipInputStream: ZipInputStream,
        activeDatabaseState: DatabaseState?
    ): EntryJsonImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        val dbFile = resolveDbFile(context, activeDatabaseState, errors)
            ?: return@withContext EntryJsonImportResult(emptyList(), 0, errors)

        val entries = parseZip(zipInputStream, errors)
        if (entries.isEmpty()) return@withContext EntryJsonImportResult(emptyList(), 0, errors)

        val inserted = insertEntriesToFile(dbFile, entries, errors)
        EntryJsonImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Validates [activeDatabaseState] and resolves the backing [File].
     * Appends an error to [errors] and returns null if the state is unusable.
     */
    private fun resolveDbFile(
        context: Context,
        activeDatabaseState: DatabaseState?,
        errors: MutableList<String>
    ): File? {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            errors.add("Database is not writable")
            return null
        }
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) {
            errors.add("Database file not found: ${activeDatabaseState.localFileName}")
            return null
        }
        return file
    }

    /**
     * Inserts [entries] into [db], capturing any exception in [errors].
     * @return Number of successfully inserted rows, or 0 on failure.
     */
    private fun insertEntries(
        db: SQLiteDatabase,
        entries: List<Entry>,
        errors: MutableList<String>
    ): Int = try {
        EntrySqliteRepository.populateEntries(db, entries)
    } catch (e: Exception) {
        errors.add("Failed to insert entries: ${e.message}")
        0
    }

    /**
     * Opens [dbFile] read-write, inserts [entries], then closes the database.
     * @return Number of successfully inserted rows, or 0 on failure.
     */
    private fun insertEntriesToFile(
        dbFile: File,
        entries: List<Entry>,
        errors: MutableList<String>
    ): Int = try {
        EntrySqliteRepository.populateEntries(dbFile, entries)
    } catch (e: Exception) {
        errors.add("Failed to insert entries: ${e.message}")
        0
    }
}
