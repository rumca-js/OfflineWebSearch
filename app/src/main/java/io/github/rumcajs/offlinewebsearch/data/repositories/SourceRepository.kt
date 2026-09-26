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


@Serializable
data class Source(
    val id: Long? = null,
    val enabled: Boolean = true,
    val url: String = "",
    val title: String = "",
    val favicon: String = "",
    val fetch_period: Long = 0,
    val source_type: String? = null,
    val age: Int? = 0,
    val auto_tag: String = "",
    val language: String = ""
)

/**
 * Sort mode applied to source queries in [SourceRepository].
 */
@Serializable
enum class SourceOrder { ByUrl, ByTitle, ByFetchTime, ByConsecutiveErrors }

/**
 * Data class representing a [Source] paired with its optional [SourceOperationalData]
 * obtained from an outer join between `sourcedatamodel` and `sourceoperationaldata`.
 */
@Serializable
data class SourceWithOperationalData(
    val source: Source,
    val operationalData: SourceOperationalData? = null
)

object SourceRepository : RepositoryInterface {
    val SOURCE_TYPE_RSS = "RSS"
    val SOURCE_TYPE_PARSE = "Parse"
    val SOURCE_TYPE_EMAIL = "Email"

    val COLUMNS = arrayOf(
        "id", "enabled", "url", "title", "favicon",
        "source_type", "age", "auto_tag", "fetch_period", "language"
    )

    override fun getTableName(): String = "sourcedatamodel"

    /**
     * Builds projection column string for SQL queries, supporting optional table alias and column alias prefix.
     */
    fun getColumnsProjection(tableAlias: String = "", prefix: String = ""): String {
        val qualifier = if (tableAlias.isNotEmpty()) "$tableAlias." else ""
        return COLUMNS.joinToString(", ") { col ->
            if (prefix.isNotEmpty()) "$qualifier$col AS $prefix$col" else "$qualifier$col"
        }
    }

    const val SOURCE_TABLE_ALIAS = "s"
    const val SOURCE_PREFIX = "s_"
    const val SOD_TABLE_ALIAS = "sod"
    const val SOD_PREFIX = "sod_"

    /**
     * Builds the combined SELECT projection for a JOIN between `sourcedatamodel` and `sourceoperationaldata`.
     */
    fun getSourceWithOperationalDataProjection(
        sourceAlias: String = SOURCE_TABLE_ALIAS,
        sourcePrefix: String = SOURCE_PREFIX,
        sodAlias: String = SOD_TABLE_ALIAS,
        sodPrefix: String = SOD_PREFIX
    ): String {
        val sourceCols = getColumnsProjection(tableAlias = sourceAlias, prefix = sourcePrefix)
        val sodCols = SourceOperationalDataRepository.getColumnsProjection(tableAlias = sodAlias, prefix = sodPrefix)
        return "$sourceCols, $sodCols"
    }

    /**
     * Builds the FROM clause for a LEFT JOIN between `sourcedatamodel` and `sourceoperationaldata`.
     */
    fun getSourceWithOperationalDataFromClause(
        sourceAlias: String = SOURCE_TABLE_ALIAS,
        sodAlias: String = SOD_TABLE_ALIAS
    ): String {
        return "${getTableName()} AS $sourceAlias LEFT JOIN ${SourceOperationalDataRepository.getTableName()} AS $sodAlias ON $sourceAlias.id = $sodAlias.source_id"
    }

    /**
     * Reads the current cursor row and constructs a [Source] from it.
     * Assumes columns: id, enabled, url, title, favicon, source_type, age, auto_tag, language (optionally prefixed).
     */
    fun cursorToSource(cursor: Cursor, prefix: String = ""): Source {
        val idIdx = cursor.getColumnIndex(prefix + "id")
        val id = if (idIdx != -1 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else null
        val enabledIdx = cursor.getColumnIndex(prefix + "enabled")
        val enabledVal = if (enabledIdx != -1 && !cursor.isNull(enabledIdx)) cursor.getInt(enabledIdx) else 1
        val urlIdx = cursor.getColumnIndex(prefix + "url")
        val url = if (urlIdx != -1 && !cursor.isNull(urlIdx)) cursor.getString(urlIdx) ?: "" else ""
        val titleIdx = cursor.getColumnIndex(prefix + "title")
        val title = if (titleIdx != -1 && !cursor.isNull(titleIdx)) cursor.getString(titleIdx) ?: "" else ""
        val faviconIdx = cursor.getColumnIndex(prefix + "favicon")
        val favicon = if (faviconIdx != -1 && !cursor.isNull(faviconIdx)) cursor.getString(faviconIdx) ?: "" else ""
        val sourceTypeIdx = cursor.getColumnIndex(prefix + "source_type")
        val sourceType = if (sourceTypeIdx != -1 && !cursor.isNull(sourceTypeIdx)) cursor.getString(sourceTypeIdx) else null
        val ageIdx = cursor.getColumnIndex(prefix + "age")
        val age = if (ageIdx != -1 && !cursor.isNull(ageIdx)) cursor.getInt(ageIdx) else 0
        val autoTagIdx = cursor.getColumnIndex(prefix + "auto_tag")
        val autoTag = if (autoTagIdx != -1 && !cursor.isNull(autoTagIdx)) cursor.getString(autoTagIdx) ?: "" else ""
        val languageIdx = cursor.getColumnIndex(prefix + "language")
        val language = if (languageIdx != -1 && !cursor.isNull(languageIdx)) cursor.getString(languageIdx) else ""
        val fetchPeriodIdx = cursor.getColumnIndex(prefix + "fetch_period")
        val fetchPeriod = if (fetchPeriodIdx != -1 && !cursor.isNull(fetchPeriodIdx)) cursor.getLong(fetchPeriodIdx) else 3600L
        return Source(
            id = id,
            enabled = enabledVal == 1,
            url = url,
            title = title,
            favicon = favicon,
            source_type = sourceType,
            age = age,
            auto_tag = autoTag,
            fetch_period = fetchPeriod,
            language = language
        )
    }

    /**
     * Reads the current cursor row and constructs a [SourceWithOperationalData] from it.
     *
     * @param cursor SQLite cursor positioned at the target row.
     * @param sourcePrefix Column prefix used for source table columns (default: [SOURCE_PREFIX]).
     * @param sodPrefix Column prefix used for operational data table columns (default: [SOD_PREFIX]).
     * @return [SourceWithOperationalData] instance populated with cursor values.
     */
    fun cursorToSourceWithOperationalData(
        cursor: Cursor,
        sourcePrefix: String = SOURCE_PREFIX,
        sodPrefix: String = SOD_PREFIX
    ): SourceWithOperationalData {
        val source = cursorToSource(cursor, prefix = sourcePrefix)
        val sodIdIdx = cursor.getColumnIndex(sodPrefix + "id")
        val operationalData = if (sodIdIdx != -1 && !cursor.isNull(sodIdIdx)) {
            SourceOperationalDataRepository.cursorToOperationalData(cursor, prefix = sodPrefix)
        } else {
            null
        }
        return SourceWithOperationalData(source = source, operationalData = operationalData)
    }

    /**
     * Builds a parameterised WHERE clause from [searchQuery] by delegating to
     * [SourceSearchQueryTranslator]. Returns a pair of (clause string, list of bind args).
     */
    fun buildWhereClause(searchQuery: String): Pair<String, List<String>> {
        if (searchQuery.isBlank()) return "" to emptyList()

        return when (val parsed = SourceSearchQueryTranslator.parse(searchQuery)) {
            is ParsedQuery.FieldContains -> {
                val term = "%${parsed.term}%"
                when (parsed.field) {
                    "title" -> "s.title LIKE ?" to listOf(term)
                    "url" -> "s.url LIKE ?" to listOf(term)
                    "source_type" -> "s.source_type LIKE ?" to listOf(term)
                    "language" -> "s.language LIKE ?" to listOf(term)
                    "auto_tag" -> "s.auto_tag LIKE ?" to listOf(term)
                    "id" -> "s.id LIKE ?" to listOf(term)
                    else -> "" to emptyList()
                }
            }
            is ParsedQuery.FieldExact -> {
                when (parsed.field) {
                    "title" -> "s.title = ?" to listOf(parsed.term)
                    "url" -> "s.url = ?" to listOf(parsed.term)
                    "source_type" -> "s.source_type = ?" to listOf(parsed.term)
                    "language" -> "s.language = ?" to listOf(parsed.term)
                    "auto_tag" -> "s.auto_tag = ?" to listOf(parsed.term)
                    "id" -> "s.id = ?" to listOf(parsed.term)
                    else -> "" to emptyList()
                }
            }
            is ParsedQuery.FullText -> {
                val term = "%${parsed.term}%"
                "(s.title LIKE ? OR s.url LIKE ?)" to listOf(term, term)
            }
        }
    }

    /**
     * Executes a query joining `sourcedatamodel` and `sourceoperationaldata` with given WHERE clause, bind args, and ORDER BY clause.
     */
    private suspend fun querySourcesWithOperationalData(
        context: Context,
        activeDatabaseState: DatabaseState?,
        whereClause: String = "",
        whereArgs: Array<String>? = null,
        orderBy: String = ""
    ): List<SourceWithOperationalData> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SourceWithOperationalData>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext results
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext results

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            SourceOperationalDataRepository.ensureTableExists(db)
            val projection = getSourceWithOperationalDataProjection()
            val fromClause = getSourceWithOperationalDataFromClause()
            val whereSql = if (whereClause.isNotBlank()) " WHERE $whereClause" else ""
            val orderSql = if (orderBy.isNotBlank()) " ORDER BY $orderBy" else ""
            val sqlText = "SELECT $projection FROM $fromClause$whereSql$orderSql"
            val cursor = db.rawQuery(sqlText, whereArgs)
            cursor.use { c ->
                while (c.moveToNext()) {
                    results.add(cursorToSourceWithOperationalData(c))
                }
            }
            db.close()
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Exception in $functionName: ${e.message}")
            e.printStackTrace()
        }

        results
    }

    /**
     * Retrieves all sources from `sourcedatamodel` joined with their operational metadata
     * from `sourceoperationaldata` via an outer join (LEFT JOIN), optionally filtered by [searchQuery]
     * and ordered by [orderBy] in SQL.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param orderBy Sort order applied to the query ([SourceOrder.ByUrl], [SourceOrder.ByTitle], [SourceOrder.ByFetchTime], or [SourceOrder.ByConsecutiveErrors]).
     * @param searchQuery Optional search query string used to filter sources directly in SQL.
     * @return List of [SourceWithOperationalData] containing each source and its operational data (if present).
     */
    suspend fun getAllSourcesWithOperationalData(
        context: Context,
        activeDatabaseState: DatabaseState?,
        orderBy: SourceOrder = SourceOrder.ByUrl,
        searchQuery: String = ""
    ): List<SourceWithOperationalData> {
        val orderByClause = when (orderBy) {
            SourceOrder.ByUrl -> "s.url ASC, s.title ASC"
            SourceOrder.ByTitle -> "s.title ASC, s.url ASC"
            SourceOrder.ByFetchTime -> "sod.date_fetched ASC, s.url ASC"
            SourceOrder.ByConsecutiveErrors -> "sod.consecutive_errors DESC, s.url ASC"
        }
        val (whereClause, args) = buildWhereClause(searchQuery)
        val argsArray = if (args.isNotEmpty()) args.toTypedArray() else null
        return querySourcesWithOperationalData(
            context = context,
            activeDatabaseState = activeDatabaseState,
            whereClause = whereClause,
            whereArgs = argsArray,
            orderBy = orderByClause
        )
    }

    /**
     * Finds a source with operational data in `sourcedatamodel` matching [sourceId].
     */
    suspend fun getSourceWithOperationalDataById(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceId: Long
    ): SourceWithOperationalData? {
        return querySourcesWithOperationalData(
            context = context,
            activeDatabaseState = activeDatabaseState,
            whereClause = "s.id = ?",
            whereArgs = arrayOf(sourceId.toString()),
            orderBy = ""
        ).firstOrNull()
    }

    /**
     * Finds a source with operational data in `sourcedatamodel` matching [sourceUrl].
     */
    suspend fun getSourceWithOperationalDataByUrl(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sourceUrl: String
    ): SourceWithOperationalData? {
        if (sourceUrl.isBlank()) return null
        return querySourcesWithOperationalData(
            context = context,
            activeDatabaseState = activeDatabaseState,
            whereClause = "s.url = ?",
            whereArgs = arrayOf(sourceUrl),
            orderBy = ""
        ).firstOrNull()
    }

    /**
     * Executes a single-source query with the specified [whereClause] and [whereArgs].
     */
    private suspend fun getSourceWhere(
        context: Context,
        activeDatabaseState: DatabaseState?,
        whereClause: String,
        whereArgs: Array<String>,
        logIdentifier: String
    ): Source? = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) return@withContext null
        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val sqlText = "SELECT ${getColumnsProjection()} FROM ${getTableName()} WHERE $whereClause LIMIT 1"
                val cursor = it.rawQuery(sqlText, whereArgs)
                cursor.use { c ->
                    if (c.moveToFirst()) cursorToSource(c) else null
                }
            }
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "$logIdentifier Exception when getting source properties in $functionName")

            e.printStackTrace()
            null
        }
    }

    /**
     * Finds a source in `sourcedatamodel` matching [sourceId].
     */
    suspend fun getSourceById(context: Context, activeDatabaseState: DatabaseState?, sourceId: Long): Source? {
        return getSourceWhere(
            context = context,
            activeDatabaseState = activeDatabaseState,
            whereClause = "id = ?",
            whereArgs = arrayOf(sourceId.toString()),
            logIdentifier = "Source ID: $sourceId"
        )
    }

    /**
     * Finds a source in `sourcedatamodel` matching [sourceUrl].
     */
    suspend fun getSourceByUrl(context: Context, activeDatabaseState: DatabaseState?, sourceUrl: String): Source? {
        if (sourceUrl.isBlank()) return null
        return getSourceWhere(
            context = context,
            activeDatabaseState = activeDatabaseState,
            whereClause = "url = ?",
            whereArgs = arrayOf(sourceUrl),
            logIdentifier = "Url: $sourceUrl"
        )
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
     * Checks if there is at least one enabled source that is outdated (never fetched or older than
     * its [Source.fetch_period], falling back to the global [AppConfiguration.outdatedFetchThresholdSeconds]).
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @return true if there are outdated enabled sources to refresh.
     */
    suspend fun hasOutdatedSources(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Boolean {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return false
        }
        val config = AppConfigManager.config.value
        if (config.networkConfig.disabled) {
            return false
        }

        val sources = querySourcesWithOperationalData(
            context = context,
            activeDatabaseState = activeDatabaseState,
            whereClause = "s.enabled = 1 AND s.url != ''"
        )

        return sources.any { item ->
            SourceOperationalDataRepository.isFetchOutdated(
                fetchTime = item.operationalData?.date_fetched,
                fetchPeriodSeconds = item.source.fetch_period
            )
        }
    }

    /**
     * Populates a list of [Source] records into the specified [SQLiteDatabase] (`sourcedatamodel`).
     * If an insertion fails, logs an error to [AppLoggingRepository], rolls back the transaction, and throws the exception.
     *
     * @param db SQLiteDatabase instance to populate.
     * @param sources List of [Source] records to insert.
     * @return The number of rows successfully inserted.
     * @throws Exception If an insertion fails.
     */
    fun populateSources(db: SQLiteDatabase, sources: List<Source>): Int {
        if (sources.isEmpty()) return 0

        val insertSql = """
            INSERT INTO ${getTableName()} (
                id, title, url, enabled, source_type, category_name, subcategory_name,
                export_to_cms, remove_after_days, language, age, favicon, fetch_period,
                auto_tag, entries_backgroundcolor_alpha, entries_backgroundcolor,
                entries_alpha, proxy_location, auto_update_favicon, category_id,
                subcategory_id, xpath
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val stmt = db.compileStatement(insertSql)
        var insertedCount = 0
        var insertError: Exception? = null
        var failedSourceIdentifier: String? = null

        db.beginTransaction()
        try {
            for (source in sources) {
                try {
                    stmt.clearBindings()
                    if (source.id != null) stmt.bindLong(1, source.id) else stmt.bindNull(1)
                    stmt.bindString(2, source.title)
                    stmt.bindString(3, source.url)
                    stmt.bindLong(4, if (source.enabled) 1L else 0L)
                    if (source.source_type != null) stmt.bindString(5, source.source_type) else stmt.bindString(5, "")
                    stmt.bindString(6, "")
                    stmt.bindString(7, "")
                    stmt.bindLong(8, 0L)
                    stmt.bindLong(9, 0L)
                    stmt.bindString(10, source.language)
                    stmt.bindLong(11, if ((source.age ?: 0) >= 0) (source.age ?: 0).toLong() else 0L)
                    stmt.bindString(12, source.favicon)
                    stmt.bindLong(13, if (source.fetch_period > 0) source.fetch_period else 3600L)
                    stmt.bindString(14, source.auto_tag.take(1000))
                    stmt.bindDouble(15, 1.0)
                    stmt.bindString(16, "")
                    stmt.bindDouble(17, 1.0)
                    stmt.bindString(18, "")
                    stmt.bindLong(19, 0L)
                    stmt.bindLong(20, 0L)
                    stmt.bindLong(21, 0L)
                    stmt.bindString(22, "")

                    val rowId = stmt.executeInsert()
                    if (rowId >= 0) insertedCount++
                } catch (e: Exception) {
                    insertError = e
                    failedSourceIdentifier = source.url.ifBlank { null } ?: source.title.ifBlank { null } ?: "ID: ${source.id}"
                    break
                }
            }
            if (insertError == null) {
                db.setTransactionSuccessful()
            }
        } finally {
            db.endTransaction()
            stmt.close()
        }

        if (insertError != null) {
            AppLoggingRepository.insertLogDirect(
                db = db,
                infoText = "Failed to insert source: $failedSourceIdentifier",
                detailText = insertError.stackTraceToString(),
                level = AppLoggingRepository.LEVEL_ERROR
            )
            throw insertError
        }

        return insertedCount
    }

    /**
     * Populates a list of [Source] records into the SQLite database file.
     *
     * @param dbFile SQLite database file.
     * @param sources List of [Source] records to insert.
     * @return The number of rows successfully inserted.
     * @throws Exception If an insertion fails.
     */
    fun populateSources(dbFile: File, sources: List<Source>): Int {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        return try {
            populateSources(db, sources)
        } finally {
            db.close()
        }
    }

    /**
     * Populates a list of [Source] records into the database referenced by [activeDatabaseState].
     *
     * @param context Application context.
     * @param activeDatabaseState Current active database state.
     * @param sources List of [Source] records to insert.
     * @return Pair of Boolean (success) and Int (count of inserted sources).
     */
    suspend fun populateSources(
        context: Context,
        activeDatabaseState: DatabaseState?,
        sources: List<Source>
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, 0)
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, 0)

        try {
            val inserted = populateSources(file, sources)
            Pair(true, inserted)
        } catch (e: Exception) {
            Pair(false, 0)
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
        fetch_period: Long = 3600L,
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
                put("fetch_period", if (fetch_period > 0) fetch_period else 3600L)
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
        fetch_period: Long? = null,
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
                if (fetch_period != null) {
                    put("fetch_period", if (fetch_period > 0) fetch_period else 3600L)
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
     * Updates the language setting for a source in `sourcedatamodel`.
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param id ID of the source.
     * @param language Language code string (e.g. "en", "pl").
     * @return Pair(true, null) on success, Pair(false, errorMessage) on failure.
     */
    suspend fun updateSourceLanguage(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long,
        language: String
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = ContentValues().apply {
                put("language", language.take(1000))
            }
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; source may not exist")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Source ID: $id Exception when updating source language in $functionName")

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
        updateFetchData(context, activeDatabaseState, urlObj, source)
    }

    /**
     * Updates operational fetch data for [source] from [urlObj].
     * If the response is invalid ([PageResponseObject.isInvalid]), increments consecutive_errors.
     * If the response is valid ([PageResponseObject.isValid]), resets consecutive_errors to 0.
     * When [urlObj] points to a valid [RssPage], also records entry count, page hash, and body hash.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param urlObj The [Url] object representing the fetched source.
     * @param source The [Source] database object being updated.
     * @return Pair(success, resultMessage).
     */
    suspend fun updateFetchData(
        context: Context,
        activeDatabaseState: DatabaseState?,
        urlObj: Url,
        source: Source
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val response = urlObj.getCachedResponse() ?: urlObj.getResponse()
        val sourceId = source.id?.takeIf { it != 0L }
        val targetUrl = if (source.url.isNotBlank()) source.url else urlObj.url

        if (response.isInvalid) {
            if (sourceId != null) {
                SourceOperationalDataRepository.setSourceFetch(
                    context = context,
                    activeDatabaseState = activeDatabaseState,
                    sourceObjId = sourceId,
                    isError = true
                )
            } else {
                SourceOperationalDataRepository.setSourceFetchByUrl(
                    context = context,
                    activeDatabaseState = activeDatabaseState,
                    sourceUrl = targetUrl,
                    isError = true
                )
            }
            return@withContext Pair(false, "Failed to fetch source: ${response.error ?: "HTTP status ${response.statusCode}"}")
        }

        val page = urlObj.getPage()
        if (page !is RssPage) {
            if (response.isValid) {
                if (sourceId != null) {
                    SourceOperationalDataRepository.setSourceFetch(
                        context = context,
                        activeDatabaseState = activeDatabaseState,
                        sourceObjId = sourceId,
                        isError = false
                    )
                } else {
                    SourceOperationalDataRepository.setSourceFetchByUrl(
                        context = context,
                        activeDatabaseState = activeDatabaseState,
                        sourceUrl = targetUrl,
                        isError = false
                    )
                }
            }
            return@withContext Pair(false, "URL does not point to a valid RSS or Atom feed")
        }

        val entries = page.getEntries()

        if (sourceId != null) {
            SourceOperationalDataRepository.setSourceFetch(
                context = context,
                activeDatabaseState = activeDatabaseState,
                sourceObjId = sourceId,
                numberOfEntries = entries.size,
                pageHash = page.getHash(),
                bodyHash = page.getBodyHash(),
                isError = false
            )
        } else {
            SourceOperationalDataRepository.setSourceFetchByUrl(
                context = context,
                activeDatabaseState = activeDatabaseState,
                sourceUrl = targetUrl,
                numberOfEntries = entries.size,
                pageHash = page.getHash(),
                bodyHash = page.getBodyHash(),
                isError = false
            )
        }
        Pair(true, "Successfully marked fetch")
    }

    /**
     * Checks whether a fetch is required for [source].
     * A fetch is required if the source is enabled, has a non-blank URL, and its
     * [SourceOperationalData.date_fetched] is outdated.
     * Uses [Source.fetch_period] as the per-source threshold when positive,
     * otherwise falls back to the global [AppConfiguration.outdatedFetchThresholdSeconds].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param source The [Source] to check.
     * @return true if fetch is required, false otherwise.
     */
    suspend fun isFetchRequired(
        context: Context,
        activeDatabaseState: DatabaseState?,
        source: Source
    ): Boolean = withContext(Dispatchers.IO) {
        if (!source.enabled || source.url.isBlank()) {
            return@withContext false
        }
        val sourceId = source.id ?: return@withContext true
        val data = SourceOperationalDataRepository.getOperationalDataBySourceId(
            context,
            activeDatabaseState,
            sourceId
        )
        SourceOperationalDataRepository.isFetchOutdated(
            data?.date_fetched,
            fetchPeriodSeconds = source.fetch_period
        )
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

        if (!isFetchRequired(context, activeDatabaseState, source)) {
            return@withContext Pair(false, "Source was fetched recently (less than 1 hour ago)")
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

