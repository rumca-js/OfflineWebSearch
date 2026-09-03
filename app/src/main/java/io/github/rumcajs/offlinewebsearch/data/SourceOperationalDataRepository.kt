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
 * Data class representing operational metadata for a source stored in the `sourceoperationaldata` table.
 * Matches SQLAlchemy model definition:
 * class SourceOperationalData(Base):
 *     __tablename__ = "sourceoperationaldata"
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     date_fetched = mapped_column(DateTime, nullable=True)
 *     source_obj_id: Mapped[int]
 *
 * @property id Primary key (autoincrement).
 * @property date_fetched ISO 8601 timestamp string when the source was last fetched.
 * @property source_obj_id Foreign key reference to `sourcedatamodel.id`.
 */
@Serializable
data class SourceOperationalData(
    val id: Long? = null,
    val date_fetched: String? = null,
    val source_obj_id: Long? = null
)

/**
 * Repository for accessing and managing the `sourceoperationaldata` SQLite table.
 */
object SourceOperationalDataRepository : RepositoryInterface {

    override fun getTableName(): String = "sourceoperationaldata"

    /**
     * Generates a current UTC ISO 8601 timestamp string.
     */
    fun getCurrentIsoTimestamp(): String = DateUtils.getCurrentIsoTimestamp()

    /**
     * Parses an ISO 8601 UTC timestamp string to epoch milliseconds, or null on error.
     */
    fun parseIsoTimestamp(timestamp: String?): Long? = DateUtils.parseIsoTimestamp(timestamp)

    const val OUTDATED_FETCH_THRESHOLD_MILLIS: Long = 3600_000L // 1 hour

    /**
     * Checks whether a fetch timestamp is considered outdated (i.e. null, unparseable, or older than 1 hour).
     */
    fun isFetchOutdated(fetchTime: String?): Boolean {
        val parsedTime = parseIsoTimestamp(fetchTime) ?: return true
        val now = System.currentTimeMillis()
        return (now - parsedTime) > OUTDATED_FETCH_THRESHOLD_MILLIS
    }

    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date_fetched TEXT,
                source_obj_id INTEGER
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Loads the [SourceOperationalData] record associated with [sourceObjId].
     */
    suspend fun getOperationalDataBySourceId(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceObjId: Long
    ): SourceOperationalData? = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext null
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val sqlText = "SELECT id, date_fetched, source_obj_id FROM ${getTableName()} WHERE source_obj_id = ? LIMIT 1"
            var result: SourceOperationalData? = null
            val cursor = db.rawQuery(sqlText, arrayOf(sourceObjId.toString()))
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val id = if (c.isNull(c.getColumnIndexOrThrow("id"))) null else c.getLong(c.getColumnIndexOrThrow("id"))
                    val dateFetched = c.getString(c.getColumnIndexOrThrow("date_fetched"))
                    val sourceId = if (c.isNull(c.getColumnIndexOrThrow("source_obj_id"))) null else c.getLong(c.getColumnIndexOrThrow("source_obj_id"))
                    result = SourceOperationalData(id = id, date_fetched = dateFetched, source_obj_id = sourceId)
                }
            }
            db.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Updates or inserts a fetch timestamp record in `sourceoperationaldata` for [sourceObjId].
     */
    suspend fun setSourceFetch(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceObjId: Long,
        fetchTime: String = getCurrentIsoTimestamp()
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val query = "SELECT id FROM ${getTableName()} WHERE source_obj_id = ?"
            val cursor = db.rawQuery(query, arrayOf(sourceObjId.toString()))
            val existingId = cursor.use { c ->
                if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("id")) else null
            }

            if (existingId != null) {
                val values = ContentValues().apply {
                    put("date_fetched", fetchTime)
                }
                db.update(getTableName(), values, "id = ?", arrayOf(existingId.toString()))
            } else {
                val values = ContentValues().apply {
                    put("date_fetched", fetchTime)
                    put("source_obj_id", sourceObjId)
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
     * Updates or inserts a fetch timestamp record in `sourceoperationaldata` for the source identified by [sourceUrl].
     */
    suspend fun setSourceFetchByUrl(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceUrl: String,
        fetchTime: String = getCurrentIsoTimestamp()
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (sourceUrl.isBlank()) return@withContext Pair(false, "Source URL is empty")
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val sourceCursor = db.rawQuery("SELECT id FROM sourcedatamodel WHERE url = ? LIMIT 1", arrayOf(sourceUrl))
            val sourceId = sourceCursor.use { c ->
                if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("id")) else null
            }

            if (sourceId == null) {
                db.close()
                return@withContext Pair(false, "Source not found for URL: $sourceUrl")
            }

            db.close()
            setSourceFetch(context, activeDatabaseState, sourceId, fetchTime)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes operational data associated with [sourceObjId].
     */
    suspend fun deleteOperationalDataBySourceId(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceObjId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext false
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            db.delete(getTableName(), "source_obj_id = ?", arrayOf(sourceObjId.toString()))
            db.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Clears all records from the `sourceoperationaldata` table.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
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

