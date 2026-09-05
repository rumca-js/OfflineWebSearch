package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing a row in the `entrycompactedtags` table.
 *
 * Matches the schema:
 * ```sql
 * CREATE TABLE "entrycompactedtags" (
 *     "id"       INTEGER NOT NULL,
 *     "tag"      VARCHAR(1000) NOT NULL,
 *     "entry_id" BIGINT,
 *     PRIMARY KEY("id")
 * );
 * ```
 */
@Serializable
data class EntryCompactedTag(
    val id: Long? = null,
    val tag: String = "",
    val entry_id: Long? = null
)

/**
 * Repository for accessing and managing the `entrycompactedtags` SQLite table.
 *
 * Each row associates a tag string with a specific entry identified by [EntryCompactedTag.entry_id].
 */
object EntryCompactedTagsRepository : RepositoryInterface {

    override fun getTableName(): String = "entrycompactedtags"

    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id       INTEGER NOT NULL,
                tag      TEXT NOT NULL,
                entry_id INTEGER,
                PRIMARY KEY(id)
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Loads all tag rows for the given [entryId], ordered by id ascending.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param entryId The entry whose tags are requested.
     * @return List of [EntryCompactedTag] rows for this entry.
     */
    suspend fun getTagsForEntry(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entryId: Long
    ): List<EntryCompactedTag> = withContext(Dispatchers.IO) {
        val tags = mutableListOf<EntryCompactedTag>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext tags
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext tags

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val sqlText = "SELECT id, tag, entry_id FROM ${getTableName()} WHERE entry_id = ? ORDER BY id ASC"
            val cursor = db.rawQuery(sqlText, arrayOf(entryId.toString()))
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = if (c.isNull(c.getColumnIndexOrThrow("id"))) null else c.getLong(c.getColumnIndexOrThrow("id"))
                    val tag = c.getString(c.getColumnIndexOrThrow("tag")) ?: ""
                    val eId = if (c.isNull(c.getColumnIndexOrThrow("entry_id"))) null else c.getLong(c.getColumnIndexOrThrow("entry_id"))
                    tags.add(EntryCompactedTag(id = id, tag = tag, entry_id = eId))
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tags
    }

    /**
     * Inserts a new tag row for the given entry.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param tag Tag string to insert (truncated to 1000 characters).
     * @param entryId The entry this tag belongs to, or null if unassociated.
     * @return Pair where first is true on success and second contains an optional error message.
     */
    suspend fun insertTag(
        context: Context,
        activeDatabaseState: DatabaseState?,
        tag: String,
        entryId: Long?
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val values = ContentValues().apply {
                put("tag", tag.take(1000))
                if (entryId != null) put("entry_id", entryId) else putNull("entry_id")
            }
            val rowId = db.insert(getTableName(), null, values)
            db.close()
            if (rowId != -1L) Pair(true, null) else Pair(false, "Insert failed")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes all tag rows associated with a specific [entryId].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param entryId The entry whose tags should be removed.
     * @return Pair where first is true on success and second contains an optional error message.
     */
    suspend fun deleteTagsForEntry(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entryId: Long
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            db.delete(getTableName(), "entry_id = ?", arrayOf(entryId.toString()))
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Clears all rows from the `entrycompactedtags` table.
     */
    override suspend fun clear(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            db.delete(getTableName(), null, null)
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }
}
