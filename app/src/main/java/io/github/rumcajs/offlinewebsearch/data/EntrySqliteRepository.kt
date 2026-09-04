package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for SQLite-based entry operations on the `linkdatamodel` table.
 * Inherits from [EntryRepository].
 */
object EntrySqliteRepository : EntryRepository() {

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

    override suspend fun getEntriesPageSql(
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

            val values = android.content.ContentValues().apply {
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
                    throw android.database.SQLException("Insert returned -1; check table schema")
                }

                if (!entry.tags.isNullOrEmpty()) {
                    entry.tags.forEach { tag ->
                        val tagValues = android.content.ContentValues().apply {
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
            val values = android.content.ContentValues().apply {
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
            val values = android.content.ContentValues().apply {
                put("page_rating_votes", newVotes)
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, newVotes) else Pair(false, null)
        } catch (e: Exception) {
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
                db.delete(EntryCompactedTagsRepository.getTableName(), "entry_id = ?", arrayOf(id.toString()))
                db.delete(SocialDataRepository.getTableName(), "entry_id = ?", arrayOf(id.toString()))
                db.delete(EntryVisitHistoryRepository.getTableName(), "entry_id = ?", arrayOf(id.toString()))
                db.delete(
                    EntryTransitionHistoryRepository.getTableName(),
                    "entry_from_id = ? OR entry_to_id = ?",
                    arrayOf(id.toString(), id.toString())
                )
                db.delete(ReadLaterRepository.getTableName(), "entry_id = ?", arrayOf(id.toString()))
                val rows = db.delete(getTableName(), "id = ?", arrayOf(id.toString()))
                db.setTransactionSuccessful()
                if (rows > 0) Pair(true, null) else Pair(false, "No rows deleted; entry may not exist")
            } finally {
                db.endTransaction()
                db.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
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
            e.printStackTrace()
        }
        return result
    }

    /**
     * Builds a parameterised WHERE clause from [searchQuery].
     * Returns a pair of (clause string, list of bind args).
     * Supports two expression forms:
     *   - `field LIKE '%value%'`  → translated to a parameterised LIKE
     *   - `field = 'value'`       → translated to a parameterised equality
     * Unrecognised expressions fall back to a full-text LIKE across title/description/link/tag.
     */
    private fun buildWhereClause(searchQuery: String): Pair<String, List<String>> {
        if (searchQuery.isBlank()) return "" to emptyList()
        val query = searchQuery.trim()

        // Match: field = 'value' or field = "value" or field = value
        val eqRegex = Regex(
            """^(title|link|description|tag|tags|source_id|source_url|source)\s*=\s*['""]?([^'""\s]+)['""]?$""",
            RegexOption.IGNORE_CASE
        )
        val eqMatch = eqRegex.find(query)
        if (eqMatch != null) {
            val field = eqMatch.groupValues[1].lowercase()
            val term = eqMatch.groupValues[2].trim()
            return when (field) {
                "title" -> "l.title = ?" to listOf(term)
                "link" -> "l.link = ?" to listOf(term)
                "description" -> "l.description = ?" to listOf(term)
                "tag", "tags" -> "t.tag = ?" to listOf(term)
                "source_id" -> "l.source_id = ?" to listOf(term)
                "source_url", "source" -> "l.source_url = ?" to listOf(term)
                else -> "" to emptyList()
            }
        }

        // Match: field LIKE '%value%'
        val likeRegex = Regex(
            """^(title|link|description|tag|tags|source_id|source_url|source)\s+LIKE\s+['"]?%?([^%'"]+)%?['"]?$""",
            RegexOption.IGNORE_CASE
        )
        val likeMatch = likeRegex.find(query)
        return if (likeMatch != null) {
            val field = likeMatch.groupValues[1].lowercase()
            val term = "%${likeMatch.groupValues[2].trim()}%"
            when (field) {
                "title" -> "l.title LIKE ?" to listOf(term)
                "link" -> "l.link LIKE ?" to listOf(term)
                "description" -> "l.description LIKE ?" to listOf(term)
                "tag", "tags" -> "t.tag LIKE ?" to listOf(term)
                "source_id" -> "l.source_id LIKE ?" to listOf(term)
                "source_url", "source" -> "l.source_url LIKE ?" to listOf(term)
                else -> "" to emptyList()
            }
        } else {
            val term = "%$query%"
            "(l.title LIKE ? OR l.description LIKE ? OR l.link LIKE ? OR t.tag LIKE ?)" to
                listOf(term, term, term, term)
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
