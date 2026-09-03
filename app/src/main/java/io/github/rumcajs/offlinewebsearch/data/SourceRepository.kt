package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class Source(
    val id: Long? = null,
    val enabled: Boolean = true,
    val url: String = "",
    val title: String = "",
    val favicon: String = "",
    val source_type: String? = null
)

object SourceRepository : RepositoryInterface {
    val SOURCE_TYPE_RSS = "RSS"
    val SOURCE_TYPE_PARSE = "Parse"
    val SOURCE_TYPE_EMAIL = "Email"

    override fun getTableName(): String = "sourcedatamodel"

    /**
     * This function return all sources
     */
    suspend fun getAllSources(context: Context, activeDatabaseState: DatabaseState?): List<Source> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<Source>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext sources
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext sources

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val sqlText = "SELECT id, enabled, url, title, favicon, source_type FROM ${getTableName()} ORDER BY url, title"
            val cursor = db.rawQuery(sqlText, null)
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("id"))
                    val enabledVal = it.getInt(it.getColumnIndexOrThrow("enabled"))
                    val url = it.getString(it.getColumnIndexOrThrow("url")) ?: ""
                    val title = it.getString(it.getColumnIndexOrThrow("title")) ?: ""
                    val favicon = it.getString(it.getColumnIndexOrThrow("favicon")) ?: ""
                    val sourceType = it.getString(it.getColumnIndexOrThrow("source_type"))

                    sources.add(
                        Source(
                            id = id,
                            enabled = enabledVal == 1,
                            url = url,
                            title = title,
                            favicon = favicon,
                            source_type = sourceType
                        )
                    )
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sources
    }

    /**
     * Source for refresh
     */
    suspend fun getSourcesByFetchTime(context: Context, activeDatabaseState: DatabaseState?): List<Source> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<Source>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext sources
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext sources

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val sqlText = "SELECT s.id, s.enabled, s.url, s.title, s.favicon, s.source_type FROM ${getTableName()} AS s " +
                    "LEFT JOIN sourceoperationaldata sod ON s.id = sod.source_obj_id ORDER BY sod.date_fetched ASC";
            val cursor = db.rawQuery(sqlText, null)
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("s.id"))
                    val enabledVal = it.getInt(it.getColumnIndexOrThrow("s.enabled"))
                    val url = it.getString(it.getColumnIndexOrThrow("s.url")) ?: ""
                    val title = it.getString(it.getColumnIndexOrThrow("s.title")) ?: ""
                    val favicon = it.getString(it.getColumnIndexOrThrow("s.favicon")) ?: ""
                    val sourceType = it.getString(it.getColumnIndexOrThrow("s.source_type"))

                    if (enabledVal == 1) {
                        sources.add(
                            Source(
                                id = id,
                                enabled = true,
                                url = url,
                                title = title,
                                favicon = favicon,
                                source_type = sourceType
                            )
                        )
                    }
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sources
    }

    private val sourceTitleCache = java.util.concurrent.ConcurrentHashMap<Pair<String, Long>, String?>()

    /**
     * Clears cached source titles.
     */
    fun clearCache() {
        sourceTitleCache.clear()
    }

    /**
     * Looks up the title of a source by [sourceId], caching the result.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param sourceId ID of the source in `sourcedatamodel`.
     * @return Title of the source, or null if not found.
     */
    suspend fun getSourceTitleById(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceId: Long
    ): String? = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext null
        val cacheKey = Pair(activeDatabaseState.localFileName, sourceId)
        sourceTitleCache[cacheKey]?.let { return@withContext it }

        val source = getSourceById(context, activeDatabaseState, sourceId)
        val title = source?.title?.takeIf { it.isNotBlank() }
        if (title != null) {
            sourceTitleCache[cacheKey] = title
        }
        title
    }

    /**
     * Finds a source in `sourcedatamodel` matching [sourceId].
     */
    suspend fun getSourceById(context: Context, activeDatabaseState: DatabaseState?, sourceId: Long): Source? = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext null
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val sqlText = "SELECT id, enabled, url, title, favicon, source_type FROM ${getTableName()} WHERE id = ? LIMIT 1"
                val cursor = it.rawQuery(sqlText, arrayOf(sourceId.toString()))
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow("id"))
                        val enabledVal = c.getInt(c.getColumnIndexOrThrow("enabled"))
                        val url = c.getString(c.getColumnIndexOrThrow("url")) ?: ""
                        val title = c.getString(c.getColumnIndexOrThrow("title")) ?: ""
                        val favicon = c.getString(c.getColumnIndexOrThrow("favicon")) ?: ""
                        val sourceType = c.getString(c.getColumnIndexOrThrow("source_type"))
                        Source(id = id, enabled = enabledVal == 1, url = url, title = title, favicon = favicon, source_type = sourceType)
                    } else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Finds a source in `sourcedatamodel` matching [sourceUrl].
     */
    suspend fun getSourceByUrl(context: Context, activeDatabaseState: DatabaseState?, sourceUrl: String): Source? = withContext(Dispatchers.IO) {
        if (sourceUrl.isBlank()) return@withContext null
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext null
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val sqlText = "SELECT id, enabled, url, title, favicon, source_type FROM ${getTableName()} WHERE url = ? LIMIT 1"
                val cursor = it.rawQuery(sqlText, arrayOf(sourceUrl))
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow("id"))
                        val enabledVal = c.getInt(c.getColumnIndexOrThrow("enabled"))
                        val url = c.getString(c.getColumnIndexOrThrow("url")) ?: ""
                        val title = c.getString(c.getColumnIndexOrThrow("title")) ?: ""
                        val favicon = c.getString(c.getColumnIndexOrThrow("favicon")) ?: ""
                        val sourceType = c.getString(c.getColumnIndexOrThrow("source_type"))
                        Source(id = id, enabled = enabledVal == 1, url = url, title = title, favicon = favicon, source_type = sourceType)
                    } else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Inserts a new source into the database.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun insertSource(
        context: Context,
        activeDatabaseState: DatabaseState?,
        title: String,
        url: String,
        enabled: Boolean
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = android.content.ContentValues().apply {
                put("title", title)
                put("url", url)
                put("enabled", if (enabled) 1 else 0)
                put("source_type", "")
                put("category_name", "")
                put("subcategory_name", "")
                put("export_to_cms", false)
                put("remove_after_days", 0)
                put("language", "")
                put("age", 0)
                put("favicon", "")
                put("fetch_period", 3600)
                put("auto_tag", "")
                put("entries_backgroundcolor_alpha", 1.0)
                put("entries_backgroundcolor", "")
                put("entries_alpha", 1.0)
                put("proxy_location", "")
                put("auto_update_favicon", false)
                put("category_id", 0)
                put("subcategory_id", 0)
                put("xpath", "")
            }
            val newId = db.insert(getTableName(), null, values)
            db.close()
            if (newId != -1L) Pair(true, null) else Pair(false, "Insert returned -1; check table schema")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates an existing source.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun updateSourceProperties(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long,
        title: String,
        url: String,
        enabled: Boolean
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = android.content.ContentValues().apply {
                put("title", title)
                put("url", url)
                put("enabled", if (enabled) 1 else 0)
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; source may not exist")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates source metadata (such as title and favicon) matching [urlObj.url] in `sourcedatamodel` using [Url.getTitle] and [Url.getThumbnails].
     */
    suspend fun updateSourceMetadata(
        context: Context,
        activeDatabaseState: DatabaseState?,
        urlObj: io.github.rumcajs.offlinewebsearch.webtoolkit.Url
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val title = urlObj.getTitle()
            val thumbnails = urlObj.getThumbnails()
            val favicon = thumbnails.firstOrNull { it.isNotBlank() }

            if (title.isNullOrBlank() && favicon.isNullOrBlank()) {
                return@withContext Pair(true, null)
            }

            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val sourceValues = android.content.ContentValues()
            if (!title.isNullOrBlank()) sourceValues.put("title", title)
            if (!favicon.isNullOrBlank()) sourceValues.put("favicon", favicon)
            val rows = db.update(getTableName(), sourceValues, "url = ?", arrayOf(urlObj.url))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; source URL may not exist")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Fetches entries from [urlObj] (expected to be RSS/Atom feed) and inserts new entries into `linkdatamodel`.
     * Existing entries (matching by link) are not duplicated.
     * [source] is optional; when provided, [Source.id] is stored as `source_id` on every inserted entry.
     * @return Pair(success, resultMessage)
     */
    suspend fun fetchAndInsertSourceEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        urlObj: io.github.rumcajs.offlinewebsearch.webtoolkit.Url,
        source: Source? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (urlObj.url.isBlank()) {
            return@withContext Pair(false, "Source URL is empty")
        }
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) {
            return@withContext Pair(false, "Database file not found")
        }

        try {
            val resp = urlObj.getResponse()

            if (resp.error != null || !io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils.isStatusCodeValid(resp.statusCode)) {
                val errorMsg = resp.error ?: "HTTP status ${resp.statusCode}"
                return@withContext Pair(false, "Failed to fetch source: $errorMsg")
            }

            val page = urlObj.getPage()
            if (page !is io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage) {
                return@withContext Pair(false, "URL does not point to a valid RSS or Atom feed")
            }

            val entries = page.getEntries()
            if (entries.isEmpty()) {
                return@withContext Pair(true, "No entries found in feed")
            }

            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            var insertedCount = 0

            db.beginTransaction()
            val now = DateUtils.getCurrentTimestamp() // TODO: not ISO?
            try {

                for (entry in entries) {
                    val link = entry.link ?: ""
                    if (link.isNotBlank()) {
                        val checkCursor = db.rawQuery("SELECT COUNT(*) FROM linkdatamodel WHERE link = ?", arrayOf(link))
                        val exists = checkCursor.use { c ->
                            if (c.moveToFirst()) c.getInt(0) > 0 else false
                        }
                        if (exists) {
                            continue
                        }
                    }

                    // TODO use EntryRepostiroy for that?
                    val values = android.content.ContentValues().apply {
                        put("link", link)
                        put("title", entry.title ?: "")
                        put("description", entry.description ?: "")
                        put("author", entry.author ?: "")
                        put("album", entry.album ?: "")
                        put("language", entry.language ?: "")
                        put("page_rating_votes", entry.page_rating_votes ?: 0)
                        put("page_rating_visits", entry.page_rating_visits ?: 0)
                        put("page_rating", entry.page_rating ?: 0)
                        put("thumbnail", entry.thumbnail ?: "")
                        put("date_created", entry.date_created?.takeIf { it.isNotBlank() } ?: now)
                        put("date_published", entry.date_published ?: "")
                        put("date_dead_since", entry.date_dead_since ?: "")
                        put("age", entry.age ?: 0)
                        put("status_code", entry.status_code ?: 0)
                        put("manual_status_code", entry.manual_status_code ?: 0)
                        put("bookmarked", if (entry.bookmarked == true) 1 else 0)
                        put("source_url", urlObj.url)
                        val sourceId = source?.id
                        if (sourceId != null && sourceId != 0L) {
                            put("source_id", sourceId)
                        }
                        put("permanent", 0)
                        put("contents_type", 0)
                        put("page_rating_contents", 0)
                    }

                    val rowId = db.insert("linkdatamodel", null, values)
                    if (rowId != -1L) {
                        insertedCount++
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.close()
            }

            val sourceId = source?.id?.takeIf { it != 0L }
            if (sourceId != null) {
                SourceOperationalDataRepository.setSourceFetch(
                    context = context,
                    activeDatabaseState = activeDatabaseState,
                    sourceObjId = sourceId,
                    numberOfEntries = entries.size,
                    pageHash = page.getHash(),
                    bodyHash = page.getBodyHash()
                )
            } else {
                SourceOperationalDataRepository.setSourceFetchByUrl(
                    context = context,
                    activeDatabaseState = activeDatabaseState,
                    sourceUrl = urlObj.url,
                    numberOfEntries = entries.size,
                    pageHash = page.getHash(),
                    bodyHash = page.getBodyHash()
                )
            }

            Pair(true, "Successfully inserted $insertedCount new entries")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Failed to fetch or insert entries")
        }
    }

    /**
     * Updates source metadata (title, favicon) and inserts new entries into `linkdatamodel` from [urlObj].
     * [source] is optional; when provided, [Source.id] is stored as `source_id` on every inserted entry.
     * @return Pair(success, resultMessage)
     */
    suspend fun updateSourceMetaAndEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        urlObj: io.github.rumcajs.offlinewebsearch.webtoolkit.Url,
        source: Source? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        updateSourceMetadata(context, activeDatabaseState, urlObj)
        fetchAndInsertSourceEntries(context, activeDatabaseState, urlObj, source)
    }

    /**
     * Updates source metadata (title, favicon) and inserts new entries into `linkdatamodel` from [source].
     * Uses [Source.url] for the fetch and [Source.id] for `source_id` on inserted entries.
     * Skips fetch if source is disabled or if last fetch was less than an hour ago.
     * @return Pair(success, resultMessage)
     */
    suspend fun updateSourceMetaAndEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        source: Source
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (source.url.isBlank()) {
            return@withContext Pair(false, "Source URL is empty")
        }
        if (!source.enabled) {
            return@withContext Pair(false, "Source is disabled")
        }

        val sourceId = source.id
        if (sourceId != null) {
            val data = SourceOperationalDataRepository.getOperationalDataBySourceId(
                context,
                activeDatabaseState,
                sourceId
            )
            if (!SourceOperationalDataRepository.isFetchOutdated(data?.date_fetched)) {
                return@withContext Pair(false, "Source was fetched recently (less than 1 hour ago)")
            }
        }
        val urlObj = io.github.rumcajs.offlinewebsearch.webtoolkit.Url(source.url)
        updateSourceMetaAndEntries(context, activeDatabaseState, urlObj, source)
    }

    /**
     * Checks fetch times of all enabled sources and fetches any sources whose fetch timestamp is older
     * than 1 hour, or never fetched.
     * @return number of successfully refreshed sources.
     */
    suspend fun updateOutdatedSources(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Int = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext 0
        }
        val config = AppConfigManager.config.value
        if (config.networkConfig.disabled) {
            return@withContext 0
        }

        val sources = getSourcesByFetchTime(context, activeDatabaseState).filter { it.enabled && it.url.isNotBlank() }
        if (sources.isEmpty()) return@withContext 0

        var refreshedCount = 0
        for (source in sources) {
            val (success, _) = updateSourceMetaAndEntries(context, activeDatabaseState, source)
            if (success) {
                refreshedCount++
            }
        }
        refreshedCount
    }

    /**
     * Deletes a source by ID from `sourcedatamodel` and cleans up associated operational data.
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
            val rows = db.delete(getTableName(), "id = ?", arrayOf(id.toString()))
            db.close()

            if (rows > 0) {
                SourceOperationalDataRepository.deleteOperationalDataBySourceId(context, activeDatabaseState, id)
                Pair(true, null)
            } else {
                Pair(false, "No rows deleted; source may not exist")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Deletes a source by ID (alias for [deleteById]).
     */
    suspend fun deleteSource(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Pair<Boolean, String?> = deleteById(context, activeDatabaseState, id)


    /**
     * Clears all records from the `sourcedatamodel` table.
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
}

