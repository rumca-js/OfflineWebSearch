package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.Int

/**
 * Data class representing operational metadata for a source stored in the `sourceoperationaldata` table.
 * Matches SQLAlchemy model definition:
 * class SourceOperationalData(Base):
 *     __tablename__ = "sourceoperationaldata"
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     date_fetched = mapped_column(DateTime, nullable=True)
 *     source_id: Mapped[int]
 *     import_seconds: Mapped[Optional[int]]
 *     number_of_entries: Mapped[Optional[int]]
 *     page_hash: Mapped[bytes | None] = mapped_column(LargeBinary)
 *     body_hash: Mapped[bytes | None] = mapped_column(LargeBinary)
 *     consecutive_errors: Mapped[Optional[int]]
 *
 * @property id Primary key (autoincrement).
 * @property date_fetched ISO 8601 timestamp string when the source was last fetched.
 * @property source_id Foreign key reference to `sourcedatamodel.id`.
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
    val source_id: Long? = null,
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
        if (source_id != other.source_id) return false
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
        result = 31 * result + (source_id?.hashCode() ?: 0)
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

    /**
     * Checks whether a fetch timestamp is considered outdated (i.e. null, unparseable, or older than threshold).
     * @param fetchTime ISO 8601 UTC timestamp string.
     * @param thresholdSeconds Seconds threshold after which fetch is outdated (defaults to [AppConfiguration.outdatedFetchThresholdSeconds]).
     * @param fetchPeriodSeconds Per-source fetch period in seconds. When positive, overrides [thresholdSeconds].
     */
    fun isFetchOutdated(
        fetchTime: String?,
        thresholdSeconds: Long = io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.value.outdatedFetchThresholdSeconds,
        fetchPeriodSeconds: Long = 0L
    ): Boolean {
        val effectiveThreshold = if (fetchPeriodSeconds > 0L) fetchPeriodSeconds else thresholdSeconds
        val parsedTime = parseIsoTimestamp(fetchTime) ?: return true
        val now = System.currentTimeMillis()
        return (now - parsedTime) > effectiveThreshold * 1000L
    }

    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date_fetched TEXT,
                source_id INTEGER,
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
     * Reads the current cursor row and constructs a [SourceOperationalData] from it.
     *
     * @param cursor SQLite cursor positioned at the target row.
     * @param prefix Optional column prefix (e.g., "sod_").
     * @return [SourceOperationalData] instance populated with cursor values.
     */
    fun cursorToOperationalData(cursor: Cursor, prefix: String = ""): SourceOperationalData {
        val idIdx = cursor.getColumnIndex(prefix + "id")
        val id = if (idIdx != -1 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else null

        val dateFetchedIdx = cursor.getColumnIndex(prefix + "date_fetched")
        val dateFetched = if (dateFetchedIdx != -1 && !cursor.isNull(dateFetchedIdx)) cursor.getString(dateFetchedIdx) else null

        val sourceIdIdx = cursor.getColumnIndex(prefix + "source_id")
        val sourceId = if (sourceIdIdx != -1 && !cursor.isNull(sourceIdIdx)) cursor.getLong(sourceIdIdx) else null

        val importSecIdx = cursor.getColumnIndex(prefix + "import_seconds")
        val importSeconds = if (importSecIdx != -1 && !cursor.isNull(importSecIdx)) cursor.getInt(importSecIdx) else null

        val numEntriesIdx = cursor.getColumnIndex(prefix + "number_of_entries")
        val numberOfEntries = if (numEntriesIdx != -1 && !cursor.isNull(numEntriesIdx)) cursor.getInt(numEntriesIdx) else null

        val pageHashIdx = cursor.getColumnIndex(prefix + "page_hash")
        val pageHash = if (pageHashIdx != -1 && !cursor.isNull(pageHashIdx)) cursor.getBlob(pageHashIdx) else null

        val bodyHashIdx = cursor.getColumnIndex(prefix + "body_hash")
        val bodyHash = if (bodyHashIdx != -1 && !cursor.isNull(bodyHashIdx)) cursor.getBlob(bodyHashIdx) else null

        val consErrorsIdx = cursor.getColumnIndex(prefix + "consecutive_errors")
        val consecutiveErrors = if (consErrorsIdx != -1 && !cursor.isNull(consErrorsIdx)) cursor.getInt(consErrorsIdx) else null

        return SourceOperationalData(
            id = id,
            date_fetched = dateFetched,
            source_id = sourceId,
            import_seconds = importSeconds,
            number_of_entries = numberOfEntries,
            page_hash = pageHash,
            body_hash = bodyHash,
            consecutive_errors = consecutiveErrors
        )
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
            val sqlText = "SELECT id, date_fetched, source_id, import_seconds, number_of_entries, page_hash, body_hash, consecutive_errors FROM ${getTableName()} WHERE source_id = ? LIMIT 1"
            var result: SourceOperationalData? = null
            val cursor = db.rawQuery(sqlText, arrayOf(sourceObjId.toString()))
            cursor.use { c ->
                if (c.moveToFirst()) {
                    result = cursorToOperationalData(c)
                }
            }
            db.close()
            result
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source ID: $sourceObjId Exception in $functionName", e.message)
            e.printStackTrace()
            null
        }
    }

    /**
     * Updates or inserts a fetch record in `sourceoperationaldata` for [sourceObjId].
     * Optionally persists [numberOfEntries], [pageHash], and [bodyHash] when provided.
     * When [isError] is true, increments [SourceOperationalData.consecutive_errors].
     * When [isError] is false, resets [SourceOperationalData.consecutive_errors] to 0.
     */
    suspend fun setSourceFetch(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceObjId: Long,
        fetchTime: String = getCurrentIsoTimestamp(),
        numberOfEntries: Int? = null,
        pageHash: ByteArray? = null,
        bodyHash: ByteArray? = null,
        isError: Boolean? = false,
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val query = "SELECT id, consecutive_errors FROM ${getTableName()} WHERE source_id = ?"
            val cursor = db.rawQuery(query, arrayOf(sourceObjId.toString()))
            var existingId: Long? = null
            var currentErrors: Int? = null
            cursor.use { c ->
                if (c.moveToFirst()) {
                    existingId = c.getLong(c.getColumnIndexOrThrow("id"))
                    val errorIdx = c.getColumnIndex("consecutive_errors")
                    currentErrors = if (errorIdx != -1 && !c.isNull(errorIdx)) c.getInt(errorIdx) else null
                }
            }

            val newErrors = when (isError) {
                true -> (currentErrors ?: 0) + 1
                false -> 0
                null -> currentErrors
            }

            if (existingId != null) {
                val values = ContentValues().apply {
                    put("date_fetched", fetchTime)
                    if (newErrors != null) {
                        put("consecutive_errors", newErrors)
                    }
                    numberOfEntries?.let { put("number_of_entries", it) }
                    pageHash?.let { put("page_hash", it) }
                    bodyHash?.let { put("body_hash", it) }
                }
                db.update(getTableName(), values, "id = ?", arrayOf(existingId.toString()))
            } else {
                val values = ContentValues().apply {
                    put("date_fetched", fetchTime)
                    put("source_id", sourceObjId)
                    put("consecutive_errors", newErrors ?: 0)
                    numberOfEntries?.let { put("number_of_entries", it) }
                    pageHash?.let { put("page_hash", it) }
                    bodyHash?.let { put("body_hash", it) }
                }
                db.insert(getTableName(), null, values)
            }

            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source ID: $sourceObjId Exception in $functionName", e.message)
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
        fetchTime: String = getCurrentIsoTimestamp(),
        numberOfEntries: Int? = null,
        pageHash: ByteArray? = null,
        bodyHash: ByteArray? = null,
        isError: Boolean? = false
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
            setSourceFetch(context, activeDatabaseState, sourceId, fetchTime, numberOfEntries, pageHash, bodyHash, isError)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source URL: $sourceUrl Exception in $functionName", e.message)
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
            db.delete(getTableName(), "source_id = ?", arrayOf(sourceObjId.toString()))
            db.close()
            true
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source ID: $sourceObjId Exception in $functionName", e.message)
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
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Clearing operational data in $functionName", e.message)
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }
}

