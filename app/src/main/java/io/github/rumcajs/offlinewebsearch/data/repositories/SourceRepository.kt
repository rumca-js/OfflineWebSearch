package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.RssPage
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class Source(
    val id: Long? = null,
    val enabled: Boolean = true,
    val url: String = "",
    val title: String = "",
    val favicon: String = "",
    val source_type: String? = null,
    val age: Int? = 0,
    val auto_tag: String = "",
    val language: String = ""
)

object SourceRepository : RepositoryInterface {
    val SOURCE_TYPE_RSS = "RSS"
    val SOURCE_TYPE_PARSE = "Parse"
    val SOURCE_TYPE_EMAIL = "Email"

    override fun getTableName(): String = "sourcedatamodel"

    /**
     * Reads the current cursor row and constructs a [Source] from it.
     * Assumes columns: id, enabled, url, title, favicon, source_type, age, auto_tag, language.
     */
    private fun cursorToSource(cursor: Cursor): Source {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        val enabledVal = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
        val url = cursor.getString(cursor.getColumnIndexOrThrow("url")) ?: ""
        val title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: ""
        val favicon = cursor.getString(cursor.getColumnIndexOrThrow("favicon")) ?: ""
        val sourceType = cursor.getString(cursor.getColumnIndexOrThrow("source_type"))
        val ageIdx = cursor.getColumnIndex("age")
        val age = if (ageIdx != -1 && !cursor.isNull(ageIdx)) cursor.getInt(ageIdx) else 0
        val autoTagIdx = cursor.getColumnIndex("auto_tag")
        val autoTag = if (autoTagIdx != -1 && !cursor.isNull(autoTagIdx)) cursor.getString(autoTagIdx) else ""
        val languageIdx = cursor.getColumnIndex("language")
        val language = if (languageIdx != -1 && !cursor.isNull(languageIdx)) cursor.getString(languageIdx) else ""
        return Source(
            id = id,
            enabled = enabledVal == 1,
            url = url,
            title = title,
            favicon = favicon,
            source_type = sourceType,
            age = age,
            auto_tag = autoTag,
            language = language
        )
    }

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
            val sqlText = "SELECT id, enabled, url, title, favicon, source_type, age, auto_tag, language FROM ${getTableName()} ORDER BY url, title"
            val cursor = db.rawQuery(sqlText, null)
            cursor.use {
                while (it.moveToNext()) {
                    sources.add(cursorToSource(it))
                }
            }
            db.close()
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Exception when getting all sources in $functionName")

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
            val sqlText = "SELECT s.id AS id, s.enabled AS enabled, s.url AS url, s.title AS title, " +
                    "s.favicon AS favicon, s.source_type AS source_type, s.age AS age, " +
                    "s.auto_tag AS auto_tag, s.language AS language " +
                    "FROM ${getTableName()} AS s " +
                    "LEFT JOIN sourceoperationaldata sod ON s.id = sod.source_obj_id ORDER BY sod.date_fetched ASC"
            val cursor = db.rawQuery(sqlText, null)
            cursor.use {
                while (it.moveToNext()) {
                    val source = cursorToSource(it)
                    if (source.enabled) {
                        sources.add(source)
                    }
                }
            }
            db.close()
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Exception when getting sources in $functionName")

            e.printStackTrace()
        }

        sources
    }

    private val sourceTitleCache = ConcurrentHashMap<Pair<String, Long>, String?>()

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
                val sqlText = "SELECT id, enabled, url, title, favicon, source_type, age, auto_tag, language FROM ${getTableName()} WHERE id = ? LIMIT 1"
                val cursor = it.rawQuery(sqlText, arrayOf(sourceId.toString()))
                cursor.use { c ->
                    if (c.moveToFirst()) cursorToSource(c) else null
                }
            }
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source ID: $sourceId Exception when getting source properties in $functionName")

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
                val sqlText = "SELECT id, enabled, url, title, favicon, source_type, age, auto_tag, language FROM ${getTableName()} WHERE url = ? LIMIT 1"
                val cursor = it.rawQuery(sqlText, arrayOf(sourceUrl))
                cursor.use { c ->
                    if (c.moveToFirst()) cursorToSource(c) else null
                }
            }
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Url: $sourceUrl Exception when getting source properties in $functionName")

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
        enabled: Boolean,
        age: Int = 0,
        auto_tag: String = "",
        language: String = ""
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                put("title", title)
                put("url", url)
                put("enabled", if (enabled) 1 else 0)
                put("source_type", "")
                put("category_name", "")
                put("subcategory_name", "")
                put("export_to_cms", false)
                put("remove_after_days", 0)
                put("language", language)
                put("age", if (age >= 0) age else 0)
                put("favicon", "")
                put("fetch_period", 3600)
                put("auto_tag", auto_tag.take(1000))
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
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Url: $url Exception when inserting source properties in $functionName")

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
        enabled: Boolean,
        age: Int? = null,
        auto_tag: String? = null,
        language: String? = null
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                put("title", title)
                put("url", url)
                put("enabled", if (enabled) 1 else 0)
                if (age != null) {
                    put("age", if (age >= 0) age else 0)
                }
                if (auto_tag != null) {
                    put("auto_tag", auto_tag.take(1000))
                }
                if (language != null) {
                    put("language", language.take(1000))
                }
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; source may not exist")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Url: $url Exception when updating source properties in $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates the auto_tag setting for a source in `sourcedatamodel`.
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param id ID of the source.
     * @param autoTag Comma-separated tags string to be automatically applied to read entries.
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun updateSourceAutoTag(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long,
        autoTag: String
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                put("auto_tag", autoTag.take(1000))
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; source may not exist")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source ID: $id Exception when updating source auto_tag in $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates the age designation for a source in `sourcedatamodel`.
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param id ID of the source.
     * @param age New age designation (defaults to 0 if <= 0).
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun updateSourceAge(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long,
        age: Int
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                put("age", if (age >= 0) age else 0)
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; source may not exist")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source ID: $id Exception when updating source age in $functionName")

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
        urlObj: Url
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
            val sourceValues = ContentValues()
            if (!title.isNullOrBlank()) sourceValues.put("title", title)
            if (!favicon.isNullOrBlank()) sourceValues.put("favicon", favicon)
            val rows = db.update(getTableName(), sourceValues, "url = ?", arrayOf(urlObj.url))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; source URL may not exist")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Url: ${urlObj.url} Exception when updating metadata in $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Fetches entries from [urlObj] (expected to be RSS/Atom feed) and inserts new entries into `linkdatamodel`.
     * Existing entries (matching by link) are not duplicated.
     * [Source.id] and [Source.url] are associated with every inserted entry.
     * @return Pair(success, resultMessage)
     */
    suspend fun fetchAndInsertSourceEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        urlObj: Url,
        source: Source
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

            if (resp.error != null || !NetworkUtils.isStatusCodeValid(resp.statusCode)) {
                val errorMsg = resp.error ?: "HTTP status ${resp.statusCode}"
                return@withContext Pair(false, "Failed to fetch source: $errorMsg")
            }

            val page = urlObj.getPage()
            if (page !is RssPage) {
                return@withContext Pair(false, "URL does not point to a valid RSS or Atom feed")
            }

            val entries = page.getEntries()
            if (entries.isEmpty()) {
                return@withContext Pair(true, "No entries found in feed")
            }

            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            var insertedCount = 0

            db.beginTransaction()
            try {
                // 1. Collect all non-blank links from the newly fetched RSS feed
                val rssLinks = entries.mapNotNull { it.link?.takeIf { l -> l.isNotBlank() } }.toSet()

                // 2. Remove entries that are no longer present in the RSS feed
                val sourceForOps = if (source.url.isNotBlank()) source else source.copy(url = urlObj.url)
                EntrySqliteRepository.removeOutdatedSourceEntries(db, sourceForOps, rssLinks)

                // 3. Insert new entries from the feed
                insertedCount = insertSourceEntries(db, entries, sourceForOps)

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.close()
            }

            val sourceId = source.id?.takeIf { it != 0L }
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
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Url: ${urlObj.url} Exception when adding in $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Failed to fetch or insert entries")
        }
    }

    /**
     * Removes all entries in `linkdatamodel` (and their related records in auxiliary tables)
     * belonging to the specified [source] whose links are NOT present in [validLinks].
     * Delegated to [EntrySqliteRepository.removeOutdatedSourceEntries].
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
    ): Pair<Boolean, Int> = EntrySqliteRepository.removeOutdatedSourceEntries(context, activeDatabaseState, source, validLinks)

    /**
     * Inserts new [entries] belonging to a [source] into `linkdatamodel`.
     * Existing entries (matching by link) are skipped to avoid duplicates.
     *
     * @param db Open writable [SQLiteDatabase] instance.
     * @param entries List of [Entry] objects from the feed to insert.
     * @param source The [Source] to associate with the inserted entries.
     * @param defaultDateCreated Fallback timestamp for date_created if not set on the entry.
     * @return Number of successfully inserted entries.
     */
    fun insertSourceEntries(
        db: SQLiteDatabase,
        entries: List<Entry>,
        source: Source,
        defaultDateCreated: String = DateUtils.getCurrentTimestamp()
    ): Int {
        var insertedCount = 0
        val validSourceId = source.id?.takeIf { it != 0L }

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

            val ageDesignation = when {
                (entry.age ?: 0) > 0 -> entry.age ?: 0
                (source.age ?: 0) > 0 -> source.age ?: 0
                else -> 0
            }

            var languageDesignation = source.language
            if (!entry.language.isNullOrBlank()) {
                languageDesignation = entry.language
            }

            val values = ContentValues().apply {
                put("link", link)
                put("title", entry.title ?: "")
                put("description", entry.description ?: "")
                put("author", entry.author ?: "")
                put("album", entry.album ?: "")
                put("language", languageDesignation)
                put("page_rating_votes", entry.page_rating_votes ?: 0)
                put("page_rating_visits", entry.page_rating_visits ?: 0)
                put("page_rating", entry.page_rating ?: 0)
                put("thumbnail", entry.thumbnail ?: "")
                put("date_created", entry.date_created?.takeIf { it.isNotBlank() } ?: defaultDateCreated)
                put("date_published", entry.date_published ?: "")
                put("date_dead_since", entry.date_dead_since ?: "")
                put("age", ageDesignation)
                put("status_code", entry.status_code ?: 0)
                put("manual_status_code", entry.manual_status_code ?: 0)
                put("bookmarked", if (entry.bookmarked == true) 1 else 0)
                put("source_url", source.url)
                if (validSourceId != null) {
                    put("source_id", validSourceId)
                }
                put("permanent", 0)
                put("contents_type", 0)
                put("page_rating_contents", 0)
            }

            val rowId = db.insert("linkdatamodel", null, values)
            if (rowId != -1L) {
                insertedCount++

                val tagsToInsert = getSourceTags(entry, source)
                if (tagsToInsert.isNotEmpty()) {
                    EntryCompactedTagsRepository.ensureTableExists(db)
                    for (tag in tagsToInsert) {
                        val tagValues = ContentValues().apply {
                            put("entry_id", rowId)
                            put("tag", tag.take(1000))
                        }
                        db.insert(EntryCompactedTagsRepository.getTableName(), null, tagValues)
                    }
                }
            }
        }
        return insertedCount
    }

    /**
     * Builds the set of tags to associate with an entry when it is inserted.
     * Combines tags declared on the entry itself with any auto-tags defined on the source.
     *
     * @param entry The entry being inserted.
     * @param source The source the entry belongs to.
     * @return Set of non-blank, trimmed tag strings to insert.
     */
    fun getSourceTags(entry: Entry, source: Source): Set<String> {
        val tagsToInsert = mutableSetOf<String>()
        if (!entry.tags.isNullOrEmpty()) {
            tagsToInsert.addAll(entry.tags.map { it.trim() }.filter { it.isNotEmpty() })
        }
        if (source.auto_tag.isNotBlank()) {
            tagsToInsert.addAll(source.auto_tag.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        }
        return tagsToInsert
    }

    /**
     * Inserts new [entries] belonging to a [source] into `linkdatamodel`.
     * Existing entries (matching by link) are skipped to avoid duplicates.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param entries List of [Entry] objects from the feed to insert.
     * @param source The [Source] to associate with the inserted entries.
     * @return Pair(success, number of inserted entries).
     */
    suspend fun insertSourceEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        entries: List<Entry>,
        source: Source
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, 0)
        }
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, 0)

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val insertedCount = insertSourceEntries(db, entries, source)
            db.close()
            Pair(true, insertedCount)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source:$source.id}. Error on Inserting source entries $functionName")

            e.printStackTrace()
            Pair(false, 0)
        }
    }

    /**
     * Updates source metadata (title, favicon) and inserts new entries into `linkdatamodel` from [urlObj].
     * [source] is associated with inserted entries.
     * @return Pair(success, resultMessage)
     */
    suspend fun updateSourceMetaAndEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        urlObj: Url,
        source: Source
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
        val urlObj = Url(source.url)
        val response = urlObj.getResponse();
        if (!response.isValid)
        {
            AppLoggingRepository.error(context, activeDatabaseState, "Failed to fetch source: ${source.url}", "Status code:${response.statusCode} Error:${response.error}")
        }
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
    ): Pair<Boolean, String?> = deleteSource(context, activeDatabaseState, id, deleteEntries = false)

    /**
     * Deletes a source by ID and cleans up associated operational data.
     * When [deleteEntries] is true, also removes all entries in `linkdatamodel` whose
     * `source_id` or `source_url` matches this source (and cleans up their auxiliary records).
     *
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun deleteSource(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long,
        deleteEntries: Boolean = false
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)

            val sourceUrl: String? = db.rawQuery(
                "SELECT url FROM ${getTableName()} WHERE id = ?",
                arrayOf(id.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

            if (deleteEntries) {
                EntrySqliteRepository.deleteEntriesForSource(db, id, sourceUrl)
            }

            val rows = db.delete(getTableName(), "id = ?", arrayOf(id.toString()))
            db.close()

            if (rows > 0) {
                SourceOperationalDataRepository.deleteOperationalDataBySourceId(context, activeDatabaseState, id)
                Pair(true, null)
            } else {
                Pair(false, "No rows deleted; source may not exist")
            }
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source:$id}. Error on Inserting source entries $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }


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
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Clearing source entries $functionName")

            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }
}

