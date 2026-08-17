package io.github.rumcajs.offlinewebsearch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
object SearchHistoryRepository {

    private const val TABLE_NAME = "searchhistory"
    private const val MAX_SEARCH_HISTORY_ENTRIES = 200

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
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
    suspend fun loadSearchHistory(
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
            val sqlText = "SELECT id, search_query, date FROM $TABLE_NAME ORDER BY date DESC, id DESC LIMIT ?"
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
     * Returns search suggestions matching a prefix or query string, sorted by date descending.
     * Used by search suggestions UI.
     */
    suspend fun getSearchSuggestions(
        context: Context,
        activeDatabaseState: DatabaseState?,
        prefix: String,
        limit: Int = 10
    ): List<String> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<String>()
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext suggestions
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext suggestions

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val sqlText = if (prefix.isBlank()) {
                "SELECT DISTINCT search_query FROM $TABLE_NAME WHERE search_query IS NOT NULL AND search_query != '' ORDER BY date DESC, id DESC LIMIT ?"
            } else {
                "SELECT DISTINCT search_query FROM $TABLE_NAME WHERE search_query LIKE ? AND search_query != '' ORDER BY date DESC, id DESC LIMIT ?"
            }
            val selectionArgs = if (prefix.isBlank()) {
                arrayOf(limit.toString())
            } else {
                arrayOf("%$prefix%", limit.toString())
            }

            val cursor = db.rawQuery(sqlText, selectionArgs)
            cursor.use { c ->
                while (c.moveToNext()) {
                    val query = c.getString(0)
                    if (!query.isNullOrBlank()) {
                        suggestions.add(query)
                    }
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        suggestions
    }

    /**
     * Records a user search query into the `searchhistory` table.
     * Replaces or updates existing matching search query timestamp or creates a new entry.
     */
    suspend fun recordSearch(
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
            val checkSql = "SELECT id FROM $TABLE_NAME WHERE search_query = ?"
            val cursor = db.rawQuery(checkSql, arrayOf(trimmedQuery))
            val existingId = cursor.use { c ->
                if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("id")) else null
            }

            if (existingId != null) {
                val values = ContentValues().apply {
                    put("date", now)
                }
                db.update(TABLE_NAME, values, "id = ?", arrayOf(existingId.toString()))
            } else {
                val values = ContentValues().apply {
                    put("search_query", trimmedQuery)
                    put("date", now)
                }
                db.insert(TABLE_NAME, null, values)
            }

            // Enforce max limit of 200 entries, deleting older entries
            val pruneSql = """
                DELETE FROM $TABLE_NAME
                WHERE id NOT IN (
                    SELECT id FROM $TABLE_NAME
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
     * Deletes a search history record by ID.
     */
    suspend fun deleteSearch(
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
     * Clears all search history records from `searchhistory`.
     */
    suspend fun clearSearchHistory(
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
