package io.github.rumcajs.offlinewebsearch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing an entry transition in the `entrytransitionhistory` table.
 * Matches SQLAlchemy model definition:
 * class EntryTransitionHistory(Base):
 *     __tablename__ = "entrytransitionhistory"
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     counter: Mapped[Optional[int]] = mapped_column()
 *     entry_from_id: Mapped[Optional[int]] = mapped_column()
 *     entry_to_id: Mapped[Optional[int]] = mapped_column()
 */
@Serializable
data class EntryTransitionHistory(
    val id: Long? = null,
    val counter: Int? = 0,
    val entry_from_id: Long? = null,
    val entry_to_id: Long? = null
)

/**
 * Repository for accessing and managing the `entrytransitionhistory` SQLite table.
 */
object EntryTransitionHistoryRepository : RepositoryInterface {

    override fun getTableName(): String = "entrytransitionhistory"

    const val MAX_NUMBER_OF_DISPLAYED_ENTRIES = 50

    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                counter INTEGER,
                entry_from_id INTEGER,
                entry_to_id INTEGER
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Result wrapper for loading transitioned entries with potential error messaging.
     */
    data class TransitionLoadResult(
        val entries: List<Pair<EntryTransitionHistory, Entry>> = emptyList(),
        val error: String? = null
    )

    /**
     * Loads destination [Entry] objects that were transitioned to from [fromEntryId], ordered by transition counter descending.
     */
    suspend fun getTransitionedEntriesFrom(
        context: Context,
        activeDatabaseState: DatabaseState?,
        fromEntryId: Long,
        limit: Int = MAX_NUMBER_OF_DISPLAYED_ENTRIES
    ): TransitionLoadResult = withContext(Dispatchers.IO) {
        val result = mutableListOf<Pair<EntryTransitionHistory, Entry>>()
        if (activeDatabaseState == null) {
            return@withContext TransitionLoadResult(emptyList(), "No active database configured")
        }
        if (activeDatabaseState.extension != ".db") {
            return@withContext TransitionLoadResult(emptyList(), null)
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) {
            return@withContext TransitionLoadResult(emptyList(), "Database file '${activeDatabaseState.localFileName}' not found")
        }

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val sqlText = """
                SELECT t.id AS t_id, t.counter AS t_counter, t.entry_from_id AS t_entry_from_id, t.entry_to_id AS t_entry_to_id,
                       ${EntryRepository.ENTRY_SELECT_COLUMNS}
                FROM ${getTableName()} t
                INNER JOIN linkdatamodel l ON t.entry_to_id = l.id
                WHERE t.entry_from_id = ?
                ORDER BY t.counter DESC, t.id DESC
                LIMIT ?
            """.trimIndent()

            val cursor = db.rawQuery(sqlText, arrayOf(fromEntryId.toString(), limit.toString()))
            cursor.use { c ->
                while (c.moveToNext()) {
                    val tId = if (c.isNull(c.getColumnIndexOrThrow("t_id"))) null else c.getLong(c.getColumnIndexOrThrow("t_id"))
                    val tCounter = if (c.isNull(c.getColumnIndexOrThrow("t_counter"))) null else c.getInt(c.getColumnIndexOrThrow("t_counter"))
                    val tEntryFromId = if (c.isNull(c.getColumnIndexOrThrow("t_entry_from_id"))) null else c.getLong(c.getColumnIndexOrThrow("t_entry_from_id"))
                    val tEntryToId = if (c.isNull(c.getColumnIndexOrThrow("t_entry_to_id"))) null else c.getLong(c.getColumnIndexOrThrow("t_entry_to_id"))

                    val transition = EntryTransitionHistory(
                        id = tId,
                        counter = tCounter,
                        entry_from_id = tEntryFromId,
                        entry_to_id = tEntryToId
                    )

                    val entry = EntryRepository.cursorToEntry(c)
                    result.add(Pair(transition, entry))
                }
            }
            db.close()
            TransitionLoadResult(result, null)
        } catch (e: Exception) {
            e.printStackTrace()
            TransitionLoadResult(emptyList(), e.message ?: "Unknown SQL error loading entry transitions")
        }
    }

    /**
     * Records or updates a transition from [fromEntryId] to [toEntryId] in `entrytransitionhistory`.
     * Increments the transition counter if an entry transition record already exists, or inserts a new one with counter 1.
     */
    suspend fun insertTransition(
        context: Context,
        activeDatabaseState: DatabaseState?,
        fromEntryId: Long,
        toEntryId: Long
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (fromEntryId == toEntryId) {
            return@withContext Pair(true, null)
        }
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val query = "SELECT id, counter FROM ${getTableName()} WHERE entry_from_id = ? AND entry_to_id = ?"
            val cursor = db.rawQuery(query, arrayOf(fromEntryId.toString(), toEntryId.toString()))
            val existing = cursor.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow("id"))
                    val counter = c.getInt(c.getColumnIndexOrThrow("counter"))
                    Pair(id, counter)
                } else {
                    null
                }
            }

            if (existing != null) {
                val (id, counter) = existing
                val values = ContentValues().apply {
                    put("counter", counter + 1)
                }
                db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            } else {
                val values = ContentValues().apply {
                    put("counter", 1)
                    put("entry_from_id", fromEntryId)
                    put("entry_to_id", toEntryId)
                }
                db.insert(getTableName(), null, values)
            }

            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * TODO. I think it can be moved to RepositoryInterface
     * Clears all transition history records from `entrytransitionhistory`.
     */
    override suspend fun clear(
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
            db.delete(getTableName(), null, null)
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }
}

