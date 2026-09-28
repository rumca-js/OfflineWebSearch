package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing a credential record in the `credentials` table.
 *
 * Matches SQLAlchemy model definition:
 * ```python
 * class Credentials(Base):
 *     __tablename__ = "credentials"
 *
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     name: Mapped[str] = mapped_column(String(1000), unique=True)  # credential name. github, or reddit etc.
 *     credential_type: Mapped[str] = mapped_column(String(1000), nullable=True) # refresh token, auth token, etc
 *     username: Mapped[str] = mapped_column(String(1000), nullable=True)
 *     password: Mapped[str] = mapped_column(String(1000), nullable=True)
 *     secret: Mapped[str] = mapped_column(String(1000), nullable=True)
 *     token: Mapped[str] = mapped_column(String(1000), nullable=True)
 *
 *     user_id: Mapped[int]
 * ```
 *
 * @property id Primary key (autoincrement).
 * @property name Unique credential identifier/name (e.g. "github", "reddit").
 * @property credential_type Type of credential (e.g. "refresh_token", "auth_token", "password").
 * @property username Optional username or login.
 * @property password Optional password.
 * @property secret Optional API secret or client secret.
 * @property token Optional access or bearer token.
 * @property user_id ID of the user owning this credential.
 */
@Serializable
data class Credentials(
    val id: Long? = null,
    val name: String = "",
    val credential_type: String? = null,
    val username: String? = null,
    val password: String? = null,
    val secret: String? = null,
    val token: String? = null,
    val user_id: Long = 0L
)

/**
 * Type alias for [Credentials].
 */
typealias Credential = Credentials

/**
 * Repository for accessing and managing the `credentials` SQLite table.
 */
object CredentialsRepository : RepositoryInterface {

    val COLUMNS = arrayOf(
        "id", "name", "credential_type", "username",
        "password", "secret", "token", "user_id"
    )

    override fun getTableName(): String = "credentials"

    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                credential_type TEXT,
                username TEXT,
                password TEXT,
                secret TEXT,
                token TEXT,
                user_id INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Converts a database cursor row into a [Credentials] instance.
     *
     * @param cursor The active cursor positioned at a valid row.
     * @param prefix Optional column prefix.
     * @return Converted [Credentials] instance.
     */
    fun cursorToCredentials(cursor: Cursor, prefix: String = ""): Credentials {
        fun getLong(col: String): Long? {
            val idx = cursor.getColumnIndex(prefix + col)
            return if (idx != -1 && !cursor.isNull(idx)) cursor.getLong(idx) else null
        }
        fun getString(col: String): String? {
            val idx = cursor.getColumnIndex(prefix + col)
            return if (idx != -1 && !cursor.isNull(idx)) cursor.getString(idx) else null
        }

        return Credentials(
            id = getLong("id"),
            name = getString("name") ?: "",
            credential_type = getString("credential_type"),
            username = getString("username"),
            password = getString("password"),
            secret = getString("secret"),
            token = getString("token"),
            user_id = getLong("user_id") ?: 0L
        )
    }

    /**
     * Converts a [Credentials] instance into [ContentValues] for insertion or update.
     *
     * @param credential The credential to convert.
     * @return Populated [ContentValues].
     */
    fun credentialsToContentValues(credential: Credentials): ContentValues {
        return ContentValues().apply {
            put("name", credential.name.take(1000))
            put("credential_type", (credential.credential_type ?: "").take(1000))
            put("username", (credential.username ?: "").take(1000))
            put("password", (credential.password ?: "").take(1000))
            put("secret", (credential.secret ?: "").take(1000))
            put("token", (credential.token ?: "").take(1000))
            put("user_id", credential.user_id)
        }
    }

    /**
     * Retrieves all credentials from the database, optionally filtered by [userId].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param userId Optional user ID filter.
     * @return List of [Credentials].
     */
    suspend fun getAllCredentials(
        context: Context,
        activeDatabaseState: DatabaseState?,
        userId: Long? = null
    ): List<Credentials> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Credentials>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext results
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext results

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val selection = if (userId != null) "user_id = ?" else null
            val selectionArgs = if (userId != null) arrayOf(userId.toString()) else null
            val cursor = db.query(
                getTableName(),
                COLUMNS,
                selection,
                selectionArgs,
                null,
                null,
                "name ASC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    results.add(cursorToCredentials(c))
                }
            }
            db.close()
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Exception in $functionName", e.message)
            e.printStackTrace()
        }

        results
    }

    /**
     * Retrieves a credential by its primary key [id].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param id Primary key ID.
     * @return The [Credentials] record, or null if not found.
     */
    suspend fun getCredentialById(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Credentials? = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext null
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val cursor = db.query(
                getTableName(),
                COLUMNS,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            )
            val result = cursor.use { c ->
                if (c.moveToFirst()) cursorToCredentials(c) else null
            }
            db.close()
            result
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Credential ID: $id Exception in $functionName", e.message)
            e.printStackTrace()
            null
        }
    }

    /**
     * Retrieves a credential by its unique [name].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param name Unique name of the credential.
     * @param userId Optional user ID filter.
     * @return The [Credentials] record, or null if not found.
     */
    suspend fun getCredentialByName(
        context: Context,
        activeDatabaseState: DatabaseState?,
        name: String,
        userId: Long? = null
    ): Credentials? = withContext(Dispatchers.IO) {
        if (name.isBlank() || activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext null
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val selection = if (userId != null) "name = ? AND user_id = ?" else "name = ?"
            val selectionArgs = if (userId != null) arrayOf(name, userId.toString()) else arrayOf(name)
            val cursor = db.query(
                getTableName(),
                COLUMNS,
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            val result = cursor.use { c ->
                if (c.moveToFirst()) cursorToCredentials(c) else null
            }
            db.close()
            result
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Credential Name: $name Exception in $functionName", e.message)
            e.printStackTrace()
            null
        }
    }

    /**
     * Retrieves all credentials matching a specified [credentialType].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param credentialType The credential type to filter by.
     * @param userId Optional user ID filter.
     * @return List of matching [Credentials].
     */
    suspend fun getCredentialsByType(
        context: Context,
        activeDatabaseState: DatabaseState?,
        credentialType: String,
        userId: Long? = null
    ): List<Credentials> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Credentials>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext results
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext results

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val selection = if (userId != null) "credential_type = ? AND user_id = ?" else "credential_type = ?"
            val selectionArgs = if (userId != null) arrayOf(credentialType, userId.toString()) else arrayOf(credentialType)
            val cursor = db.query(
                getTableName(),
                COLUMNS,
                selection,
                selectionArgs,
                null,
                null,
                "name ASC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    results.add(cursorToCredentials(c))
                }
            }
            db.close()
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Credential Type: $credentialType Exception in $functionName", e.message)
            e.printStackTrace()
        }

        results
    }

    /**
     * Inserts a new credential into the database.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param credential The credential to insert.
     * @return Pair where first is the new row ID (or null on error), and second is an optional error message.
     */
    suspend fun insertCredential(
        context: Context,
        activeDatabaseState: DatabaseState?,
        credential: Credentials
    ): Pair<Long?, String?> = withContext(Dispatchers.IO) {
        if (credential.name.isBlank()) {
            return@withContext Pair(null, "Credential name cannot be empty")
        }
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(null, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(null, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val values = credentialsToContentValues(credential)
            val rowId = db.insertOrThrow(getTableName(), null, values)
            db.close()
            Pair(rowId, null)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Credential Name: ${credential.name} Exception in $functionName", e.message)
            e.printStackTrace()
            Pair(null, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates an existing credential in the database.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param credential The credential to update (must have non-null [Credentials.id]).
     * @return Pair where first is true on success, and second contains an optional error message.
     */
    suspend fun updateCredential(
        context: Context,
        activeDatabaseState: DatabaseState?,
        credential: Credentials
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (credential.id == null) {
            return@withContext Pair(false, "Credential ID is required for update")
        }
        if (credential.name.isBlank()) {
            return@withContext Pair(false, "Credential name cannot be empty")
        }
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val values = credentialsToContentValues(credential)
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(credential.id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; credential may not exist")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Credential ID: ${credential.id} Exception in $functionName", e.message)
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes a credential by its unique [name].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param name Unique name of the credential to delete.
     * @return Pair where first is true on success, and second contains an optional error message.
     */
    suspend fun deleteByName(
        context: Context,
        activeDatabaseState: DatabaseState?,
        name: String
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext Pair(false, "Credential name cannot be empty")
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val rows = db.delete(getTableName(), "name = ?", arrayOf(name))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows deleted")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Credential Name: $name Exception in $functionName", e.message)
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Clears all records from the `credentials` table.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @return Pair where first is true on success, and second contains an optional error message.
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
            AppLoggingRepository.error(context, activeDatabaseState, "Clearing credentials in $functionName", e.message)
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }
}
