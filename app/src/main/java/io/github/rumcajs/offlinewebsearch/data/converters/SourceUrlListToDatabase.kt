package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Result of a plain-text URL list import for sources.
 *
 * @property sources  Parsed [Source] objects (one per non-blank URL line).
 * @property inserted Number of rows written to `sourcedatamodel` (-1 if the DB step was skipped).
 * @property errors   Human-readable error messages accumulated during parsing or import.
 */
data class SourceUrlListImportResult(
    val sources: List<Source>,
    val inserted: Int,
    val errors: List<String>
)

/**
 * Converts a plain-text, one-URL-per-line list of source addresses into [Source] records and
 * optionally persists them into a SQLite database via [SourceRepository].
 *
 * ### Supported input format
 * ```
 * https://example.com/feed.rss
 * https://another.example.com/atom.xml
 * # lines starting with '#' are treated as comments and ignored
 * ```
 * - Blank lines and lines whose trimmed form starts with `#` are skipped.
 * - Each remaining line is treated as the `url` field of a new [Source].
 * - `title` is left empty (the caller or a subsequent enrichment step can populate it).
 * - `source_type` defaults to [SourceRepository.SOURCE_TYPE_RSS].
 *
 * @see FileToDatabaseInterface
 */
object SourceUrlListToDatabase : FileToDatabaseInterface<Source, SourceUrlListImportResult> {

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a newline-separated URL list and returns [Source] records.
     *
     * @param inputStream Readable text stream; **not** closed by this function.
     * @return List of [Source] objects, one per valid URL line.
     */
    override fun parse(inputStream: InputStream): List<Source> =
        parseText(inputStream.bufferedReader(Charsets.UTF_8).readText())

    /**
     * Parses [file] as a newline-separated URL list and returns [Source] records.
     *
     * @param file Input text file.
     * @return List of [Source] objects, one per valid URL line.
     */
    override fun parse(file: File): List<Source> =
        file.inputStream().use { parse(it) }

    /**
     * Converts [text] (newline-separated URLs) into a list of [Source] records.
     * Skips blank lines and comment lines (lines whose trimmed form starts with `#`).
     *
     * @param text Raw plain-text content.
     * @return List of [Source] objects derived from valid URL lines.
     */
    fun parseText(text: String): List<Source> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { url ->
                Source(
                    url = url,
                    title = "",
                    enabled = true,
                    source_type = SourceRepository.SOURCE_TYPE_RSS
                )
            }
            .toList()

    // ── Import to database ────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a URL list and inserts the sources into [db].
     *
     * @param inputStream Readable text stream; **not** closed by this function.
     * @param db          Open, writable [SQLiteDatabase].
     * @return [SourceUrlListImportResult] with parsed sources, insert count, and errors.
     */
    override fun importToDatabase(inputStream: InputStream, db: SQLiteDatabase): SourceUrlListImportResult {
        val errors = mutableListOf<String>()
        val sources = parse(inputStream)
        if (sources.isEmpty()) return SourceUrlListImportResult(emptyList(), 0, errors)

        val inserted = insertSources(db, sources, errors)
        return SourceUrlListImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses [file] as a URL list and inserts the sources into [dbFile].
     *
     * @param file   Input text file.
     * @param dbFile SQLite database file to write into.
     * @return [SourceUrlListImportResult] with parsed sources, insert count, and errors.
     */
    override fun importToDatabase(file: File, dbFile: File): SourceUrlListImportResult {
        val errors = mutableListOf<String>()
        val sources = parse(file)
        if (sources.isEmpty()) return SourceUrlListImportResult(emptyList(), 0, errors)

        val inserted = insertSourcesToFile(dbFile, sources, errors)
        return SourceUrlListImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses [inputStream] as a URL list and inserts the sources into the database
     * referenced by [activeDatabaseState].
     *
     * @param context               Application context.
     * @param inputStream           Readable text stream; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be writable SQLite.
     * @return [SourceUrlListImportResult] with parsed sources, insert count, and errors.
     */
    override suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        activeDatabaseState: DatabaseState?
    ): SourceUrlListImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            errors.add("Database is not writable")
            return@withContext SourceUrlListImportResult(emptyList(), 0, errors)
        }
        val dbFile = File(context.filesDir, activeDatabaseState.localFileName)
        if (!dbFile.exists()) {
            errors.add("Database file not found: ${activeDatabaseState.localFileName}")
            return@withContext SourceUrlListImportResult(emptyList(), 0, errors)
        }

        val sources = parse(inputStream)
        if (sources.isEmpty()) return@withContext SourceUrlListImportResult(emptyList(), 0, errors)

        val inserted = insertSourcesToFile(dbFile, sources, errors)
        SourceUrlListImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun insertSources(
        db: SQLiteDatabase,
        sources: List<Source>,
        errors: MutableList<String>
    ): Int = try {
        SourceRepository.populateSources(db, sources)
    } catch (e: Exception) {
        errors.add("Failed to insert sources: ${e.message}")
        0
    }

    private fun insertSourcesToFile(
        dbFile: File,
        sources: List<Source>,
        errors: MutableList<String>
    ): Int = try {
        SourceRepository.populateSources(dbFile, sources)
    } catch (e: Exception) {
        errors.add("Failed to insert sources: ${e.message}")
        0
    }
}
