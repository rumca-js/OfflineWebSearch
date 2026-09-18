package io.github.rumcajs.offlinewebsearch.data.builders

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.ASSET_EMPTY_TABLE
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus
import io.github.rumcajs.offlinewebsearch.data.repositories.ConfigurationEntry
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.SearchViewRepository
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Base abstract class providing shared implementation for database builders.
 *
 * Implements common file operations, SQLite table population from JSON entries,
 * metadata extraction (configuration & search view), and status reporting.
 */
abstract class AbstractDatabaseBuilder(
    protected val context: Context,
    override val url: String,
    protected val oldUrl: String? = null,
    protected val customFileName: String? = null
) : DatabaseBuilder {

    protected val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    protected var _currentStatus: DatabaseStatus = DatabaseStatus.INIT
    override val currentStatus: DatabaseStatus get() = _currentStatus

    protected val targetLocalFileName: String by lazy {
        if (!customFileName.isNullOrBlank()) {
            if (customFileName.endsWith(".db", ignoreCase = true)) customFileName else "$customFileName.db"
        } else {
            DatabaseState.fromUrl(url).localFileName
        }
    }

    protected fun updateStatus(
        status: DatabaseStatus,
        progress: Float? = null,
        errorMessage: String? = null
    ) {
        _currentStatus = status
        AppConfigManager.updateDatabaseStatus(url, status, errorMessage, progress)
    }

    override suspend fun onInit() {
        updateStatus(DatabaseStatus.INIT, 0.0f)
        if (oldUrl == null) {
            AppConfigManager.addDatabase(url)
        } else if (oldUrl != url) {
            AppConfigManager.updateDatabase(oldUrl, url)
        }
    }

    override suspend fun onDownloading() {
        // Default no-op for local/asset sources
    }

    override suspend fun onUnpacking() {
        // Default no-op if no archive unpacking is required
    }

    override suspend fun onPopulatingTable() {
        // Default no-op if source is already an SQLite database
    }

    override suspend fun onReady(): DatabaseState = withContext(Dispatchers.IO) {
        val now = DateUtils.getCurrentIsoTimestamp()
        val finalFile = File(context.filesDir, targetLocalFileName)
        val finalSize = if (finalFile.exists()) finalFile.length() else 0L

        val readyState = DatabaseState(
            url = url,
            localFileName = targetLocalFileName,
            status = DatabaseStatus.READY,
            progress = 1.0f,
            errorMessage = null,
            sizeInBytes = finalSize,
            isReadOnly = false,
            dateCreated = now,
            dateLastRefresh = now
        )

        val configEntry = if (finalFile.exists()) ConfigurationEntry.readFromDatabase(finalFile) else null
        val searchViewEntry = if (finalFile.exists()) SearchViewRepository.readDefaultFromDatabase(finalFile) else null

        AppConfigManager.updateConfig { config ->
            if (oldUrl != null && oldUrl != url) {
                val oldState = DatabaseState.fromUrl(oldUrl)
                AppConfigManager.removeDatabaseFiles(context, oldState.localFileName)
            }

            val newDatabases = config.databases.toMutableMap().apply {
                val existing = if (oldUrl != null) remove(oldUrl) else get(url)
                put(url, readyState.copy(
                    displayNameField = existing?.displayNameField ?: "",
                    dateCreated = existing?.dateCreated ?: now
                ))
            }

            val newDbConfigs = config.dbConfigs.toMutableMap().apply {
                val existingConfig = if (oldUrl != null) remove(oldUrl) else get(url)
                var updatedConfig = existingConfig ?: config.defaultDbConfig
                if (configEntry?.showIcons != null) {
                    updatedConfig = updatedConfig.copy(showIcons = configEntry.showIcons)
                }
                if (configEntry?.viewStyle != null) {
                    updatedConfig = updatedConfig.copy(viewStyle = configEntry.viewStyle!!)
                }
                if (configEntry?.linksPerPage != null) {
                    val links = kotlin.math.max(DatabaseConfiguration.MIN_LINKS_PER_PAGE, configEntry.linksPerPage)
                    updatedConfig = updatedConfig.copy(linksPerPage = links)
                }
                if (configEntry?.trackUserSearches != null) {
                    updatedConfig = updatedConfig.copy(trackUserSearches = configEntry.trackUserSearches)
                }
                if (configEntry?.trackUserNavigation != null) {
                    updatedConfig = updatedConfig.copy(trackUserNavigation = configEntry.trackUserNavigation)
                }
                if (configEntry?.entriesVisitAlpha != null) {
                    updatedConfig = updatedConfig.copy(entriesVisitAlpha = configEntry.entriesVisitAlpha)
                }
                if (configEntry?.entriesDeadAlpha != null) {
                    updatedConfig = updatedConfig.copy(entriesDeadAlpha = configEntry.entriesDeadAlpha)
                }
                if (searchViewEntry?.orderBy != null) {
                    updatedConfig = updatedConfig.copy(orderBy = searchViewEntry.orderBy!!)
                }
                put(url, updatedConfig)
            }

            config.copy(
                databases = newDatabases,
                dbConfigs = newDbConfigs,
                activeDatabaseUrl = if (config.activeDatabaseUrl == oldUrl) url else config.activeDatabaseUrl
            )
        }

        updateStatus(DatabaseStatus.READY, 1.0f)
        readyState
    }

    override suspend fun onFailed(error: Throwable): DatabaseState = withContext(Dispatchers.IO) {
        val errorMessage = error.message ?: error.javaClass.simpleName
        updateStatus(DatabaseStatus.FAILED, errorMessage = errorMessage)
        val currentState = AppConfigManager.config.value.databases[url]
            ?: DatabaseState(url = url, localFileName = targetLocalFileName)
        currentState.copy(
            status = DatabaseStatus.FAILED,
            errorMessage = errorMessage
        )
    }

    override suspend fun build(): DatabaseState = withContext(Dispatchers.IO) {
        try {
            onInit()
            onDownloading()
            onUnpacking()
            onPopulatingTable()
            onReady()
        } catch (e: Exception) {
            onFailed(e)
            throw e
        }
    }

    /**
     * Copies the SQLite schema template database (`assets/table.db`) to the specified destination file.
     */
    protected fun copyAssetTableDb(destFile: File, assetName: String = ASSET_EMPTY_TABLE) {
        destFile.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Populates a list of [Entry] records into the SQLite database file (`linkdatamodel`, `entrycompactedtags`, `socialdata`).
     */
    protected fun populateEntriesToDatabase(entries: List<Entry>, dbFile: File) {
        if (entries.isEmpty()) return

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        val insertSql = """
            INSERT INTO linkdatamodel (
                id, link, title, description, author, album, language,
                page_rating_votes, page_rating_visits, page_rating, thumbnail,
                date_created, date_published, date_dead_since, age,
                status_code, manual_status_code, bookmarked, source_id, source_url,
                permanent, contents_type, page_rating_contents
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val insertTagSql = "INSERT INTO entrycompactedtags (entry_id, tag) VALUES (?, ?)"
        val insertSocialSql = """
            INSERT INTO socialdata (
                entry_id, thumbs_up, thumbs_down, view_count, rating,
                upvote_ratio, upvote_diff, upvote_view_ratio, stars, followers_count, date_updated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val stmt = db.compileStatement(insertSql)
        val tagStmt = db.compileStatement(insertTagSql)
        val socialStmt = db.compileStatement(insertSocialSql)

        db.beginTransaction()
        try {
            for (entry in entries) {
                stmt.clearBindings()
                if (entry.id != null) stmt.bindLong(1, entry.id) else stmt.bindNull(1)
                stmt.bindString(2, entry.link ?: "")
                if (entry.title != null) stmt.bindString(3, entry.title) else stmt.bindNull(3)
                if (entry.description != null) stmt.bindString(4, entry.description) else stmt.bindNull(4)
                if (entry.author != null) stmt.bindString(5, entry.author) else stmt.bindNull(5)
                if (entry.album != null) stmt.bindString(6, entry.album) else stmt.bindNull(6)
                if (entry.language != null) stmt.bindString(7, entry.language) else stmt.bindNull(7)
                stmt.bindLong(8, (entry.page_rating_votes ?: 0).toLong())
                stmt.bindLong(9, (entry.page_rating_visits ?: 0).toLong())
                stmt.bindLong(10, (entry.page_rating ?: 0).toLong())
                if (entry.thumbnail != null) stmt.bindString(11, entry.thumbnail) else stmt.bindNull(11)
                if (entry.date_created != null) stmt.bindString(12, entry.date_created) else stmt.bindNull(12)
                if (entry.date_published != null) stmt.bindString(13, entry.date_published) else stmt.bindNull(13)
                if (entry.date_dead_since != null) stmt.bindString(14, entry.date_dead_since) else stmt.bindNull(14)
                stmt.bindLong(15, (entry.age ?: 0).toLong())
                stmt.bindLong(16, (entry.status_code ?: 0).toLong())
                stmt.bindLong(17, (entry.manual_status_code ?: 0).toLong())
                stmt.bindLong(18, if (entry.bookmarked == true) 1L else 0L)
                if (entry.source_id != null) stmt.bindLong(19, entry.source_id) else stmt.bindNull(19)
                stmt.bindString(20, entry.source_url ?: "")
                stmt.bindLong(21, 0L)
                stmt.bindLong(22, 0L)
                stmt.bindLong(23, 0L)

                val rowId = stmt.executeInsert()
                val entryId = entry.id ?: rowId

                if (!entry.tags.isNullOrEmpty()) {
                    for (tag in entry.tags) {
                        tagStmt.clearBindings()
                        tagStmt.bindLong(1, entryId)
                        tagStmt.bindString(2, tag)
                        tagStmt.executeInsert()
                    }
                }

                if (entry.socialData != null) {
                    socialStmt.clearBindings()
                    socialStmt.bindLong(1, entryId)
                    if (entry.socialData.thumbsUp != null) socialStmt.bindLong(2, entry.socialData.thumbsUp.toLong()) else socialStmt.bindNull(2)
                    if (entry.socialData.thumbsDown != null) socialStmt.bindLong(3, entry.socialData.thumbsDown.toLong()) else socialStmt.bindNull(3)
                    if (entry.socialData.viewCount != null) socialStmt.bindLong(4, entry.socialData.viewCount.toLong()) else socialStmt.bindNull(4)
                    if (entry.socialData.rating != null) socialStmt.bindLong(5, entry.socialData.rating.toLong()) else socialStmt.bindNull(5)
                    if (entry.socialData.upvoteRatio != null) socialStmt.bindLong(6, entry.socialData.upvoteRatio.toLong()) else socialStmt.bindNull(6)
                    if (entry.socialData.upvoteDiff != null) socialStmt.bindLong(7, entry.socialData.upvoteDiff.toLong()) else socialStmt.bindNull(7)
                    if (entry.socialData.upvoteViewRatio != null) socialStmt.bindLong(8, entry.socialData.upvoteViewRatio.toLong()) else socialStmt.bindNull(8)
                    if (entry.socialData.stars != null) socialStmt.bindLong(9, entry.socialData.stars.toLong()) else socialStmt.bindNull(9)
                    if (entry.socialData.followersCount != null) socialStmt.bindLong(10, entry.socialData.followersCount.toLong()) else socialStmt.bindNull(10)
                    if (entry.socialData.dateUpdated != null) socialStmt.bindString(11, entry.socialData.dateUpdated) else socialStmt.bindNull(11)
                    socialStmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            stmt.close()
            tagStmt.close()
            socialStmt.close()
            db.close()
        }
    }

    /**
     * Extracts a `.db` file from a ZIP archive to [outputFile].
     */
    @Throws(IOException::class, NoSuchElementException::class)
    protected fun unzipDatabaseToFile(zipFile: File, outputFile: File) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.endsWith(".db", ignoreCase = true)) {
                    zip.getInputStream(entry).use { inputStream ->
                        outputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        return
                    }
                }
            }
            throw NoSuchElementException("ZIP archive parsed successfully, but no file ending in '.db' was found inside.")
        }
    }

    /**
     * Extracts all `.json` files from a ZIP archive, parses entries, and inserts them into [dbFile].
     */
    protected fun unzipAndPopulateJsonToDb(zipFile: File, dbFile: File) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    zip.getInputStream(entry).bufferedReader().use { reader ->
                        val jsonText = reader.readText()
                        val parsedEntries: List<Entry> = json.decodeFromString(jsonText)
                        populateEntriesToDatabase(parsedEntries, dbFile)
                    }
                }
            }
        }
    }
}
