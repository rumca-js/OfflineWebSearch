package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Interface defining common repository operations across data layer repositories.
 */
interface RepositoryInterface {

    /**
     * Returns the name of the SQLite table managed by this repository.
     */
    fun getTableName(): String

    /**
     * Deletes a record from the repository table by its primary key ID.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param id Primary key ID of the record to delete.
     * @return Pair where first is true if a record was deleted, and second contains an optional error message.
     */
    suspend fun deleteById(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val rows = db.delete(getTableName(), "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows deleted")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Clears all records managed by this repository from the database.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @return Pair where first is true if successful, and second contains an optional error message on failure.
     */
    suspend fun clear(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Pair<Boolean, String?>

    /**
     * Ensures that the database table managed by this repository exists.
     * Repositories that create tables on the fly override this method.
     *
     * @param db SQLiteDatabase instance to execute creation statements against.
     */
    fun ensureTableExists(db: SQLiteDatabase) {}

    /**
     * Returns the number of rows in the table managed by this repository, or null if the table does not exist or an error occurs.
     *
     * @param db SQLiteDatabase instance to execute count query against.
     * @return Row count, or null on error.
     */
    fun getRowCount(db: SQLiteDatabase): Long? {
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM ${getTableName()}", null)
            cursor.use { c ->
                if (c.moveToFirst()) {
                    c.getLong(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}




