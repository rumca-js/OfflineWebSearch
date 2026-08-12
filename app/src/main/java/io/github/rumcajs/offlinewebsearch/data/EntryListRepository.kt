package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Entry(
    val id: Long? = null,
    val link: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    val album: String? = null,
    val language: String? = null,
    val tags: List<String>? = null,
    val page_rating_votes: Int? = 0,
    val page_rating: Int? = 0,
    val thumbnail: String? = null,
    val date_created: String? = null,
    val date_published: String? = null,
    val date_dead_since: String? = null,
    val age: Int? = 0,
    val status_code: Int? = 0,
    val manual_status_code: Int? = 0,
    val bookmarked: Boolean? = false
)

private val jsonConfig = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private val defaultAssets = listOf(
    "places_0.json",
    "places_1.json",
    "places_2.json",
    "places_3.json",
    "places_4.json",
    "places_5.json",
    "places_6.json",
    "places_7.json",
    "places_8.json",
    "places_9.json",
    "places_10.json",
)

object EntryListRepository {

    // ──────────────────────────────────────────────────────────────────────────
    // Public API – paginated queries
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the total number of entries matching [searchQuery].
     * For SQLite databases the COUNT is run on the DB; JSON/asset sources load into memory.
     */
    suspend fun countEntries(
        context: Context,
        activeDatabaseState: DatabaseState? = null,
        searchQuery: String = "",
        orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES
    ): Int = withContext(Dispatchers.IO) {
        when {
            activeDatabaseState == null ->
                filterInMemory(loadEntriesFromAssets(context, defaultAssets), searchQuery).size
            activeDatabaseState.extension != ".db" ->
                filterInMemory(loadEntriesFromJson(context, activeDatabaseState), searchQuery).size
            else ->
                countEntriesFromSql(context, activeDatabaseState, searchQuery)
        }
    }

    /**
     * Returns a single page of [pageSize] entries starting at [offset].
     * For SQLite the LIMIT/OFFSET is pushed into the query; JSON/asset sources paginate in memory.
     */
    suspend fun loadEntriesPage(
        context: Context,
        activeDatabaseState: DatabaseState? = null,
        searchQuery: String = "",
        orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
        offset: Int = 0,
        pageSize: Int = 20
    ): List<Entry> = withContext(Dispatchers.IO) {
        when {
            activeDatabaseState == null -> {
                val all = filterInMemory(loadEntriesFromAssets(context, defaultAssets), searchQuery)
                all.sortedByOrderBy(orderBy).drop(offset).take(pageSize)
            }
            activeDatabaseState.extension != ".db" -> {
                val all = filterInMemory(loadEntriesFromJson(context, activeDatabaseState), searchQuery)
                all.sortedByOrderBy(orderBy).drop(offset).take(pageSize)
            }
            else ->
                loadPageFromSql(context, activeDatabaseState, searchQuery, orderBy, offset, pageSize)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Write operations
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a new entry (and its tags) into the SQLite database.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun addEntryToSql(
        context: Context,
        activeDatabaseState: DatabaseState,
        entry: Entry
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState.extension != ".db") {
            return@withContext Pair(false, "Database is not a SQLite .db file")
        }
        if (activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is read-only")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found: ${activeDatabaseState.localFileName}")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)

            val values = android.content.ContentValues().apply {
                put("link", entry.link)
                put("title", entry.title)
                put("description", entry.description)
                put("author", entry.author)
                put("album", entry.album)
                put("language", entry.language)
                put("page_rating_votes", entry.page_rating_votes ?: 0)
                put("page_rating", entry.page_rating ?: 0)
                put("thumbnail", entry.thumbnail)
                put("date_created", entry.date_created)
                put("date_published", entry.date_published)
                put("date_dead_since", entry.date_dead_since)
                put("age", entry.age ?: 0)
                put("status_code", entry.status_code ?: 0)
                put("manual_status_code", entry.manual_status_code ?: 0)
                put("bookmarked", if (entry.bookmarked == true) 1 else 0)
            }

            db.beginTransaction()
            try {
                val rowId = db.insert("linkdatamodel", null, values)
                if (rowId == -1L) {
                    throw android.database.SQLException("Insert returned -1; check table schema")
                }

                if (!entry.tags.isNullOrEmpty()) {
                    entry.tags.forEach { tag ->
                        val tagValues = android.content.ContentValues().apply {
                            put("entry_id", rowId)
                            put("tag", tag)
                        }
                        db.insert("entrycompactedtags", null, tagValues)
                    }
                }

                db.setTransactionSuccessful()
                Pair(true, null)
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
     * Updates an existing entry's title and description in the database.
     * Entry is identified by its primary key [id] (or [originalLink] if [id] is null).
     */
    suspend fun updateEntryInSql(
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
                db.update("linkdatamodel", values, "id = ?", arrayOf(id.toString()))
            } else if (!originalLink.isNullOrEmpty()) {
                db.update("linkdatamodel", values, "link = ?", arrayOf(originalLink))
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

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers – SQLite
    // ──────────────────────────────────────────────────────────────────────────

    private fun countEntriesFromSql(
        context: Context,
        state: DatabaseState,
        searchQuery: String
    ): Int {
        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) return 0
        return try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val (whereClause, args) = buildWhereClause(searchQuery)
                val whereSql = if (whereClause.isNotEmpty()) " WHERE $whereClause" else ""
                val sql = "SELECT COUNT(DISTINCT l.id) FROM linkdatamodel l" +
                    " LEFT JOIN entrycompactedtags t ON l.id = t.entry_id" +
                    whereSql
                val cursor = it.rawQuery(sql, args.toTypedArray())
                cursor.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun loadPageFromSql(
        context: Context,
        state: DatabaseState,
        searchQuery: String,
        orderBy: OrderBy,
        offset: Int,
        pageSize: Int
    ): List<Entry> {
        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) return emptyList()

        val result = mutableListOf<Entry>()
        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val (whereClause, args) = buildWhereClause(searchQuery)
                val orderSql = orderBy.toSqlColumn()
                val whereSql = if (whereClause.isNotEmpty()) "WHERE $whereClause" else ""

                // Inner subquery pages on distinct entry IDs, outer join fetches data + tags.
                val sql = """
                    SELECT
                        l.id, l.title, l.description, l.thumbnail, l.link,
                        l.page_rating_votes, l.page_rating, l.date_created, l.date_published,
                        l.date_dead_since, l.age, l.author, l.album, l.language,
                        l.status_code, l.manual_status_code, l.bookmarked,
                        GROUP_CONCAT(t.tag, ',') AS tag
                    FROM (
                        SELECT DISTINCT l.id
                        FROM linkdatamodel l
                        LEFT JOIN entrycompactedtags t ON l.id = t.entry_id
                        $whereSql
                        ORDER BY l.$orderSql
                        LIMIT ? OFFSET ?
                    ) AS paged
                    JOIN linkdatamodel l ON l.id = paged.id
                    LEFT JOIN entrycompactedtags t ON l.id = t.entry_id
                    GROUP BY l.id
                    ORDER BY l.$orderSql
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
     */
    private fun buildWhereClause(searchQuery: String): Pair<String, List<String>> {
        if (searchQuery.isBlank()) return "" to emptyList()
        val query = searchQuery.trim()
        val likeRegex = Regex(
            """^(title|link|description|tag|tags)\s+LIKE\s+['"]?%?([^%'"]+)%?['"]?$""",
            RegexOption.IGNORE_CASE
        )
        val match = likeRegex.find(query)
        return if (match != null) {
            val field = match.groupValues[1].lowercase()
            val term = "%${match.groupValues[2].trim()}%"
            when (field) {
                "title" -> "l.title LIKE ?" to listOf(term)
                "link" -> "l.link LIKE ?" to listOf(term)
                "description" -> "l.description LIKE ?" to listOf(term)
                "tag", "tags" -> "t.tag LIKE ?" to listOf(term)
                else -> "" to emptyList()
            }
        } else {
            val term = "%$query%"
            "(l.title LIKE ? OR l.description LIKE ? OR l.link LIKE ? OR t.tag LIKE ?)" to
                listOf(term, term, term, term)
        }
    }

    /** Maps a cursor row to an [Entry]. */
    private fun cursorToEntry(c: android.database.Cursor): Entry {
        val id = c.getLong(c.getColumnIndexOrThrow("id"))
        val title = c.getString(c.getColumnIndexOrThrow("title"))
        val description = c.getString(c.getColumnIndexOrThrow("description"))
        val thumbnail = c.getString(c.getColumnIndexOrThrow("thumbnail"))
        val link = c.getString(c.getColumnIndexOrThrow("link"))
        val votes = c.getInt(c.getColumnIndexOrThrow("page_rating_votes"))
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
        val tagString = c.getString(c.getColumnIndexOrThrow("tag"))
        val tags = tagString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

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
            page_rating = rating,
            date_created = dateCreated,
            date_published = datePublished,
            date_dead_since = dateDeadSince,
            age = age,
            status_code = statusCode,
            manual_status_code = manualStatusCode,
            bookmarked = bookmarked,
            tags = tags
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers – JSON / assets
    // ──────────────────────────────────────────────────────────────────────────

    private fun loadEntriesFromAssets(context: Context, assets: List<String>): List<Entry> {
        val loaded = mutableListOf<Entry>()
        assets.forEach { fileName ->
            try {
                context.assets.open(fileName).bufferedReader().use { reader ->
                    val jsonString = reader.readText()
                    val places: List<Entry> = jsonConfig.decodeFromString(jsonString)
                    loaded.addAll(places)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return loaded
    }

    private fun loadEntriesFromJson(context: Context, state: DatabaseState): List<Entry> {
        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) return emptyList()
        return try {
            file.bufferedReader().use { reader ->
                jsonConfig.decodeFromString(reader.readText())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun filterInMemory(entries: List<Entry>, searchQuery: String): List<Entry> {
        if (searchQuery.isBlank()) return entries
        val query = searchQuery.trim()
        val likeRegex = Regex(
            """^(title|link|description|tag|tags)\s+LIKE\s+['"]?%?([^%'"]+)%?['"]?$""",
            RegexOption.IGNORE_CASE
        )
        val match = likeRegex.find(query)
        return if (match != null) {
            val field = match.groupValues[1].lowercase()
            val term = match.groupValues[2].trim()
            entries.filter { entry ->
                when (field) {
                    "title" -> entry.title?.contains(term, ignoreCase = true) == true
                    "link" -> entry.link?.contains(term, ignoreCase = true) == true
                    "description" -> entry.description?.contains(term, ignoreCase = true) == true
                    "tag", "tags" -> entry.tags?.any { it.contains(term, ignoreCase = true) } == true
                    else -> false
                }
            }
        } else {
            entries.filter { entry ->
                entry.title?.contains(query, ignoreCase = true) == true ||
                    entry.description?.contains(query, ignoreCase = true) == true ||
                    entry.link?.contains(query, ignoreCase = true) == true ||
                    entry.tags?.any { it.contains(query, ignoreCase = true) } == true
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Extension helpers
// ──────────────────────────────────────────────────────────────────────────────

/** Returns the SQL column name + direction for this [OrderBy] value. */
private fun OrderBy.toSqlColumn(): String = when (this) {
    OrderBy.PAGE_RATING_VOTES -> "page_rating_votes DESC"
    OrderBy.DATE_CREATED -> "date_created DESC"
    OrderBy.DATE_PUBLISHED -> "date_published DESC"
}

/** Sorts a list of [Entry] in descending order for this [OrderBy] value. */
private fun List<Entry>.sortedByOrderBy(orderBy: OrderBy): List<Entry> = when (orderBy) {
    OrderBy.PAGE_RATING_VOTES -> sortedByDescending { it.page_rating_votes ?: 0 }
    OrderBy.DATE_CREATED -> sortedByDescending { it.date_created ?: "" }
    OrderBy.DATE_PUBLISHED -> sortedByDescending { it.date_published ?: "" }
}
