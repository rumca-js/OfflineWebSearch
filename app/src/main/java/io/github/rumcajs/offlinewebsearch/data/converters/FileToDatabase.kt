package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import java.io.File
import java.io.InputStream

/**
 * Dispatches file imports to the appropriate converter ([EntryJsonToDatabase] or [OpmlToDatabase])
 * based on the file extension.
 *
 * Supported extensions:
 *  - `.json` -> [EntryJsonToDatabase]
 *  - `.opml` -> [OpmlToDatabase]
 */
object FileToDatabase {

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
     * Parses and imports records from [file] into the SQLite database at [dbFile]
     * by checking the file extension.
     *
     * @param file   Input file (.json or .opml).
     * @param dbFile SQLite database file to write into.
     * @throws IllegalArgumentException if the file extension is unsupported.
     */
    fun importToDatabase(file: File, dbFile: File) {
        val name = file.name.lowercase()
        when {
            name.endsWith(".json") -> EntryJsonToDatabase.importToDatabase(file, dbFile)
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
            lower.endsWith(".json") -> EntryJsonToDatabase.importToDatabase(inputStream, db)
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
                val tempJsonFile = File.createTempFile("import_json_", ".json")
                try {
                    tempJsonFile.writeBytes(bytes)
                    EntryJsonToDatabase.importToDatabase(tempJsonFile, dbFile)
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
     * [activeDatabaseState], selecting the converter based on [fileNameOrUrl].
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
            lower.endsWith(".json") -> EntryJsonToDatabase.importToDatabase(context, inputStream, activeDatabaseState)
            lower.endsWith(".opml") -> OpmlToDatabase.importToDatabase(context, inputStream, activeDatabaseState)
            else -> throw IllegalArgumentException("Unsupported file extension for: $fileNameOrUrl")
        }
    }
}
