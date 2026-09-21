package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import java.io.File
import java.io.InputStream

/**
 * Common contract for converters that read a file (or stream) and persist the parsed
 * records into a SQLite database.
 *
 * ### Type parameters
 * - [TEntity] – the domain record produced by parsing (e.g. [io.github.rumcajs.offlinewebsearch.data.repositories.Entry],
 *   [io.github.rumcajs.offlinewebsearch.data.repositories.Source]).
 * - [TResult] – the import result carrying parsed entities, insert count, and errors
 *   (e.g. [EntryJsonImportResult], [OpmlImportResult], [SourceJsonImportResult]).
 *
 * ### Implementing converters
 * - [EntryJsonToDatabase] – JSON array of entry objects → `linkdatamodel`.
 * - [SourceJsonToDatabase] – JSON array of source objects → `sourcedatamodel`.
 * - [OpmlToDatabase] – OPML/XML subscription list → `sourcedatamodel`.
 *
 * ### Usage pattern
 * ```kotlin
 * val result = EntryJsonToDatabase.importToDatabase(
 *     context = context,
 *     inputStream = stream,
 *     activeDatabaseState = dbState
 * )
 * ```
 */
interface FileToDatabase<TEntity, TResult> {

    /**
     * Parses [inputStream] into a list of [TEntity] records.
     *
     * @param inputStream Readable input; **not** closed by this function.
     * @return Parsed records, or empty list if the input contained no data.
     * @throws Exception if the stream is unreadable or contains invalid data.
     */
    fun parse(inputStream: InputStream): List<TEntity>

    /**
     * Parses [file] into a list of [TEntity] records.
     *
     * @param file Input file to read.
     * @return Parsed records, or empty list if the file contained no data.
     * @throws Exception if the file is unreadable or contains invalid data.
     */
    fun parse(file: File): List<TEntity>

    /**
     * Parses [inputStream] and inserts the records into the already-open [db].
     *
     * @param inputStream Readable input; **not** closed by this function.
     * @param db          Open, writable [SQLiteDatabase] instance.
     * @return [TResult] carrying parsed records, insert count, and any errors.
     */
    fun importToDatabase(inputStream: InputStream, db: SQLiteDatabase): TResult

    /**
     * Parses [file] and inserts the records into the SQLite database at [dbFile].
     *
     * @param file   Input file to read.
     * @param dbFile SQLite database file to write into.
     * @return [TResult] carrying parsed records, insert count, and any errors.
     */
    fun importToDatabase(file: File, dbFile: File): TResult

    /**
     * Parses [inputStream] and inserts the records into the database referenced by
     * [activeDatabaseState]. Runs on [kotlinx.coroutines.Dispatchers.IO].
     *
     * @param context               Application context.
     * @param inputStream           Readable input; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be a writable SQLite database.
     * @return [TResult] carrying parsed records, insert count, and any errors.
     */
    suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        activeDatabaseState: DatabaseState?
    ): TResult
}
