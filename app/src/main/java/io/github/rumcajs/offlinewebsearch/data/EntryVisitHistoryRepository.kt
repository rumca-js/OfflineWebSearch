package io.github.rumcajs.offlinewebsearch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing an entry in the `entryvisithistory` table.
 * Matches SQLAlchemy model definition:
 * class EntryVisitHistory(Base):
 *     __tablename__ = "entryvisithistory"
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     visits: Mapped[Optional[int]] = mapped_column()
 *     date_last_visit = mapped_column(DateTime(timezone=True), nullable=True)
 *     entry_id: Mapped[Optional[int]] = mapped_column()
 */
@Serializable
data class EntryVisitHistory(
    val id: Long? = null,
    val visits: Int? = 0,
    val date_last_visit: String? = null,
    val entry_id: Long? = null
)

/**
 * Repository for accessing and managing the `entryvisithistory` SQLite table.
 */
object EntryVisitHistoryRepository : RepositoryInterface {

    override fun getTableName(): String = "entryvisithistory"

    private fun getCurrentIsoTimestamp(): String = DateUtils.getCurrentIsoTimestamp()

    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                visits INTEGER,
                date_last_visit TEXT,
                entry_id INTEGER
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Loads all records from `entryvisithistory` ordered by date_last_visit descending.
     */
    suspend fun loadVisitHistory(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): List<EntryVisitHistory> = withContext(Dispatchers.IO) {
        val history = mutableListOf<EntryVisitHistory>()
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext history
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext history

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val sqlText = "SELECT id, visits, date_last_visit, entry_id FROM ${getTableName()} ORDER BY date_last_visit DESC, id DESC"
            val cursor = db.rawQuery(sqlText, null)
            cursor.use {
                while (it.moveToNext()) {
                    val id = if (it.isNull(it.getColumnIndexOrThrow("id"))) null else it.getLong(it.getColumnIndexOrThrow("id"))
                    val visits = if (it.isNull(it.getColumnIndexOrThrow("visits"))) null else it.getInt(it.getColumnIndexOrThrow("visits"))
                    val dateLastVisit = it.getString(it.getColumnIndexOrThrow("date_last_visit"))
                    val entryId = if (it.isNull(it.getColumnIndexOrThrow("entry_id"))) null else it.getLong(it.getColumnIndexOrThrow("entry_id"))

                    history.add(
                        EntryVisitHistory(
                            id = id,
                            visits = visits,
                            date_last_visit = dateLastVisit,
                            entry_id = entryId
                        )
                    )
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        history
    }

    /**
     * Loads visit history records joined with their corresponding [Entry] from `linkdatamodel`.
     * Useful for displaying recently browsed entries.
     */
    suspend fun loadVisitedEntries(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): List<Pair<EntryVisitHistory, Entry>> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Pair<EntryVisitHistory, Entry>>()
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext result
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext result

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val sqlText = """
                SELECT v.id AS v_id, v.visits AS v_visits, v.date_last_visit AS v_date_last_visit, v.entry_id AS v_entry_id,
                       ${EntryRepository.ENTRY_SELECT_COLUMNS}
                FROM ${getTableName()} v
                INNER JOIN linkdatamodel l ON v.entry_id = l.id
                ORDER BY v.date_last_visit DESC, v.id DESC
            """.trimIndent()

            val cursor = db.rawQuery(sqlText, null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    val vId = if (c.isNull(c.getColumnIndexOrThrow("v_id"))) null else c.getLong(c.getColumnIndexOrThrow("v_id"))
                    val vVisits = if (c.isNull(c.getColumnIndexOrThrow("v_visits"))) null else c.getInt(c.getColumnIndexOrThrow("v_visits"))
                    val vDateLastVisit = c.getString(c.getColumnIndexOrThrow("v_date_last_visit"))
                    val vEntryId = if (c.isNull(c.getColumnIndexOrThrow("v_entry_id"))) null else c.getLong(c.getColumnIndexOrThrow("v_entry_id"))

                    val visit = EntryVisitHistory(
                        id = vId,
                        visits = vVisits,
                        date_last_visit = vDateLastVisit,
                        entry_id = vEntryId
                    )

                    val entry = EntryRepository.cursorToEntry(c)
                    result.add(Pair(visit, entry))
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result
    }

    /**
     * Records or updates a visit for [entryId] in the `entryvisithistory` table.
     * Increments the visits count and updates date_last_visit.
     */
    suspend fun recordVisit(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entryId: Long
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val lastVisitQuery = "SELECT entry_id FROM ${getTableName()} WHERE entry_id IS NOT NULL ORDER BY date_last_visit DESC, id DESC LIMIT 1"
            val lastVisitCursor = db.rawQuery(lastVisitQuery, null)
            val lastVisitedEntryId = lastVisitCursor.use { c ->
                if (c.moveToFirst() && !c.isNull(c.getColumnIndexOrThrow("entry_id"))) {
                    c.getLong(c.getColumnIndexOrThrow("entry_id"))
                } else {
                    null
                }
            }

            val now = getCurrentIsoTimestamp()
            val query = "SELECT id, visits FROM ${getTableName()} WHERE entry_id = ?"
            val cursor = db.rawQuery(query, arrayOf(entryId.toString()))
            val existing = cursor.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow("id"))
                    val visits = c.getInt(c.getColumnIndexOrThrow("visits"))
                    Pair(id, visits)
                } else {
                    null
                }
            }

            if (existing != null) {
                val (id, visits) = existing
                val values = ContentValues().apply {
                    put("visits", visits + 1)
                    put("date_last_visit", now)
                }
                db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            } else {
                val values = ContentValues().apply {
                    put("visits", 1)
                    put("date_last_visit", now)
                    put("entry_id", entryId)
                }
                db.insert(getTableName(), null, values)
            }

            db.close()

            if (lastVisitedEntryId != null && lastVisitedEntryId != entryId) {
                EntryTransitionHistoryRepository.insertTransition(
                    context = context,
                    activeDatabaseState = activeDatabaseState,
                    fromEntryId = lastVisitedEntryId,
                    toEntryId = entryId
                )
            }

            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes a visit history record by ID (alias for [deleteById]).
     */
    suspend fun deleteVisit(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Pair<Boolean, String?> = deleteById(context, activeDatabaseState, id)

    /**
     * Clears all records from the `entryvisithistory` table.
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

    /**
     * Clears all records from the `entryvisithistory` table (alias for [clear]).
     */
    suspend fun clearVisitHistory(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Pair<Boolean, String?> = clear(context, activeDatabaseState)
}

