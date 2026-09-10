package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.OrderBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for SQLite-based entry operations on the `linkdatamodel` table.
 * Inherits from [EntryRepository].
 */
object EntrySqliteRepository : EntryRepository() {

    /** Standard column selection list for queries on `linkdatamodel` (aliased as `l`). */
    const val ENTRY_SELECT_COLUMNS = "l.id, l.link, l.title, l.description, l.author, l.album, l.language, l.page_rating_votes, l.page_rating_visits, l.page_rating, l.thumbnail, l.date_created, l.date_published, l.date_dead_since, l.age, l.status_code, l.manual_status_code, l.bookmarked, l.source_id, l.source_url"

    /** Column selection list for `socialdata` (aliased as `s`). */
    const val SOCIAL_DATA_SELECT_COLUMNS = "s.id AS s_id, s.entry_id AS s_entry_id, s.thumbs_up, s.thumbs_down, s.view_count, s.rating AS s_rating, s.upvote_ratio, s.upvote_diff, s.upvote_view_ratio, s.stars, s.followers_count, s.date_updated"

    /** Maps a cursor row to an [Entry]. */
    fun cursorToEntry(c: Cursor): Entry {
        val id = c.getLong(c.getColumnIndexOrThrow("id"))
        val title = c.getString(c.getColumnIndexOrThrow("title"))
        val description = c.getString(c.getColumnIndexOrThrow("description"))
        val thumbnail = c.getString(c.getColumnIndexOrThrow("thumbnail"))
        val link = c.getString(c.getColumnIndexOrThrow("link"))
        val votes = c.getInt(c.getColumnIndexOrThrow("page_rating_votes"))
        val visits = c.getInt(c.getColumnIndexOrThrow("page_rating_visits"))
        val rating = c.getInt(c.getColumnIndexOrThrow("page_rating"))
        val dateCreated = c.getString(c.getColumnIndexOrThrow("date_created"))
        val datePublished = c.getString(c.getColumnIndexOrThrow("date_published"))
        val dateDeadSince = c.getString(c.getColumnIndexOrThrow("date_dead_since"))
        val author = c.getString(c.getColumnIndexOrThrow("author"))
        val album = c.getString(c.getColumnIndexOrThrow("album"))
        val language = c.getString(c.getColumnIndexOrThrow("language"))
        val age = c.getInt(c.getColumnIndexOrThrow("age"))
        val statusCode = c.getInt(c.getColumnIndexOrThrow("status_code"))
        val manualStatusCode = c.getInt(c.getColumnIndexOrThrow("manual_status_code"))
        val bookmarked = c.getInt(c.getColumnIndexOrThrow("bookmarked")) == 1
        val sourceIdIndex = c.getColumnIndex("source_id")
        val sourceId = if (sourceIdIndex != -1 && !c.isNull(sourceIdIndex)) c.getLong(sourceIdIndex) else null
        val sourceUrlIndex = c.getColumnIndex("source_url")
        val sourceUrl = if (sourceUrlIndex != -1 && !c.isNull(sourceUrlIndex)) c.getString(sourceUrlIndex) else null
        val tagIndex = c.getColumnIndex("tag")
        val tagString = if (tagIndex != -1 && !c.isNull(tagIndex)) c.getString(tagIndex) else null
        val tags = tagString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

        val sIdIndex = c.getColumnIndex("s_id")
        val socialData = if (sIdIndex != -1 && !c.isNull(sIdIndex)) {
            SocialData(
                id = c.getLong(sIdIndex),
                entryId = if (c.isNull(c.getColumnIndexOrThrow("s_entry_id"))) null else c.getLong(
                    c.getColumnIndexOrThrow(
                        "s_entry_id"
                    )
                ),
                thumbsUp = if (c.isNull(c.getColumnIndexOrThrow("thumbs_up"))) null else c.getInt(
                    c.getColumnIndexOrThrow(
                        "thumbs_up"
                    )
                ),
                thumbsDown = if (c.isNull(c.getColumnIndexOrThrow("thumbs_down"))) null else c.getInt(
                    c.getColumnIndexOrThrow("thumbs_down")
                ),
                viewCount = if (c.isNull(c.getColumnIndexOrThrow("view_count"))) null else c.getInt(
                    c.getColumnIndexOrThrow("view_count")
                ),
                rating = if (c.isNull(c.getColumnIndexOrThrow("s_rating"))) null else c.getInt(
                    c.getColumnIndexOrThrow(
                        "s_rating"
                    )
                ),
                upvoteRatio = if (c.isNull(c.getColumnIndexOrThrow("upvote_ratio"))) null else c.getInt(
                    c.getColumnIndexOrThrow("upvote_ratio")
                ),
                upvoteDiff = if (c.isNull(c.getColumnIndexOrThrow("upvote_diff"))) null else c.getInt(
                    c.getColumnIndexOrThrow("upvote_diff")
                ),
                upvoteViewRatio = if (c.isNull(c.getColumnIndexOrThrow("upvote_view_ratio"))) null else c.getInt(
                    c.getColumnIndexOrThrow("upvote_view_ratio")
                ),
                stars = if (c.isNull(c.getColumnIndexOrThrow("stars"))) null else c.getInt(
                    c.getColumnIndexOrThrow(
                        "stars"
                    )
                ),
                followersCount = if (c.isNull(c.getColumnIndexOrThrow("followers_count"))) null else c.getInt(
                    c.getColumnIndexOrThrow("followers_count")
                ),
                dateUpdated = c.getString(c.getColumnIndexOrThrow("date_updated"))
            )
        } else null

        return Entry(
            id = id,
            link = link,
            title = title,
            description = description,
            thumbnail = thumbnail,
            author = author,
            album = album,
            language = language,
            page_rating_votes = votes,
            page_rating_visits = visits,
            page_rating = rating,
            date_created = dateCreated,
            date_published = datePublished,
            date_dead_since = dateDeadSince,
            age = age,
            status_code = statusCode,
            manual_status_code = manualStatusCode,
            bookmarked = bookmarked,
            source_id = sourceId,
            source_url = sourceUrl,
            tags = tags,
            socialData = socialData
        )
    }

    override fun getTableName(): String = "linkdatamodel"

    override suspend fun countEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        searchQuery: String,
        orderBy: OrderBy,
        filterByVisited: Boolean,
        filterByReadLater: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext 0
        countEntriesSql(context, activeDatabaseState, searchQuery, filterByVisited, filterByReadLater)
    }

    override suspend fun getEntriesPage(
        context: Context,
        activeDatabaseState: DatabaseState?,
        searchQuery: String,
        orderBy: OrderBy,
        offset: Int,
        pageSize: Int,
        filterByVisited: Boolean,
        filterByReadLater: Boolean
    ): List<Entry> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext emptyList()
        getPageFromSql(context, activeDatabaseState, searchQuery, orderBy, offset, pageSize, filterByVisited, filterByReadLater)
    }

    /**
     * Inserts a new entry (and its tags) into the SQLite database.
     * @return Triple(success, insertedRowId, errorMessage).
     *         [insertedRowId] is the primary key of the new row on success, or -1 on failure.
     */
    suspend fun addEntrySql(
        context: Context,
        activeDatabaseState: DatabaseState,
        entry: Entry
    ): Triple<Boolean, Long, String?> = withContext(Dispatchers.IO) {
        if (!activeDatabaseState.isSQLite) {
            return@withContext Triple(false, -1L, "Database is not a SQLite file")
        }
        if (activeDatabaseState.isReadOnly) {
            return@withContext Triple(false, -1L, "Database is read-only")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Triple(false, -1L, "Database file not found: ${activeDatabaseState.localFileName}")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)

            val values = ContentValues().apply {
                put("link", entry.link ?: "")
                put("title", entry.title)
                put("description", entry.description)
                put("author", entry.author)
                put("album", entry.album)
                put("language", entry.language)
                put("page_rating_votes", entry.page_rating_votes ?: 0)
                put("page_rating_visits", entry.page_rating_visits ?: 0)
                put("page_rating", entry.page_rating ?: 0)
                put("thumbnail", entry.thumbnail)
                put("date_created", entry.date_created)
                put("date_published", entry.date_published)
                put("date_dead_since", entry.date_dead_since)
                put("age", entry.age ?: 0)
                put("status_code", entry.status_code ?: 0)
                put("manual_status_code", entry.manual_status_code ?: 0)
                put("bookmarked", if (entry.bookmarked == true) 1 else 0)
                // NOT NULL columns required by the schema
                put("source_url", "")
                put("permanent", 0)
                put("contents_type", 0)
                put("page_rating_contents", 0)
            }

            db.beginTransaction()
            try {
                val rowId = db.insert(getTableName(), null, values)
                if (rowId == -1L) {
                    throw SQLException("Insert returned -1; check table schema")
                }

                if (!entry.tags.isNullOrEmpty()) {
                    entry.tags.forEach { tag ->
                        val tagValues = ContentValues().apply {
                            put("entry_id", rowId)
                            put("tag", tag)
                        }
                        db.insert(EntryCompactedTagsRepository.getTableName(), null, tagValues)
                    }
                }

                db.setTransactionSuccessful()
                Triple(true, rowId, null)
            } finally {
                db.endTransaction()
                db.close()
            }
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Entry:${entry.id} Clearing source entries $functionName")

            e.printStackTrace()
            Triple(false, -1L, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates an existing entry's title and description in the database.
     * Entry is identified by its primary key [id] (or [originalLink] if [id] is null).
     */
    suspend fun updateEntrySql(
        context: Context,
        activeDatabaseState: DatabaseState,
        id: Long?,
        originalLink: String?,
        newTitle: String?,
        newDescription: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val extension = activeDatabaseState.extension
        if (extension != ".db") return@withContext false
        if (activeDatabaseState.isReadOnly) return@withContext false

        val fileName = activeDatabaseState.localFileName
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                put("title", newTitle)
                put("description", newDescription)
            }
            val rows = if (id != null) {
                db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            } else if (!originalLink.isNullOrEmpty()) {
                db.update(getTableName(), values, "link = ?", arrayOf(originalLink))
            } else {
                0
            }
            db.close()
            rows > 0
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Entry:${id} Clearing source entries $functionName")

            e.printStackTrace()
            false
        }
    }

    /**
     * Sets the page_rating_votes count for an entry in the SQLite database to [vote] (clamped between MIN_PAGE_RATING_VOTES and MAX_PAGE_RATING_VOTES).
     * @return Pair where first is true on success and second is the new vote total (or null on failure).
     */
    suspend fun setVoteSql(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long?,
        vote: Int
    ): Pair<Boolean, Int?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || id == null) return@withContext Pair(false, null)
        if (activeDatabaseState.extension != ".db") return@withContext Pair(false, null)
        if (activeDatabaseState.isReadOnly) return@withContext Pair(false, null)

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, null)

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val newVotes = vote.coerceIn(MIN_PAGE_RATING_VOTES, MAX_PAGE_RATING_VOTES)
            val values = ContentValues().apply {
                put("page_rating_votes", newVotes)
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, newVotes) else Pair(false, null)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Entry:${id} Setting vote $functionName")

            e.printStackTrace()
            Pair(false, null)
        }
    }

    /**
     * Increments the page_rating_visits count for an entry in the SQLite database.
     */
    suspend fun incrementVisitSql(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long?,
        link: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null) return@withContext false
        if (activeDatabaseState.extension != ".db") return@withContext false
        if (activeDatabaseState.isReadOnly) return@withContext false

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            if (id != null) {
                db.execSQL("UPDATE ${getTableName()} SET page_rating_visits = COALESCE(page_rating_visits, 0) + 1 WHERE id = ?", arrayOf(id.toString()))
            } else if (!link.isNullOrEmpty()) {
                db.execSQL("UPDATE ${getTableName()} SET page_rating_visits = COALESCE(page_rating_visits, 0) + 1 WHERE link = ?", arrayOf(link))
            }
            db.close()
            true
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Entry:${id} incrementing visits in $functionName")

            e.printStackTrace()
            false
        }
    }

    /**
     * Deletes an entry and all related records (tags, socialdata, visits, transitions, readlater) by [id].
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    override suspend fun deleteById(
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
            db.beginTransaction()
            try {
                val rows = deleteEntryRecords(db, id)
                db.setTransactionSuccessful()
                if (rows > 0) Pair(true, null) else Pair(false, "No rows deleted; entry may not exist")
            } finally {
                db.endTransaction()
                db.close()
            }
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Entry:${id} deleting entry $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes a single entry from `linkdatamodel` and all its associated records in auxiliary
     * tables (tags, social data, visit history, transition history, read later).
     *
     * @param db Open writable [SQLiteDatabase] instance.
     * @param entryId Primary key of the entry to delete.
     * @return Number of rows deleted from `linkdatamodel`.
     */
    fun deleteEntryRecords(db: SQLiteDatabase, entryId: Long): Int {
        val idArg = arrayOf(entryId.toString())
        db.delete(EntryCompactedTagsRepository.getTableName(), "entry_id = ?", idArg)
        db.delete(SocialDataRepository.getTableName(), "entry_id = ?", idArg)
        db.delete(EntryVisitHistoryRepository.getTableName(), "entry_id = ?", idArg)
        db.delete(
            EntryTransitionHistoryRepository.getTableName(),
            "entry_from_id = ? OR entry_to_id = ?",
            arrayOf(entryId.toString(), entryId.toString())
        )
        db.delete(ReadLaterRepository.getTableName(), "entry_id = ?", idArg)
        return db.delete(getTableName(), "id = ?", idArg)
    }

    /**
     * Removes all entries in `linkdatamodel` (and their related records in auxiliary tables)
     * belonging to the specified [source] whose links are NOT present in [validLinks].
     *
     * @param db Open writable [SQLiteDatabase] instance.
     * @param source The [Source] whose outdated entries should be removed.
     * @param validLinks Set of valid URLs to keep.
     * @return Number of deleted entries.
     */
    fun removeOutdatedSourceEntries(
        db: SQLiteDatabase,
        source: Source,
        validLinks: Set<String>
    ): Int {
        val validSourceId = source.id?.takeIf { it != 0L }
        val sourceWhereClause: String
        val sourceWhereArgs: Array<String>
        if (validSourceId != null && source.url.isNotBlank()) {
            sourceWhereClause = "(source_id = ? OR source_url = ?)"
            sourceWhereArgs = arrayOf(validSourceId.toString(), source.url)
        } else if (validSourceId != null) {
            sourceWhereClause = "source_id = ?"
            sourceWhereArgs = arrayOf(validSourceId.toString())
        } else {
            sourceWhereClause = "source_url = ?"
            sourceWhereArgs = arrayOf(source.url)
        }

        val existingCursor = db.rawQuery(
            "SELECT id, link FROM ${getTableName()} WHERE $sourceWhereClause",
            sourceWhereArgs
        )
        val entriesToDelete = mutableListOf<Long>()
        existingCursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val link = c.getString(1) ?: ""
                if (link.isNotBlank() && link !in validLinks) {
                    entriesToDelete.add(id)
                }
            }
        }

        for (id in entriesToDelete) {
            deleteEntryRecords(db, id)
        }

        return entriesToDelete.size
    }

    /**
     * Removes all entries in `linkdatamodel` (and their related records in auxiliary tables)
     * belonging to the specified [source] whose links are NOT present in [validLinks].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param source The [Source] whose outdated entries should be removed.
     * @param validLinks Set of valid URLs to keep.
     * @return Pair(success, number of deleted entries).
     */
    suspend fun removeOutdatedSourceEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        source: Source,
        validLinks: Set<String>
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, 0)
        }
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, 0)

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val deletedCount = removeOutdatedSourceEntries(db, source, validLinks)
            db.close()
            Pair(true, deletedCount)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Removing outdated $functionName")

            e.printStackTrace()
            Pair(false, 0)
        }
    }

    /**
     * Deletes all entries belonging to a given source (by `source_id` or `source_url`),
     * including associated auxiliary table records.
     *
     * @param db Open writable [SQLiteDatabase] instance.
     * @param sourceId Source ID to match, or null.
     * @param sourceUrl Source URL to match, or null.
     * @return Number of deleted entries.
     */
    fun deleteEntriesForSource(
        db: SQLiteDatabase,
        sourceId: Long?,
        sourceUrl: String?
    ): Int {
        val validSourceId = sourceId?.takeIf { it != 0L }
        val hasUrl = !sourceUrl.isNullOrBlank()

        if (validSourceId == null && !hasUrl) return 0

        val whereClause: String
        val whereArgs: Array<String>
        if (validSourceId != null && hasUrl) {
            whereClause = "source_id = ? OR source_url = ?"
            whereArgs = arrayOf(validSourceId.toString(), sourceUrl!!)
        } else if (validSourceId != null) {
            whereClause = "source_id = ?"
            whereArgs = arrayOf(validSourceId.toString())
        } else {
            whereClause = "source_url = ?"
            whereArgs = arrayOf(sourceUrl!!)
        }

        val cursor = db.rawQuery("SELECT id FROM ${getTableName()} WHERE $whereClause", whereArgs)
        val entryIdsToDelete = mutableListOf<Long>()
        cursor.use { c ->
            while (c.moveToNext()) {
                entryIdsToDelete.add(c.getLong(0))
            }
        }

        for (entryId in entryIdsToDelete) {
            deleteEntryRecords(db, entryId)
        }

        return entryIdsToDelete.size
    }

    /**
     * Deletes all entries belonging to a given source (by `source_id` or `source_url`),
     * including associated auxiliary table records.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param sourceId Source ID to match, or null.
     * @param sourceUrl Source URL to match, or null.
     * @return Pair(success, number of deleted entries).
     */
    suspend fun deleteEntriesForSource(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceId: Long?,
        sourceUrl: String?
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, 0)
        }
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, 0)

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val count = deleteEntriesForSource(db, sourceId, sourceUrl)
            db.close()
            Pair(true, count)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source:${sourceId}. Removing source entries $functionName")

            e.printStackTrace()
            Pair(false, 0)
        }
    }

    /**
     * Deletes an entry (and its associated tags, history, social data) from the SQLite database.
     * Entry is identified by its primary key [id] (or [link] if [id] is null).
     * @return true if at least one row was deleted, false otherwise.
     */
    suspend fun deleteEntry(
        context: Context,
        activeDatabaseState: DatabaseState,
        id: Long?,
        link: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (id != null) {
            return@withContext deleteById(context, activeDatabaseState, id).first
        }
        if (!activeDatabaseState.isSQLite) return@withContext false
        if (activeDatabaseState.isReadOnly) return@withContext false

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val entryId = if (!link.isNullOrEmpty()) {
                val cursor = db.rawQuery("SELECT id FROM ${getTableName()} WHERE link = ? LIMIT 1", arrayOf(link))
                cursor.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            } else null

            db.close()

            if (entryId != null) {
                deleteById(context, activeDatabaseState, entryId).first
            } else {
                false
            }
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source:${id}. Removing entry $functionName")

            e.printStackTrace()
            false
        }
    }

    /**
     * Clears all records from the `linkdatamodel` table.
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
            db.delete(getTableName(), null, null)
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Clearing $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    private fun countEntriesSql(
        context: Context,
        state: DatabaseState,
        searchQuery: String,
        filterByVisited: Boolean = false,
        filterByReadLater: Boolean = false
    ): Int {
        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) return 0
        return try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val (whereClause, args) = buildWhereClause(searchQuery)
                val extraConditions = mutableListOf<String>()
                if (whereClause.isNotEmpty()) extraConditions.add(whereClause)
                if (filterByVisited) {
                    extraConditions.add("l.id IN (SELECT entry_id FROM entryvisithistory WHERE entry_id IS NOT NULL)")
                }
                if (filterByReadLater) {
                    extraConditions.add("l.id IN (SELECT entry_id FROM readlater WHERE entry_id IS NOT NULL)")
                }
                val whereSql = if (extraConditions.isNotEmpty()) " WHERE " + extraConditions.joinToString(" AND ") else ""
                val sql = "SELECT COUNT(DISTINCT l.id) FROM linkdatamodel l" +
                    " LEFT JOIN entrycompactedtags t ON l.id = t.entry_id" +
                    " LEFT JOIN socialdata s ON l.id = s.entry_id" +
                    whereSql
                val cursor = it.rawQuery(sql, args.toTypedArray())
                cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            }
        } catch (e: Exception) {
            //val functionName = object {}.javaClass.enclosingMethod?.name
            //AppLoggingRepository.error(context, state, "counting entries $functionName")

            e.printStackTrace()
            0
        }
    }

    private fun getPageFromSql(
        context: Context,
        state: DatabaseState,
        searchQuery: String,
        orderBy: OrderBy,
        offset: Int,
        pageSize: Int,
        filterByVisited: Boolean = false,
        filterByReadLater: Boolean = false
    ): List<Entry> {
        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) return emptyList()

        val result = mutableListOf<Entry>()
        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val (whereClause, args) = buildWhereClause(searchQuery)
                val extraConditions = mutableListOf<String>()
                if (whereClause.isNotEmpty()) extraConditions.add(whereClause)
                if (filterByVisited) {
                    extraConditions.add("l.id IN (SELECT entry_id FROM entryvisithistory WHERE entry_id IS NOT NULL)")
                }
                if (filterByReadLater) {
                    extraConditions.add("l.id IN (SELECT entry_id FROM readlater WHERE entry_id IS NOT NULL)")
                }
                val orderSql = orderBy.toSqlColumn()
                val whereSql = if (extraConditions.isNotEmpty()) "WHERE " + extraConditions.joinToString(" AND ") else ""

                // Inner subquery pages on distinct entry IDs, outer join fetches data + tags + socialdata.
                val sql = """
                    SELECT
                        $ENTRY_SELECT_COLUMNS,
                        $SOCIAL_DATA_SELECT_COLUMNS,
                        GROUP_CONCAT(t.tag, ',') AS tag
                    FROM (
                        SELECT DISTINCT l.id
                        FROM linkdatamodel l
                        LEFT JOIN entrycompactedtags t ON l.id = t.entry_id
                        LEFT JOIN socialdata s ON l.id = s.entry_id
                        $whereSql
                        ORDER BY $orderSql, l.id DESC
                        LIMIT ? OFFSET ?
                    ) AS paged
                    JOIN linkdatamodel l ON l.id = paged.id
                    LEFT JOIN entrycompactedtags t ON l.id = t.entry_id
                    LEFT JOIN socialdata s ON l.id = s.entry_id
                    GROUP BY l.id
                    ORDER BY $orderSql, l.id DESC
                """.trimIndent()

                val queryArgs = args + listOf(pageSize.toString(), offset.toString())
                val cursor = it.rawQuery(sql, queryArgs.toTypedArray())
                cursor.use { c ->
                    while (c.moveToNext()) {
                        result.add(cursorToEntry(c))
                    }
                }
            }
        } catch (e: Exception) {
            //val functionName = object {}.javaClass.enclosingMethod?.name
            //AppLoggingRepository.error(context, state, "counting entries $functionName")

            e.printStackTrace()
        }
        return result
    }

    /**
     * Builds a parameterised WHERE clause from [searchQuery] by delegating to
     * [EntrySearchQueryTranslator]. Returns a pair of (clause string, list of bind args).
     *
     * Operator semantics (per project spec):
     *  - `field=value`   → LIKE / contains (e.g. `title=youtube` finds entries with "youtube" in title)
     *  - `field==value`  → exact equality  (e.g. `title==youtube` matches only the exact title)
     *  - `field LIKE …`  → SQLite-style LIKE (contains)
     *  - plain text      → full-text LIKE across title / description / link / tag
     */
    private fun buildWhereClause(searchQuery: String): Pair<String, List<String>> {
        if (searchQuery.isBlank()) return "" to emptyList()

        return when (val parsed = EntrySearchQueryTranslator.parse(searchQuery)) {
            is ParsedQuery.FieldContains -> {
                val term = "%${parsed.term}%"
                when (parsed.field) {
                    "title" -> "l.title LIKE ?" to listOf(term)
                    "link" -> "l.link LIKE ?" to listOf(term)
                    "description" -> "l.description LIKE ?" to listOf(term)
                    "tag", "tags" -> "t.tag LIKE ?" to listOf(term)
                    "source_id" -> "l.source_id LIKE ?" to listOf(term)
                    "source_url", "source" -> "l.source_url LIKE ?" to listOf(term)
                    else -> "" to emptyList()
                }
            }
            is ParsedQuery.FieldExact -> {
                when (parsed.field) {
                    "title" -> "l.title = ?" to listOf(parsed.term)
                    "link" -> "l.link = ?" to listOf(parsed.term)
                    "description" -> "l.description = ?" to listOf(parsed.term)
                    "tag", "tags" -> "t.tag = ?" to listOf(parsed.term)
                    "source_id" -> "l.source_id = ?" to listOf(parsed.term)
                    "source_url", "source" -> "l.source_url = ?" to listOf(parsed.term)
                    else -> "" to emptyList()
                }
            }
            is ParsedQuery.FullText -> {
                val term = "%${parsed.term}%"
                "(l.title LIKE ? OR l.description LIKE ? OR l.link LIKE ? OR t.tag LIKE ?)" to
                    listOf(term, term, term, term)
            }
        }
    }
}

/** Returns the SQL column name + direction for this [OrderBy] value. */
private fun OrderBy.toSqlColumn(): String = when (this) {
    OrderBy.PAGE_RATING_VOTES -> "l.page_rating_votes DESC"
    OrderBy.PAGE_RATING_VISITS_DESC -> "l.page_rating_visits DESC"
    OrderBy.PAGE_RATING_VISITS_ASC -> "l.page_rating_visits ASC"
    OrderBy.DATE_CREATED -> "l.date_created DESC"
    OrderBy.DATE_PUBLISHED -> "l.date_published DESC"
    OrderBy.STARS_DESC -> "COALESCE(s.stars, 0) DESC"
    OrderBy.STARS_ASC -> "COALESCE(s.stars, 0) ASC"
    OrderBy.FOLLOWERS_COUNT_DESC -> "COALESCE(s.followers_count, 0) DESC"
    OrderBy.FOLLOWERS_COUNT_ASC -> "COALESCE(s.followers_count, 0) ASC"
}
