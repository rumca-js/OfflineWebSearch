package io.github.rumcajs.offlinewebsearch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing an entry in the `readlater` table.
 * Matches SQLAlchemy model definition:
 * class ReadLater(Base):
 *     __tablename__ = "readlater"
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     entry_id: Mapped[int]
 *     user_id: Mapped[int]
 */
@Serializable
data class ReadLater(
    val id: Long? = null,
    val entry_id: Long? = null,
    val user_id: Long? = null
)

/**
 * Repository for accessing and managing the `readlater` SQLite table.
 */
object ReadLaterRepository {

    private const val TABLE_NAME = "readlater"

    private fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Loads all ReadLater records from the database, optionally filtered by [userId].
     */
    suspend fun loadReadLaterList(
        context: Context,
        activeDatabaseState: DatabaseState?,
        userId: Long? = null
    ): List<ReadLater> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ReadLater>()
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext list
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext list

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val query = if (userId != null) {
                "SELECT id, entry_id, user_id FROM $TABLE_NAME WHERE user_id = ? ORDER BY id DESC"
            } else {
                "SELECT id, entry_id, user_id FROM $TABLE_NAME ORDER BY id DESC"
            }
            val args = if (userId != null) arrayOf(userId.toString()) else null

            val cursor = db.rawQuery(query, args)
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = if (c.isNull(c.getColumnIndexOrThrow("id"))) null else c.getLong(c.getColumnIndexOrThrow("id"))
                    val entryId = if (c.isNull(c.getColumnIndexOrThrow("entry_id"))) null else c.getLong(c.getColumnIndexOrThrow("entry_id"))
                    val uId = if (c.isNull(c.getColumnIndexOrThrow("user_id"))) null else c.getLong(c.getColumnIndexOrThrow("user_id"))

                    list.add(
                        ReadLater(
                            id = id,
                            entry_id = entryId,
                            user_id = uId
                        )
                    )
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list
    }

    /**
     * Loads ReadLater items joined with their corresponding [Entry] from `linkdatamodel`.
     */
    suspend fun loadReadLaterEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        userId: Long? = null
    ): List<Pair<ReadLater, Entry>> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Pair<ReadLater, Entry>>()
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext result
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext result

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val whereClause = if (userId != null) "WHERE r.user_id = ?" else ""
            val args = if (userId != null) arrayOf(userId.toString()) else null

            val sqlText = """
                SELECT r.id AS r_id, r.entry_id AS r_entry_id, r.user_id AS r_user_id,
                       ${EntryRepository.ENTRY_SELECT_COLUMNS}
                FROM $TABLE_NAME r
                INNER JOIN linkdatamodel l ON r.entry_id = l.id
                $whereClause
                ORDER BY r.id DESC
            """.trimIndent()

            val cursor = db.rawQuery(sqlText, args)
            cursor.use { c ->
                while (c.moveToNext()) {
                    val rId = if (c.isNull(c.getColumnIndexOrThrow("r_id"))) null else c.getLong(c.getColumnIndexOrThrow("r_id"))
                    val rEntryId = if (c.isNull(c.getColumnIndexOrThrow("r_entry_id"))) null else c.getLong(c.getColumnIndexOrThrow("r_entry_id"))
                    val rUserId = if (c.isNull(c.getColumnIndexOrThrow("r_user_id"))) null else c.getLong(c.getColumnIndexOrThrow("r_user_id"))

                    val readLater = ReadLater(
                        id = rId,
                        entry_id = rEntryId,
                        user_id = rUserId
                    )

                    val entry = EntryRepository.cursorToEntry(c)
                    result.add(Pair(readLater, entry))
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result
    }

    /**
     * Checks whether an entry is in the ReadLater list for a given [userId].
     */
    suspend fun isReadLater(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entryId: Long,
        userId: Long = 0
    ): Boolean = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext false
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            ensureTableExists(db)
            val cursor = db.rawQuery(
                "SELECT 1 FROM $TABLE_NAME WHERE entry_id = ? AND user_id = ? LIMIT 1",
                arrayOf(entryId.toString(), userId.toString())
            )
            val exists = cursor.use { it.moveToFirst() }
            db.close()
            exists
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Adds an entry to the ReadLater list if not already present.
     */
    suspend fun addReadLater(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entryId: Long,
        userId: Long = 0
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val checkCursor = db.rawQuery(
                "SELECT id FROM $TABLE_NAME WHERE entry_id = ? AND user_id = ? LIMIT 1",
                arrayOf(entryId.toString(), userId.toString())
            )
            val exists = checkCursor.use { it.moveToFirst() }

            if (!exists) {
                val values = ContentValues().apply {
                    put("entry_id", entryId)
                    put("user_id", userId)
                }
                db.insert(TABLE_NAME, null, values)
            }

            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Removes an entry from ReadLater by [entryId] and [userId].
     */
    suspend fun removeReadLaterByEntryId(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entryId: Long,
        userId: Long = 0
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val rows = db.delete(
                TABLE_NAME,
                "entry_id = ? AND user_id = ?",
                arrayOf(entryId.toString(), userId.toString())
            )
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows deleted")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes a ReadLater record by its primary key [id].
     */
    suspend fun deleteReadLater(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val rows = db.delete(TABLE_NAME, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows deleted")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Clears all records from the `readlater` table.
     */
    suspend fun clearReadLater(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            db.delete(TABLE_NAME, null, null)
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }
}
