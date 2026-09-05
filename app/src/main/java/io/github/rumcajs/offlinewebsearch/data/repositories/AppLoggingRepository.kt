package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing a log entry in the `applogging` table.
 * Matches SQLAlchemy model definition:
 * ```python
 * class AppLogging(Base):
 *     __tablename__ = "applogging"
 *
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     info_text: Mapped[str] = mapped_column(String(2000))
 *     detail_text: Mapped[Optional[str]] = mapped_column(String(2000))
 *     level: Mapped[int] = mapped_column(default=0)
 *     date = mapped_column(DateTime(timezone=True), nullable=True)
 * ```
 *
 * @property id Primary key (autoincrement).
 * @property info_text Summary text of the log event (up to 2000 chars).
 * @property detail_text Optional detailed text / stacktrace / payload (up to 2000 chars).
 * @property level Log level integer (default 0).
 * @property date ISO 8601 timestamp string with timezone.
 */
@Serializable
data class AppLogging(
    val id: Long? = null,
    val info_text: String = "",
    val detail_text: String? = null,
    val level: Int = 0,
    val date: String? = null
)

/**
 * Repository for accessing and managing the `applogging` SQLite table.
 * Implements [RepositoryInterface] to support common operations like clearing table and deleting by ID.
 */
object AppLoggingRepository : RepositoryInterface {

    override fun getTableName(): String = "applogging"

    /** Log level constants matching the Python model defaults. */
    const val LEVEL_INFO = 0
    const val LEVEL_WARNING = 1
    const val LEVEL_ERROR = 2

    /**
     * Maximum number of log records retained in the database table to prevent unbounded growth.
     */
    private const val MAX_LOG_ENTRIES = 500

    private fun getCurrentIsoTimestamp(): String = DateUtils.getCurrentIsoTimestamp()

    /**
     * Records an informational log entry (level = [LEVEL_INFO]).
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param infoText Summary message.
     * @param detailText Optional detail / stack trace.
     */
    suspend fun info(
        context: Context,
        activeDatabaseState: DatabaseState?,
        infoText: String,
        detailText: String? = null
    ): Pair<Boolean, String?> = insertLog(context, activeDatabaseState, infoText, detailText, LEVEL_INFO)

    /**
     * Records a warning log entry (level = [LEVEL_WARNING]).
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param infoText Summary message.
     * @param detailText Optional detail / stack trace.
     */
    suspend fun warning(
        context: Context,
        activeDatabaseState: DatabaseState?,
        infoText: String,
        detailText: String? = null
    ): Pair<Boolean, String?> = insertLog(context, activeDatabaseState, infoText, detailText, LEVEL_WARNING)

    /**
     * Records an error log entry (level = [LEVEL_ERROR]).
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param infoText Summary message.
     * @param detailText Optional detail / stack trace (e.g. exception message).
     */
    suspend fun error(
        context: Context,
        activeDatabaseState: DatabaseState?,
        infoText: String,
        detailText: String? = null
    ): Pair<Boolean, String?> = insertLog(context, activeDatabaseState, infoText, detailText, LEVEL_ERROR)

    /**
     * Ensures that the `applogging` table exists in the given database.
     *
     * @param db SQLiteDatabase instance to execute creation statements against.
     */
    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                info_text VARCHAR(2000) NOT NULL,
                detail_text VARCHAR(2000),
                level INTEGER NOT NULL DEFAULT 0,
                date TEXT
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Loads log entries from `applogging` ordered by date descending.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param limit Maximum number of log records to retrieve (default 100).
     * @param minLevel Optional minimum log level filter.
     * @return List of [AppLogging] records.
     */
    suspend fun getLogs(
        context: Context,
        activeDatabaseState: DatabaseState?,
        limit: Int = 100,
        minLevel: Int? = null
    ): List<AppLogging> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<AppLogging>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext logs
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext logs

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val whereClause = if (minLevel != null) "WHERE level >= ?" else ""
            val args = if (minLevel != null) arrayOf(minLevel.toString(), limit.toString()) else arrayOf(limit.toString())
            val sqlText = "SELECT id, info_text, detail_text, level, date FROM ${getTableName()} $whereClause ORDER BY date DESC, id DESC LIMIT ?"

            val cursor = db.rawQuery(sqlText, args)
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = if (c.isNull(c.getColumnIndexOrThrow("id"))) null else c.getLong(c.getColumnIndexOrThrow("id"))
                    val infoText = c.getString(c.getColumnIndexOrThrow("info_text")) ?: ""
                    val detailText = c.getString(c.getColumnIndexOrThrow("detail_text"))
                    val level = if (c.isNull(c.getColumnIndexOrThrow("level"))) 0 else c.getInt(c.getColumnIndexOrThrow("level"))
                    val date = c.getString(c.getColumnIndexOrThrow("date"))

                    logs.add(
                        AppLogging(
                            id = id,
                            info_text = infoText,
                            detail_text = detailText,
                            level = level,
                            date = date
                        )
                    )
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        logs
    }

    /**
     * Inserts a new log entry into the `applogging` table and prunes old logs beyond [MAX_LOG_ENTRIES].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param infoText Summary text of the log event.
     * @param detailText Optional detailed text or stack trace.
     * @param level Log level integer (e.g., 0 = INFO, 1 = WARN, 2 = ERROR).
     * @param date Optional custom timestamp; defaults to current ISO 8601 UTC timestamp.
     * @return Pair where first is true on success, and second contains an optional error message on failure.
     */
    suspend fun insertLog(
        context: Context,
        activeDatabaseState: DatabaseState?,
        infoText: String,
        detailText: String? = null,
        level: Int = 0,
        date: String? = null
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)

            val now = date ?: getCurrentIsoTimestamp()
            val values = ContentValues().apply {
                put("info_text", infoText.take(2000))
                detailText?.let { put("detail_text", it.take(2000)) }
                put("level", level)
                put("date", now)
            }
            db.insert(getTableName(), null, values)

            // Prune older log entries to stay within storage limit
            val pruneSql = """
                DELETE FROM ${getTableName()}
                WHERE id NOT IN (
                    SELECT id FROM ${getTableName()}
                    ORDER BY date DESC, id DESC
                    LIMIT $MAX_LOG_ENTRIES
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
     * Inserts an [AppLogging] object into the `applogging` table.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param log Log object to insert.
     * @return Pair where first is true on success, and second contains an optional error message on failure.
     */
    suspend fun insertLog(
        context: Context,
        activeDatabaseState: DatabaseState?,
        log: AppLogging
    ): Pair<Boolean, String?> = insertLog(
        context = context,
        activeDatabaseState = activeDatabaseState,
        infoText = log.info_text,
        detailText = log.detail_text,
        level = log.level,
        date = log.date
    )

    /**
     * Clears all log entries from the `applogging` table.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @return Pair where first is true if successful, and second contains an optional error message on failure.
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
