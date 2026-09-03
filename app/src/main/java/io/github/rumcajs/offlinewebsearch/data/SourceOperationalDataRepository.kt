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
 *     import_seconds: Mapped[Optional[int]]
 *     number_of_entries: Mapped[Optional[int]]
 *     page_hash: Mapped[bytes | None] = mapped_column(LargeBinary)
 *     body_hash: Mapped[bytes | None] = mapped_column(LargeBinary)
 *     consecutive_errors: Mapped[Optional[int]]
 *
 * @property id Primary key (autoincrement).
 * @property date_fetched ISO 8601 timestamp string when the source was last fetched.
 * @property source_obj_id Foreign key reference to `sourcedatamodel.id`.
 * @property import_seconds Duration of import in seconds.
 * @property number_of_entries Number of entries in the source.
 * @property page_hash Binary hash of page content.
 * @property body_hash Binary hash of page body.
 * @property consecutive_errors Count of consecutive errors.
 */
@Serializable
data class SourceOperationalData(
    val id: Long? = null,
    val date_fetched: String? = null,
    val source_obj_id: Long? = null,
    val import_seconds: Int? = null,
    val number_of_entries: Int? = null,
    val page_hash: ByteArray? = null,
    val body_hash: ByteArray? = null,
    val consecutive_errors: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SourceOperationalData

        if (id != other.id) return false
        if (date_fetched != other.date_fetched) return false
        if (source_obj_id != other.source_obj_id) return false
        if (import_seconds != other.import_seconds) return false
        if (number_of_entries != other.number_of_entries) return false
        if (page_hash != null) {
            if (other.page_hash == null) return false
            if (!page_hash.contentEquals(other.page_hash)) return false
        } else if (other.page_hash != null) return false
        if (body_hash != null) {
            if (other.body_hash == null) return false
            if (!body_hash.contentEquals(other.body_hash)) return false
        } else if (other.body_hash != null) return false
        if (consecutive_errors != other.consecutive_errors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (date_fetched?.hashCode() ?: 0)
        result = 31 * result + (source_obj_id?.hashCode() ?: 0)
        result = 31 * result + (import_seconds?.hashCode() ?: 0)
        result = 31 * result + (number_of_entries?.hashCode() ?: 0)
        result = 31 * result + (page_hash?.contentHashCode() ?: 0)
        result = 31 * result + (body_hash?.contentHashCode() ?: 0)
        result = 31 * result + (consecutive_errors?.hashCode() ?: 0)
        return result
    }
}

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
                source_obj_id INTEGER,
                import_seconds INTEGER,
                number_of_entries INTEGER,
                page_hash BLOB,
                body_hash BLOB,
                consecutive_errors INTEGER
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
            val sqlText = "SELECT id, date_fetched, source_obj_id, import_seconds, number_of_entries, page_hash, body_hash, consecutive_errors FROM ${getTableName()} WHERE source_obj_id = ? LIMIT 1"
            var result: SourceOperationalData? = null
            val cursor = db.rawQuery(sqlText, arrayOf(sourceObjId.toString()))
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val id = if (c.isNull(c.getColumnIndexOrThrow("id"))) null else c.getLong(c.getColumnIndexOrThrow("id"))
                    val dateFetched = c.getString(c.getColumnIndexOrThrow("date_fetched"))
                    val sourceId = if (c.isNull(c.getColumnIndexOrThrow("source_obj_id"))) null else c.getLong(c.getColumnIndexOrThrow("source_obj_id"))
                    val importSeconds = if (c.isNull(c.getColumnIndexOrThrow("import_seconds"))) null else c.getInt(c.getColumnIndexOrThrow("import_seconds"))
                    val numberOfEntries = if (c.isNull(c.getColumnIndexOrThrow("number_of_entries"))) null else c.getInt(c.getColumnIndexOrThrow("number_of_entries"))
                    val pageHash = if (c.isNull(c.getColumnIndexOrThrow("page_hash"))) null else c.getBlob(c.getColumnIndexOrThrow("page_hash"))
                    val bodyHash = if (c.isNull(c.getColumnIndexOrThrow("body_hash"))) null else c.getBlob(c.getColumnIndexOrThrow("body_hash"))
                    val consecutiveErrors = if (c.isNull(c.getColumnIndexOrThrow("consecutive_errors"))) null else c.getInt(c.getColumnIndexOrThrow("consecutive_errors"))
                    result = SourceOperationalData(
                        id = id,
                        date_fetched = dateFetched,
                        source_obj_id = sourceId,
                        import_seconds = importSeconds,
                        number_of_entries = numberOfEntries,
                        page_hash = pageHash,
                        body_hash = bodyHash,
                        consecutive_errors = consecutiveErrors
                    )
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

