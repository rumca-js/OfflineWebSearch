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
 * @property source_obj_id Foreign key referencing the associated source ID in `sourcedatamodel`.
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
object SourceOperationalDataRepository {

    private const val TABLE_NAME = "sourceoperationaldata"

    /**
     * Generates a current UTC ISO 8601 timestamp string.
     */
    fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
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
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext null
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val sqlText = "SELECT id, date_fetched, source_obj_id FROM $TABLE_NAME WHERE source_obj_id = ? LIMIT 1"
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
    suspend fun recordSourceFetch(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceObjId: Long,
        fetchTime: String = getCurrentIsoTimestamp()
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val query = "SELECT id FROM $TABLE_NAME WHERE source_obj_id = ?"
            val cursor = db.rawQuery(query, arrayOf(sourceObjId.toString()))
            val existingId = cursor.use { c ->
                if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("id")) else null
            }

            if (existingId != null) {
                val values = ContentValues().apply {
                    put("date_fetched", fetchTime)
                }
                db.update(TABLE_NAME, values, "id = ?", arrayOf(existingId.toString()))
            } else {
                val values = ContentValues().apply {
                    put("date_fetched", fetchTime)
                    put("source_obj_id", sourceObjId)
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
     * Updates or inserts a fetch timestamp record in `sourceoperationaldata` for the source identified by [sourceUrl].
     */
    suspend fun recordSourceFetchByUrl(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceUrl: String,
        fetchTime: String = getCurrentIsoTimestamp()
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (sourceUrl.isBlank()) return@withContext Pair(false, "Source URL is empty")
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
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
            recordSourceFetch(context, activeDatabaseState, sourceId, fetchTime)
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
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext false
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            db.delete(TABLE_NAME, "source_obj_id = ?", arrayOf(sourceObjId.toString()))
            db.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
