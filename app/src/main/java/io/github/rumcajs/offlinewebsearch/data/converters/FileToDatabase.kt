package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Dispatches file imports to the appropriate converter ([EntryJsonToDatabase], [SourceJsonToDatabase],
 * or [OpmlToDatabase]) based on the file extension and JSON content.
 *
 * Supported extensions:
 *  - `.entries`, `.sources`, `.json` -> [EntryJsonToDatabase] or [SourceJsonToDatabase] (detected by content or filename)
 *  - `.opml` -> [OpmlToDatabase]
 */
object FileToDatabase {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Determines whether the given [fileNameOrUrl] has a supported file extension (.json, .opml, .entries, .sources).
     *
     * @param fileNameOrUrl File path, name, or URL string to check.
     * @return true if the file extension is supported, false otherwise.
     */
    fun isSupported(fileNameOrUrl: String): Boolean {
        val lower = fileNameOrUrl.lowercase()
        return lower.endsWith(".json") || lower.endsWith(".opml") || lower.endsWith(".entries") || lower.endsWith(".sources")
    }

    /**
     * Determines whether JSON content represents sources (for [SourceJsonToDatabase])
     * rather than entries (for [EntryJsonToDatabase]).
     *
     * Inspects the JSON structure for a `"sources"` key or source attributes (e.g. `"source_type"`,
     * `"favicon"`, or `"url"` without `"link"`), falling back to [fileNameOrUrl] inspection.
     *
     * @param jsonText      Raw JSON content.
     * @param fileNameOrUrl Optional file name, path, or URL.
     * @return true if the JSON represents sources, false if it represents entries.
     */
    fun isSourceJson(jsonText: String, fileNameOrUrl: String? = null): Boolean {
        try {
            val element = jsonConfig.parseToJsonElement(jsonText)
            when (element) {
                is JsonObject -> {
                    if (element.containsKey("sources")) return true
                    if (element.containsKey("entries")) return false
                }
                is JsonArray -> {
                    val first = element.firstOrNull()
                    if (first is JsonObject) {
                        if (first.containsKey("source_type") || first.containsKey("favicon") ||
                            (first.containsKey("url") && !first.containsKey("link"))) {
                            return true
                        }
                        if (first.containsKey("link")) {
                            return false
                        }
                    }
                }
                else -> {}
            }
        } catch (_: Exception) {
            // If JSON is malformed, fall back to name heuristic.
        }

        if (fileNameOrUrl != null) {
            val lower = fileNameOrUrl.lowercase()
            if (lower.contains("source")) return true
        }

        return false
    }

    /**
     * Resolves the appropriate [FileToDatabaseInterface] converter for the given [fileNameOrUrl]
     * and optional [content] (used to disambiguate `.json` files into entries vs sources).
     *
     * @param fileNameOrUrl Name, path, or URL of the input file.
     * @param content       Optional raw text content (for JSON structure inspection).
     * @return The matching [FileToDatabaseInterface] converter.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun resolveConverter(fileNameOrUrl: String, content: String? = null): FileToDatabaseInterface<*, *> {
        val name = fileNameOrUrl.lowercase()
        return when {
            name.endsWith(".entries") -> EntryJsonToDatabase
            name.endsWith(".sources") -> SourceJsonToDatabase
            name.endsWith(".json") -> {
                if (content != null && isSourceJson(content, fileNameOrUrl)) {
                    SourceJsonToDatabase
                } else if (content == null && fileNameOrUrl.lowercase().contains("source")) {
                    SourceJsonToDatabase
                } else {
                    EntryJsonToDatabase
                }
            }
            name.endsWith(".opml") -> OpmlToDatabase
            else -> throw IllegalArgumentException("Unsupported file extension for: $fileNameOrUrl")
        }
    }

    /**
     * Parses and imports records from [file] into the SQLite database at [dbFile]
     * by resolving the converter based on file extension and content.
     *
     * @param file   Input file (.json, .opml, .entries, .sources).
     * @param dbFile SQLite database file to write into.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun importToDatabase(file: File, dbFile: File) {
        val content = if (file.name.lowercase().endsWith(".json")) file.readText(Charsets.UTF_8) else null
        resolveConverter(file.name, content).importToDatabase(file, dbFile)
    }

    /**
     * Parses and imports records from [inputStream] with the given [fileNameOrUrl]
     * into the open [db] SQLite database.
     *
     * @param inputStream   Readable input stream.
     * @param fileNameOrUrl Name or URL used to determine the file type.
     * @param db            Open writable SQLite database.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun importToDatabase(inputStream: InputStream, fileNameOrUrl: String, db: SQLiteDatabase) {
        val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val converter = resolveConverter(fileNameOrUrl, text)
        converter.importToDatabase(text.byteInputStream(), db)
    }

    /**
     * Parses and imports records from [bytes] with the given [fileNameOrUrl]
     * into the SQLite database at [dbFile].
     *
     * @param bytes         Input byte array.
     * @param fileNameOrUrl Name or URL used to determine the file type.
     * @param dbFile        SQLite database file to write into.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun importToDatabase(bytes: ByteArray, fileNameOrUrl: String, dbFile: File) {
        val text = bytes.toString(Charsets.UTF_8)
        val converter = resolveConverter(fileNameOrUrl, text)
        val tempFile = File.createTempFile("import_", ".tmp")
        try {
            tempFile.writeBytes(bytes)
            converter.importToDatabase(tempFile, dbFile)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Parses and imports records from [inputStream] into the database referenced by
     * [activeDatabaseState], selecting the converter based on [fileNameOrUrl] and content.
     *
     * @param context             Application context.
     * @param inputStream         Readable input stream.
     * @param fileNameOrUrl       Name or URL used to determine the file type.
     * @param activeDatabaseState Target database state.
     */
    suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        fileNameOrUrl: String,
        activeDatabaseState: DatabaseState?
    ) {
        val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val converter = resolveConverter(fileNameOrUrl, text)
        converter.importToDatabase(context, text.byteInputStream(), activeDatabaseState)
    }

    /**
     * Parses and imports all files from inside a ZIP archive into [dbFile].
     *
     * @param zipFile ZIP archive file.
     * @param dbFile  SQLite database file to write into.
     */
    fun importZipToDatabase(zipFile: File, dbFile: File) {
        parseZip(zipFile, dbFile)
    }

    /**
     * Parses and imports all supported files from inside [zipFile] into the database
     * referenced by [activeDatabaseState].
     *
     * @param context             Application context.
     * @param zipFile             ZIP archive file.
     * @param activeDatabaseState Target [DatabaseState]; must be writable SQLite.
     */
    suspend fun importZipToDatabase(
        context: Context,
        zipFile: File,
        activeDatabaseState: DatabaseState?
    ) {
        parseZip(context, zipFile, activeDatabaseState)
    }

    /**
     * Parses and imports all supported files from inside [zipInputStream] into [dbFile].
     *
     * @param zipInputStream Open [ZipInputStream]; **not** closed by this function.
     * @param dbFile         SQLite database file to write into.
     * @param errors         Mutable list that receives any per-file error messages.
     */
    fun parseZip(
        zipInputStream: ZipInputStream,
        dbFile: File,
        errors: MutableList<String> = mutableListOf()
    ) {
        var zipItem = zipInputStream.nextEntry
        while (zipItem != null) {
            if (!zipItem.isDirectory && isSupported(zipItem.name)) {
                try {
                    val bytes = zipInputStream.readBytes()
                    val text = bytes.toString(Charsets.UTF_8)
                    val converter = resolveConverter(zipItem.name, text)
                    val tempFile = File.createTempFile("zip_entry_", ".tmp")
                    try {
                        tempFile.writeBytes(bytes)
                        val result = converter.importToDatabase(tempFile, dbFile)
                        when (result) {
                            is EntryJsonImportResult -> errors.addAll(result.errors.map { "Error in '${zipItem.name}': $it" })
                            is SourceJsonImportResult -> errors.addAll(result.errors.map { "Error in '${zipItem.name}': $it" })
                            is OpmlImportResult -> errors.addAll(result.errors.map { "Error in '${zipItem.name}': $it" })
                        }
                    } finally {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    errors.add("Failed to parse zip entry '${zipItem.name}': ${e.message}")
                }
            }
            zipInputStream.closeEntry()
            zipItem = zipInputStream.nextEntry
        }
    }

    /**
     * Parses and imports all supported files inside [zipFile] into [dbFile].
     *
     * @param zipFile ZIP archive file.
     * @param dbFile  SQLite database file to write into.
     * @param errors  Mutable list that receives any per-file error messages.
     */
    fun parseZip(
        zipFile: File,
        dbFile: File,
        errors: MutableList<String> = mutableListOf()
    ) {
        ZipInputStream(zipFile.inputStream().buffered()).use { parseZip(it, dbFile, errors) }
    }

    /**
     * Parses and imports all supported files inside [zipInputStream] into the database
     * referenced by [activeDatabaseState].
     *
     * @param context             Application context.
     * @param zipInputStream      Open [ZipInputStream]; **not** closed by this function.
     * @param activeDatabaseState Target [DatabaseState]; must be writable SQLite.
     * @param errors              Mutable list that receives any per-file error messages.
     */
    suspend fun parseZip(
        context: Context,
        zipInputStream: ZipInputStream,
        activeDatabaseState: DatabaseState?,
        errors: MutableList<String> = mutableListOf()
    ) {
        var zipItem = zipInputStream.nextEntry
        while (zipItem != null) {
            if (!zipItem.isDirectory && isSupported(zipItem.name)) {
                try {
                    val bytes = zipInputStream.readBytes()
                    val text = bytes.toString(Charsets.UTF_8)
                    val converter = resolveConverter(zipItem.name, text)
                    val result = converter.importToDatabase(context, text.byteInputStream(), activeDatabaseState)
                    when (result) {
                        is EntryJsonImportResult -> errors.addAll(result.errors.map { "Error in '${zipItem.name}': $it" })
                        is SourceJsonImportResult -> errors.addAll(result.errors.map { "Error in '${zipItem.name}': $it" })
                        is OpmlImportResult -> errors.addAll(result.errors.map { "Error in '${zipItem.name}': $it" })
                    }
                } catch (e: Exception) {
                    errors.add("Failed to parse zip entry '${zipItem.name}': ${e.message}")
                }
            }
            zipInputStream.closeEntry()
            zipItem = zipInputStream.nextEntry
        }
    }

    /**
     * Parses and imports all supported files inside [zipFile] into the database
     * referenced by [activeDatabaseState].
     *
     * @param context             Application context.
     * @param zipFile             ZIP archive file.
     * @param activeDatabaseState Target [DatabaseState]; must be writable SQLite.
     * @param errors              Mutable list that receives any per-file error messages.
     */
    suspend fun parseZip(
        context: Context,
        zipFile: File,
        activeDatabaseState: DatabaseState?,
        errors: MutableList<String> = mutableListOf()
    ) {
        ZipInputStream(zipFile.inputStream().buffered()).use {
            parseZip(context, it, activeDatabaseState, errors)
        }
    }
}
