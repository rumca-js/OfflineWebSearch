package io.github.rumcajs.offlinewebsearch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing social statistics stored in the `socialdata` table.
 *
 * @property id Primary key (autoincrement).
 * @property entryId Foreign key referencing the associated entry ID.
 * @property thumbsUp Count of thumbs-up votes.
 * @property thumbsDown Count of thumbs-down votes.
 * @property viewCount Total view count.
 * @property rating Rating value.
 * @property upvoteRatio Upvote ratio value.
 * @property upvoteDiff Difference between upvotes and downvotes.
 * @property upvoteViewRatio Upvote to view ratio.
 * @property stars Star rating or star count.
 * @property followersCount Count of followers.
 * @property dateUpdated Timestamp when the social data was last updated.
 */
@Serializable
data class SocialData(
    val id: Long? = null,
    val entryId: Long? = null,
    val thumbsUp: Int? = 0,
    val thumbsDown: Int? = 0,
    val viewCount: Int? = 0,
    val rating: Int? = 0,
    val upvoteRatio: Int? = 0,
    val upvoteDiff: Int? = 0,
    val upvoteViewRatio: Int? = 0,
    val stars: Int? = 0,
    val followersCount: Int? = 0,
    val dateUpdated: String? = null
)

/**
 * Repository providing access to the `socialdata` table in SQLite databases.
 */
object SocialDataRepository : RepositoryInterface {

    override fun getTableName(): String = "socialdata"

    /**
     * Loads the [SocialData] associated with a given [entryId].
     */
    suspend fun getSocialDataByEntryId(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entryId: Long
    ): SocialData? = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext null
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val sqlText = """
                SELECT id, entry_id, thumbs_up, thumbs_down, view_count, rating,
                       upvote_ratio, upvote_diff, upvote_view_ratio, stars, followers_count, date_updated
                FROM ${getTableName()}
                WHERE entry_id = ?
                LIMIT 1
            """.trimIndent()

            var socialData: SocialData? = null
            val cursor = db.rawQuery(sqlText, arrayOf(entryId.toString()))
            cursor.use { c ->
                if (c.moveToFirst()) {
                    socialData = SocialData(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        entryId = if (c.isNull(c.getColumnIndexOrThrow("entry_id"))) null else c.getLong(c.getColumnIndexOrThrow("entry_id")),
                        thumbsUp = if (c.isNull(c.getColumnIndexOrThrow("thumbs_up"))) null else c.getInt(c.getColumnIndexOrThrow("thumbs_up")),
                        thumbsDown = if (c.isNull(c.getColumnIndexOrThrow("thumbs_down"))) null else c.getInt(c.getColumnIndexOrThrow("thumbs_down")),
                        viewCount = if (c.isNull(c.getColumnIndexOrThrow("view_count"))) null else c.getInt(c.getColumnIndexOrThrow("view_count")),
                        rating = if (c.isNull(c.getColumnIndexOrThrow("rating"))) null else c.getInt(c.getColumnIndexOrThrow("rating")),
                        upvoteRatio = if (c.isNull(c.getColumnIndexOrThrow("upvote_ratio"))) null else c.getInt(c.getColumnIndexOrThrow("upvote_ratio")),
                        upvoteDiff = if (c.isNull(c.getColumnIndexOrThrow("upvote_diff"))) null else c.getInt(c.getColumnIndexOrThrow("upvote_diff")),
                        upvoteViewRatio = if (c.isNull(c.getColumnIndexOrThrow("upvote_view_ratio"))) null else c.getInt(c.getColumnIndexOrThrow("upvote_view_ratio")),
                        stars = if (c.isNull(c.getColumnIndexOrThrow("stars"))) null else c.getInt(c.getColumnIndexOrThrow("stars")),
                        followersCount = if (c.isNull(c.getColumnIndexOrThrow("followers_count"))) null else c.getInt(c.getColumnIndexOrThrow("followers_count")),
                        dateUpdated = c.getString(c.getColumnIndexOrThrow("date_updated"))
                    )
                }
            }
            db.close()
            socialData
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Inserts a new record into `socialdata`.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun insertSocialData(
        context: Context,
        activeDatabaseState: DatabaseState?,
        socialData: SocialData
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                socialData.entryId?.let { put("entry_id", it) }
                socialData.thumbsUp?.let { put("thumbs_up", it) }
                socialData.thumbsDown?.let { put("thumbs_down", it) }
                socialData.viewCount?.let { put("view_count", it) }
                socialData.rating?.let { put("rating", it) }
                socialData.upvoteRatio?.let { put("upvote_ratio", it) }
                socialData.upvoteDiff?.let { put("upvote_diff", it) }
                socialData.upvoteViewRatio?.let { put("upvote_view_ratio", it) }
                socialData.stars?.let { put("stars", it) }
                socialData.followersCount?.let { put("followers_count", it) }
                socialData.dateUpdated?.let { put("date_updated", it) }
            }
            val newId = db.insert(getTableName(), null, values)
            db.close()
            if (newId != -1L) Pair(true, null) else Pair(false, "Failed to insert into ${getTableName()}")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates an existing record in `socialdata`.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun updateSocialData(
        context: Context,
        activeDatabaseState: DatabaseState?,
        socialData: SocialData
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (socialData.id == null) {
            return@withContext Pair(false, "SocialData ID is required for update")
        }
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                socialData.entryId?.let { put("entry_id", it) }
                socialData.thumbsUp?.let { put("thumbs_up", it) }
                socialData.thumbsDown?.let { put("thumbs_down", it) }
                socialData.viewCount?.let { put("view_count", it) }
                socialData.rating?.let { put("rating", it) }
                socialData.upvoteRatio?.let { put("upvote_ratio", it) }
                socialData.upvoteDiff?.let { put("upvote_diff", it) }
                socialData.upvoteViewRatio?.let { put("upvote_view_ratio", it) }
                socialData.stars?.let { put("stars", it) }
                socialData.followersCount?.let { put("followers_count", it) }
                socialData.dateUpdated?.let { put("date_updated", it) }
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(socialData.id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; socialdata record may not exist")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes a record from `socialdata` by [id] (alias for [deleteById]).
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun deleteSocialData(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Pair<Boolean, String?> = deleteById(context, activeDatabaseState, id)

    /**
     * Clears all records from the `socialdata` table.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
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
            db.delete(getTableName(), null, null)
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Clears all records from the `socialdata` table (alias for [clear]).
     */
    suspend fun clearSocialData(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Pair<Boolean, String?> = clear(context, activeDatabaseState)
}


