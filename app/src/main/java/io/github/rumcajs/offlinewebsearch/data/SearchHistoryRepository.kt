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
 * Data class representing an entry in the `searchhistory` table.
 * Matches SQLAlchemy model definition:
 * class SearchHistory(Base):
 *     __tablename__ = "searchhistory"
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     search_query: Mapped[str] = mapped_column(String(500))
 *     date = mapped_column(DateTime(timezone=True), nullable=True)
 */
@Serializable
data class SearchHistory(
    val id: Long? = null,
    val search_query: String = "",
    val date: String? = null
)

/**
 * Repository for accessing and managing the `searchhistory` SQLite table.
 * Used for storing user searches and providing search suggestions.
 */
object SearchHistoryRepository : RepositoryInterface {

    override fun getTableName(): String = "searchhistory"

    private const val MAX_SEARCH_HISTORY_ENTRIES = 200

    private fun getCurrentIsoTimestamp(): String = DateUtils.getCurrentIsoTimestamp()

    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                search_query TEXT NOT NULL,
                date TEXT
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Loads search history entries ordered by date descending.
     * @param limit Maximum number of recent search records to retrieve (default 50).
     */
    suspend fun getSearchHistory(
        context: Context,
        activeDatabaseState: DatabaseState?,
        limit: Int = 50
    ): List<SearchHistory> = withContext(Dispatchers.IO) {
        val history = mutableListOf<SearchHistory>()
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext history
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext history

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val sqlText = "SELECT id, search_query, date FROM ${getTableName()} ORDER BY date DESC, id DESC LIMIT ?"
            val cursor = db.rawQuery(sqlText, arrayOf(limit.toString()))
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = if (c.isNull(c.getColumnIndexOrThrow("id"))) null else c.getLong(c.getColumnIndexOrThrow("id"))
                    val query = c.getString(c.getColumnIndexOrThrow("search_query")) ?: ""
                    val date = c.getString(c.getColumnIndexOrThrow("date"))

                    history.add(
                        SearchHistory(
                            id = id,
                            search_query = query,
                            date = date
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
     * Records a user search query into the `searchhistory` table.
     * Replaces or updates existing matching search query timestamp or creates a new entry.
     */
    suspend fun insertSearch(
        context: Context,
        activeDatabaseState: DatabaseState?,
        query: String
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
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

            val now = getCurrentIsoTimestamp()
            val checkSql = "SELECT id FROM ${getTableName()} WHERE search_query = ?"
            val cursor = db.rawQuery(checkSql, arrayOf(trimmedQuery))
            val existingId = cursor.use { c ->
                if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("id")) else null
            }

            if (existingId != null) {
                val values = ContentValues().apply {
                    put("date", now)
                }
                db.update(getTableName(), values, "id = ?", arrayOf(existingId.toString()))
            } else {
                val values = ContentValues().apply {
                    put("search_query", trimmedQuery)
                    put("date", now)
                }
                db.insert(getTableName(), null, values)
            }

            // Enforce max limit of 200 entries, deleting older entries
            val pruneSql = """
                DELETE FROM ${getTableName()}
                WHERE id NOT IN (
                    SELECT id FROM ${getTableName()}
                    ORDER BY date DESC, id DESC
                    LIMIT $MAX_SEARCH_HISTORY_ENTRIES
                )
            """.trimIndent()
            db.execSQL(pruneSql)

            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Clears all search history records from `searchhistory`.
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


