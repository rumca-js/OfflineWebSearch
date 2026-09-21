package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Result of a JSON-to-database import operation for sources.
 *
 * @property sources  Parsed [Source] objects from the JSON source.
 * @property inserted Number of rows written to `sourcedatamodel` (-1 if the DB step was skipped).
 * @property errors   Human-readable error messages accumulated during parsing or import.
 */
data class SourceJsonImportResult(
    val sources: List<Source>,
    val inserted: Int,
    val errors: List<String>
)

/**
 * Intermediate representation of a source JSON object as exported by linkarchivetools.
 *
 * Contains all fields present in [example_sources.json][example_sources] to ensure
 * the parser does not fail on extra keys. Fields beyond what [Source] models are silently
 * ignored on conversion.
 */
@Serializable
private data class JsonSourceEntry(
    val id: Long? = null,
    val enabled: Boolean = true,
    val url: String = "",
    val title: String = "",
    val favicon: String = "",
    val source_type: String? = null,
    val age: Int? = 0,
    val auto_tag: String = "",
    val language: String = "",
    // Extra fields present in linkarchivetools exports – absorbed and ignored.
    val category_name: String? = null,
    val subcategory_name: String? = null,
    val export_to_cms: Boolean? = null,
    val remove_after_days: Int? = null,
    val fetch_period: Int? = null,
    val proxy_location: String? = null
) {
    /** Converts this intermediate entry into a [Source] record. */
    fun toSource(): Source = Source(
        id = id,
        enabled = enabled,
        url = url,
        title = title,
        favicon = favicon,
        source_type = source_type,
        age = age ?: 0,
        auto_tag = auto_tag,
        language = language
    )
}

/**
 * Converts a JSON source list (the format used by linkarchivetools exports and the application's
 * own asset file `example_sources.json`) into [Source] records and optionally persists them
 * into a SQLite database via [SourceRepository].
 *
 * ### Supported input formats
 * - **Plain JSON** – a JSON array of source objects (`[{…}, …]`), readable from a [File],
 *   [InputStream], or raw [String].
 * - **ZIP archive** – a `.zip` file whose entries are `.json` files in the array format above.
 *   Every `.json` file inside the archive is parsed and merged into a single result.
 *
 * ### Relationship to [OpmlToDatabase]
 * Both converters produce [Source] records and persist via [SourceRepository]. This converter
 * handles JSON-format exports while [OpmlToDatabase] handles OPML/XML exports.
 */
object SourceJsonToDatabase : FileToDatabaseInterface<Source, SourceJsonImportResult>{

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a JSON array of sources and returns the resulting list.
     *
     * @param inputStream Readable JSON stream; **not** closed by this function.
     * @return List of [Source] objects decoded from the stream.
     * @throws kotlinx.serialization.SerializationException if the JSON is malformed.
     */
    override fun parse(inputStream: InputStream): List<Source> {
        val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
        return parse(text)
    }

    /**
     * Parses [jsonFile] as a JSON array of sources and returns the resulting list.
     *
     * @param jsonFile File containing a JSON array of source objects.
     * @return List of [Source] objects decoded from the file.
     */
    override fun parse(jsonFile: File): List<Source> =
        jsonFile.inputStream().use { parse(it) }

    /**
     * Parses [jsonText] as a JSON array of sources and returns the resulting list.
     *
     * @param jsonText Raw JSON string containing an array of source objects.
     * @return List of [Source] objects decoded from the string.
     */
    fun parse(jsonText: String): List<Source> =
        jsonConfig.decodeFromString<List<JsonSourceEntry>>(jsonText).map { it.toSource() }

    /**
     * Parses all `.json` entries inside [zipInputStream] and returns a merged list of sources.
     * Files that fail to parse are skipped and their errors are collected in [errors].
     *
     * @param zipInputStream Open [ZipInputStream]; **not** closed by this function.
     * @param errors         Mutable list that receives any per-file error messages.
     * @return Merged list of [Source] objects from all successfully parsed JSON files.
     */
    fun parseZip(zipInputStream: ZipInputStream, errors: MutableList<String> = mutableListOf()): List<Source> {
        val results = mutableListOf<Source>()
        var entry = zipInputStream.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                try {
                    // Read without closing the ZipInputStream between entries.
                    val text = zipInputStream.bufferedReader(Charsets.UTF_8).readText()
                    results.addAll(jsonConfig.decodeFromString<List<JsonSourceEntry>>(text).map { it.toSource() })
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
     * Parses all `.json` entries inside [zipFile] and returns a merged list of sources.
     *
     * @param zipFile  ZIP file to read.
     * @param errors   Mutable list that receives any per-file error messages.
     * @return Merged list of [Source] objects from all successfully parsed JSON files.
     */
    fun parseZip(zipFile: File, errors: MutableList<String> = mutableListOf()): List<Source> =
        ZipInputStream(zipFile.inputStream().buffered()).use { parseZip(it, errors) }

    // ── Import to database ────────────────────────────────────────────────────

    /**
     * Parses [inputStream] as a JSON array and inserts the sources into [db].
     *
     * @param inputStream Readable JSON stream; **not** closed by this function.
     * @param db          Open, writable [SQLiteDatabase].
     * @return [SourceJsonImportResult] with parsed sources, insert count, and errors.
     */
    override fun importToDatabase(inputStream: InputStream, db: SQLiteDatabase): SourceJsonImportResult {
        val errors = mutableListOf<String>()
        val sources: List<Source> = try {
            parse(inputStream)
        } catch (e: Exception) {
            errors.add("Failed to parse JSON: ${e.message}")
            return SourceJsonImportResult(emptyList(), 0, errors)
        }
        if (sources.isEmpty()) return SourceJsonImportResult(emptyList(), 0, errors)

        val inserted = insertSources(db, sources, errors)
        return SourceJsonImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses [jsonFile] and inserts the sources into [dbFile].
     *
     * @param jsonFile JSON file to read.
     * @param dbFile   SQLite database file to write into.
     * @return [SourceJsonImportResult] with parsed sources, insert count, and errors.
     */
    override fun importToDatabase(jsonFile: File, dbFile: File): SourceJsonImportResult {
        val errors = mutableListOf<String>()
        val sources: List<Source> = try {
            parse(jsonFile)
        } catch (e: Exception) {
            errors.add("Failed to parse JSON: ${e.message}")
            return SourceJsonImportResult(emptyList(), 0, errors)
        }
        if (sources.isEmpty()) return SourceJsonImportResult(emptyList(), 0, errors)

        val inserted = insertSourcesToFile(dbFile, sources, errors)
        return SourceJsonImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses all `.json` files inside [zipFile] and inserts the merged source list into [dbFile].
     *
     * @param zipFile  ZIP archive containing one or more JSON files.
     * @param dbFile   SQLite database file to write into.
     * @return [SourceJsonImportResult] with parsed sources, insert count, and errors.
     */
    fun importZipToDatabase(zipFile: File, dbFile: File): SourceJsonImportResult {
        val errors = mutableListOf<String>()
        val sources = parseZip(zipFile, errors)
        if (sources.isEmpty()) return SourceJsonImportResult(emptyList(), 0, errors)

        val inserted = insertSourcesToFile(dbFile, sources, errors)
        return SourceJsonImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses [inputStream] as a JSON array and inserts the sources into the database
     * referenced by [activeDatabaseState].
     *
     * @param context               Application context.
     * @param inputStream           Readable JSON stream; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be writable SQLite.
     * @return [SourceJsonImportResult] with parsed sources, insert count, and errors.
     */
    override suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        activeDatabaseState: DatabaseState?
    ): SourceJsonImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        val dbFile = resolveDbFile(context, activeDatabaseState, errors)
            ?: return@withContext SourceJsonImportResult(emptyList(), 0, errors)

        val sources: List<Source> = try {
            parse(inputStream)
        } catch (e: Exception) {
            errors.add("Failed to parse JSON: ${e.message}")
            return@withContext SourceJsonImportResult(emptyList(), 0, errors)
        }

        if (sources.isEmpty()) return@withContext SourceJsonImportResult(emptyList(), 0, errors)

        val inserted = insertSourcesToFile(dbFile, sources, errors)
        SourceJsonImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses all `.json` files inside [zipInputStream] and inserts the sources into the
     * database referenced by [activeDatabaseState].
     *
     * @param context               Application context.
     * @param zipInputStream        Open [ZipInputStream]; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be writable SQLite.
     * @return [SourceJsonImportResult] with parsed sources, insert count, and errors.
     */
    suspend fun importZipToDatabase(
        context: Context,
        zipInputStream: ZipInputStream,
        activeDatabaseState: DatabaseState?
    ): SourceJsonImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        val dbFile = resolveDbFile(context, activeDatabaseState, errors)
            ?: return@withContext SourceJsonImportResult(emptyList(), 0, errors)

        val sources = parseZip(zipInputStream, errors)
        if (sources.isEmpty()) return@withContext SourceJsonImportResult(emptyList(), 0, errors)

        val inserted = insertSourcesToFile(dbFile, sources, errors)
        SourceJsonImportResult(sources = sources, inserted = inserted, errors = errors)
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
     * Inserts [sources] into [db], capturing any exception in [errors].
     * @return Number of successfully inserted rows, or 0 on failure.
     */
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

    /**
     * Opens [dbFile] read-write, inserts [sources], then closes the database.
     * @return Number of successfully inserted rows, or 0 on failure.
     */
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
