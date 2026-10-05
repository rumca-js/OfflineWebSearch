package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Result of a plain-text URL list import for entries.
 *
 * @property entries  Parsed [Entry] objects (one per non-blank URL line).
 * @property inserted Number of rows written to `linkdatamodel` (-1 if the DB step was skipped).
 * @property errors   Human-readable error messages accumulated during parsing or import.
 */
data class EntryUrlListImportResult(
    val entries: List<Entry>,
    val inserted: Int,
    val errors: List<String>
)

/**
 * Converts a plain-text, one-URL-per-line list of entry links into [Entry] records and
 * optionally persists them into a SQLite database via [EntrySqliteRepository].
 *
 * ### Supported input format
 * ```
 * https://example.com/article1
 * https://example.com/article2
 * # lines starting with '#' are treated as comments and ignored
 * ```
 * - Blank lines and lines whose trimmed form starts with `#` are skipped.
 * - Each remaining line is treated as the `link` field of a new [Entry].
 * - All other [Entry] fields default to `null` / their zero values.
 *
 * @see FileToDatabaseInterface
 */
object EntryUrlListToDatabase : FileToDatabaseInterface<Entry, EntryUrlListImportResult> {

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a newline-separated URL list and returns [Entry] records.
     *
     * @param inputStream Readable text stream; **not** closed by this function.
     * @return List of [Entry] objects, one per valid URL line.
     */
    override fun parse(inputStream: InputStream): List<Entry> =
        parseText(inputStream.bufferedReader(Charsets.UTF_8).readText())

    /**
     * Parses [file] as a newline-separated URL list and returns [Entry] records.
     *
     * @param file Input text file.
     * @return List of [Entry] objects, one per valid URL line.
     */
    override fun parse(file: File): List<Entry> =
        file.inputStream().use { parse(it) }

    /**
     * Converts [text] (newline-separated URLs) into a list of [Entry] records.
     * Skips blank lines and comment lines (lines whose trimmed form starts with `#`).
     *
     * @param text Raw plain-text content.
     * @return List of [Entry] objects, each with `link` set to the URL from that line.
     */
    fun parseText(text: String): List<Entry> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { url -> Entry(link = url) }
            .toList()

    // ── Import to database ────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a URL list and inserts the entries into [db].
     *
     * @param inputStream Readable text stream; **not** closed by this function.
     * @param db          Open, writable [SQLiteDatabase].
     * @return [EntryUrlListImportResult] with parsed entries, insert count, and errors.
     */
    override fun importToDatabase(inputStream: InputStream, db: SQLiteDatabase): EntryUrlListImportResult {
        val errors = mutableListOf<String>()
        val entries = parse(inputStream)
        if (entries.isEmpty()) return EntryUrlListImportResult(emptyList(), 0, errors)

        val inserted = insertEntries(db, entries, errors)
        return EntryUrlListImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    /**
     * Parses [file] as a URL list and inserts the entries into [dbFile].
     *
     * @param file   Input text file.
     * @param dbFile SQLite database file to write into.
     * @return [EntryUrlListImportResult] with parsed entries, insert count, and errors.
     */
    override fun importToDatabase(file: File, dbFile: File): EntryUrlListImportResult {
        val errors = mutableListOf<String>()
        val entries = parse(file)
        if (entries.isEmpty()) return EntryUrlListImportResult(emptyList(), 0, errors)

        val inserted = insertEntriesToFile(dbFile, entries, errors)
        return EntryUrlListImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    /**
     * Parses [inputStream] as a URL list and inserts the entries into the database
     * referenced by [activeDatabaseState].
     *
     * @param context               Application context.
     * @param inputStream           Readable text stream; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be writable SQLite.
     * @return [EntryUrlListImportResult] with parsed entries, insert count, and errors.
     */
    override suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        activeDatabaseState: DatabaseState?
    ): EntryUrlListImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            errors.add("Database is not writable")
            return@withContext EntryUrlListImportResult(emptyList(), 0, errors)
        }
        val dbFile = File(context.filesDir, activeDatabaseState.localFileName)
        if (!dbFile.exists()) {
            errors.add("Database file not found: ${activeDatabaseState.localFileName}")
            return@withContext EntryUrlListImportResult(emptyList(), 0, errors)
        }

        val entries = parse(inputStream)
        if (entries.isEmpty()) return@withContext EntryUrlListImportResult(emptyList(), 0, errors)

        val inserted = insertEntriesToFile(dbFile, entries, errors)
        EntryUrlListImportResult(entries = entries, inserted = inserted, errors = errors)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
