package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.InputStream

/**
 * Dispatches file imports to the appropriate converter ([EntryJsonToDatabase], [SourceJsonToDatabase],
 * or [OpmlToDatabase]) based on the file extension and JSON content.
 *
 * Supported extensions:
 *  - `.json` -> [EntryJsonToDatabase] or [SourceJsonToDatabase] (detected by content or filename)
 *  - `.opml` -> [OpmlToDatabase]
 */
object FileToDatabase {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Determines whether the given [fileNameOrUrl] has a supported file extension (.json or .opml).
     *
     * @param fileNameOrUrl File path, name, or URL string to check.
     * @return true if the file extension is supported, false otherwise.
     */
    fun isSupported(fileNameOrUrl: String): Boolean {
        val lower = fileNameOrUrl.lowercase()
        return lower.endsWith(".json") || lower.endsWith(".opml")
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
     * Parses and imports records from [file] into the SQLite database at [dbFile]
     * by checking the file extension and content.
     *
     * @param file   Input file (.json or .opml).
     * @param dbFile SQLite database file to write into.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun importToDatabase(file: File, dbFile: File) {
        val name = file.name.lowercase()
        when {
            name.endsWith(".json") -> {
                val text = file.readText(Charsets.UTF_8)
                if (isSourceJson(text, file.name)) {
                    SourceJsonToDatabase.importToDatabase(file, dbFile)
                } else {
                    EntryJsonToDatabase.importToDatabase(file, dbFile)
                }
            }
            name.endsWith(".opml") -> OpmlToDatabase.importToDatabase(file, dbFile)
            else -> throw IllegalArgumentException("Unsupported file extension for: ${file.name}")
        }
    }

    /**
     * Parses and imports records from [inputStream] with the given [fileNameOrUrl]
     * into the open [db] SQLite database.
     *
     * @param inputStream   Readable input stream.
     * @param fileNameOrUrl Name or URL used to determine the file type (.json or .opml).
     * @param db            Open writable SQLite database.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun importToDatabase(inputStream: InputStream, fileNameOrUrl: String, db: SQLiteDatabase) {
        val lower = fileNameOrUrl.lowercase()
        when {
            lower.endsWith(".json") -> {
                val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
                if (isSourceJson(text, fileNameOrUrl)) {
                    SourceJsonToDatabase.importToDatabase(text.byteInputStream(), db)
                } else {
                    EntryJsonToDatabase.importToDatabase(text.byteInputStream(), db)
                }
            }
            lower.endsWith(".opml") -> OpmlToDatabase.importToDatabase(inputStream, db)
            else -> throw IllegalArgumentException("Unsupported file extension for: $fileNameOrUrl")
        }
    }

    /**
     * Parses and imports records from [bytes] with the given [fileNameOrUrl]
     * into the SQLite database at [dbFile].
     *
     * @param bytes         Input byte array.
     * @param fileNameOrUrl Name or URL used to determine the file type (.json or .opml).
     * @param dbFile        SQLite database file to write into.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun importToDatabase(bytes: ByteArray, fileNameOrUrl: String, dbFile: File) {
        val lower = fileNameOrUrl.lowercase()
        when {
            lower.endsWith(".json") -> {
                val text = bytes.toString(Charsets.UTF_8)
                val tempJsonFile = File.createTempFile("import_json_", ".json")
                try {
                    tempJsonFile.writeBytes(bytes)
                    if (isSourceJson(text, fileNameOrUrl)) {
                        SourceJsonToDatabase.importToDatabase(tempJsonFile, dbFile)
                    } else {
                        EntryJsonToDatabase.importToDatabase(tempJsonFile, dbFile)
                    }
                } finally {
                    tempJsonFile.delete()
                }
            }
            lower.endsWith(".opml") -> {
                val tempOpmlFile = File.createTempFile("import_opml_", ".opml")
                try {
                    tempOpmlFile.writeBytes(bytes)
                    OpmlToDatabase.importToDatabase(tempOpmlFile, dbFile)
                } finally {
                    tempOpmlFile.delete()
                }
            }
            else -> throw IllegalArgumentException("Unsupported file extension for: $fileNameOrUrl")
        }
    }

    /**
     * Parses and imports all JSON entries from inside a ZIP archive into [dbFile].
     *
     * @param zipFile  ZIP archive file.
     * @param dbFile   SQLite database file to write into.
     */
    fun importZipToDatabase(zipFile: File, dbFile: File) {
        EntryJsonToDatabase.importZipToDatabase(zipFile, dbFile)
    }

    /**
     * Parses and imports records from [inputStream] into the database referenced by
     * [activeDatabaseState], selecting the converter based on [fileNameOrUrl] and content.
     *
     * @param context             Application context.
     * @param inputStream         Readable input stream.
     * @param fileNameOrUrl       Name or URL used to determine the file type (.json or .opml).
     * @param activeDatabaseState Target database state.
     */
    suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        fileNameOrUrl: String,
        activeDatabaseState: DatabaseState?
    ) {
        val lower = fileNameOrUrl.lowercase()
        when {
            lower.endsWith(".json") -> {
                val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
                if (isSourceJson(text, fileNameOrUrl)) {
                    SourceJsonToDatabase.importToDatabase(context, text.byteInputStream(), activeDatabaseState)
                } else {
                    EntryJsonToDatabase.importToDatabase(context, text.byteInputStream(), activeDatabaseState)
                }
            }
            lower.endsWith(".opml") -> OpmlToDatabase.importToDatabase(context, inputStream, activeDatabaseState)
            else -> throw IllegalArgumentException("Unsupported file extension for: $fileNameOrUrl")
        }
    }
}
