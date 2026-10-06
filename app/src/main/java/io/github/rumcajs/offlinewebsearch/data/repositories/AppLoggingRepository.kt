package io.github.rumcajs.offlinewebsearch.data.repositories

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.rumcajs.offlinewebsearch.MainActivity
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
    const val LEVEL_DEBUG = 10
    const val LEVEL_INFO = 20
    const val LEVEL_WARNING = 30
    const val LEVEL_ERROR = 40
    const val LEVEL_CRITICAL = 50
    const val LEVEL_NOTIFICATION = 60

    /** Notification channel constants. */
    const val NOTIFICATION_CHANNEL_ID = "app_notifications"
    const val NOTIFICATION_CHANNEL_NAME = "App Notifications"

    private val notificationIdCounter = AtomicInteger(1000)

    /**
     * Maximum number of log records retained in the database table to prevent unbounded growth.
     */
    private const val MAX_LOG_ENTRIES = 500

    private fun getCurrentIsoTimestamp(): String = DateUtils.getCurrentIsoTimestamp()

    /**
     * Creates the notification channel on Android O (API 26) and above.
     *
     * @param context Application context.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from Offline Web Search"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Pushes a system notification to the Android notification drawer.
     *
     * @param context Application context.
     * @param infoText Summary / title text of the notification.
     * @param detailText Optional detailed body text.
     * @param notificationId Optional explicit notification ID; auto-generated if null.
     * @return The notification ID if posted, or null if posting failed / permission missing.
     */
    fun pushNotification(
        context: Context,
        infoText: String,
        detailText: String? = null,
        notificationId: Int? = null
    ): Int? {
        return try {
            createNotificationChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return null
                }
            }

            val id = notificationId ?: notificationIdCounter.incrementAndGet()

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val icon = if (context.applicationInfo.icon != 0) {
                context.applicationInfo.icon
            } else {
                io.github.rumcajs.offlinewebsearch.R.mipmap.ic_launcher
            }

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(infoText)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            if (!detailText.isNullOrBlank()) {
                builder.setContentText(detailText)
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            }

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(id, builder.build())
            id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun debug(
        context: Context,
        activeDatabaseState: DatabaseState?,
        infoText: String,
        detailText: String? = null
    ): Pair<Boolean, String?> = insertLog(context, activeDatabaseState, infoText, detailText, LEVEL_DEBUG)

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
     * Records a notification log entry (level = [LEVEL_NOTIFICATION]) and pushes a system notification.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param infoText Summary message (used as notification title).
     * @param detailText Optional detail / body message (used as notification body).
     * @return Pair where first is true on success, and second contains an optional error message on failure.
     */
    suspend fun notify(
        context: Context,
        activeDatabaseState: DatabaseState?,
        infoText: String,
        detailText: String? = null
    ): Pair<Boolean, String?> {
        pushNotification(context, infoText, detailText)
        return insertLog(context, activeDatabaseState, infoText, detailText, LEVEL_NOTIFICATION)
    }

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
    /**
     * Inserts a new log entry directly into the given [SQLiteDatabase] instance and prunes old logs.
     *
     * @param db SQLiteDatabase instance to insert into.
     * @param infoText Summary text of the log event.
     * @param detailText Optional detailed text or stack trace.
     * @param level Log level integer (e.g., 0 = INFO, 1 = WARN, 2 = ERROR).
     * @param date Optional custom timestamp; defaults to current ISO 8601 UTC timestamp.
     * @return Pair where first is true on success, and second contains an optional error message on failure.
     */
    fun insertLogDirect(
        db: SQLiteDatabase,
        infoText: String,
        detailText: String? = null,
        level: Int = 0,
        date: String? = null
    ): Pair<Boolean, String?> {
        return try {
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
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
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
            val result = insertLogDirect(db, infoText, detailText, level, date)
            db.close()
            result
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
