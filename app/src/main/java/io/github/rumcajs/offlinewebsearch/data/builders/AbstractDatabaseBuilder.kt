package io.github.rumcajs.offlinewebsearch.data.builders

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.ASSET_EMPTY_TABLE
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus
import io.github.rumcajs.offlinewebsearch.data.repositories.ConfigurationEntry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SearchViewRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     * Populates a list of [Entry] records into the SQLite database file (`linkdatamodel`, `entrycompactedtags`, `socialdata`)
     * via [EntrySqliteRepository.populateEntries].
     *
     * @return The number of rows successfully inserted into `linkdatamodel`.
     */
    protected fun populateEntriesToDatabase(entries: List<Entry>, dbFile: File): Int {
        return EntrySqliteRepository.populateEntries(dbFile, entries)
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
}
