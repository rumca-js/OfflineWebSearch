package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.net.Uri
import io.github.rumcajs.offlinewebsearch.data.repositories.ConfigurationEntry
import io.github.rumcajs.offlinewebsearch.data.repositories.SearchViewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import io.github.rumcajs.offlinewebsearch.util.DateUtils


/**
 * Singleton to manage app configuration.
 * Can be updated from various sources.
 */
object AppConfigManager {
    private const val APP_CONFIG_FILE_NAME = "app_config.json"
    private const val NETWORK_CONFIG_FILE_NAME = "network_config.json"
    private var appContext: Context? = null

    // Reuse a single Scope for background disk I/O tasks
    private val configScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Reuse a single Json instance to allow internal serialization caching
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _config = MutableStateFlow(AppConfiguration())
    val config: StateFlow<AppConfiguration> = _config.asStateFlow()

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext

        // Load configurations sequentially on the background thread to avoid state races
        configScope.launch {
            loadPersistedConfigSync(applicationContext)
            loadNetworkConfigSync(applicationContext)
        }
    }

    fun updateConfig(update: (AppConfiguration) -> AppConfiguration) {
        _config.update(update)
        saveConfigAsync() // Offloaded to background thread
    }

    suspend fun reloadConfig(context: Context) = withContext(Dispatchers.IO) {
        loadPersistedConfigSync(context.applicationContext)
        loadNetworkConfigSync(context.applicationContext)
    }

    private fun loadPersistedConfigSync(context: Context) {
        try {
            val file = context.getFileStreamPath(APP_CONFIG_FILE_NAME)
            if (file != null && file.exists()) {
                context.openFileInput(APP_CONFIG_FILE_NAME).bufferedReader().use { reader ->
                    val jsonString = reader.readText()
                    val persistedConfig = json.decodeFromString<AppConfiguration>(jsonString)
                    _config.update { currentConfig ->
                        persistedConfig.copy(networkConfig = currentConfig.networkConfig)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadNetworkConfigSync(context: Context) {
        try {
            context.assets.open(NETWORK_CONFIG_FILE_NAME).bufferedReader().use { reader ->
                val jsonString = reader.readText()
                val networkConfig = json.decodeFromString<NetworkConfig>(jsonString)
                _config.update {
                    it.copy(networkConfig = networkConfig)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveConfigAsync() {
        val context = appContext ?: return
        val currentConfig = config.value
        configScope.launch {
            try {
                val jsonString = json.encodeToString(currentConfig)
                context.openFileOutput(APP_CONFIG_FILE_NAME, Context.MODE_PRIVATE).use { output ->
                    output.write(jsonString.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDatabaseConfig(url: String?, update: (DatabaseConfiguration) -> DatabaseConfiguration) {
        updateConfig { currentConfig ->
            if (url != null) {
                val currentDbConfig = currentConfig.dbConfigs[url] ?: DatabaseConfiguration()
                val newDbConfig = update(currentDbConfig)
                currentConfig.copy(dbConfigs = currentConfig.dbConfigs + (url to newDbConfig))
            } else {
                val newDefault = update(currentConfig.defaultDbConfig)
                currentConfig.copy(defaultDbConfig = newDefault)
            }
        }
    }

    fun setDirectLinks(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(directLinks = enabled) }
        }
    }

    fun setShowIcons(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(showIcons = enabled) }
        }
    }

    fun setTrackUserSearches(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(trackUserSearches = enabled) }
        }
    }

    fun setTrackUserNavigation(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(trackUserNavigation = enabled) }
        }
    }

    fun setLinksPerPage(count: Int) {
        val validCount = kotlin.math.max(DatabaseConfiguration.MIN_LINKS_PER_PAGE, count)
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(linksPerPage = validCount) }
        }
    }

    fun setVideoPreview(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(videoPreview = enabled) }
        }
    }

    fun setOrderBy(orderBy: OrderBy) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(orderBy = orderBy) }
        }
    }

    fun setViewStyle(viewStyle: ViewStyle) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(viewStyle = viewStyle) }
        }
    }

    fun setUserAge(age: Int) {
        updateConfig { it.copy(userAge = age) }
    }

    fun setNetworkDisabled(disabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.copy(
                networkConfig = currentConfig.networkConfig.copy(disabled = disabled)
            )
        }
    }

    fun addDatabase(url: String) {
        updateConfig {
            it.copy(databases = it.databases + (url to DatabaseState.fromUrl(url)))
        }
    }

    fun removeDatabase(url: String) {
        updateConfig {
            val newDatabases = it.databases - url
            val newDbConfigs = it.dbConfigs - url
            it.copy(
                databases = newDatabases,
                dbConfigs = newDbConfigs,
                activeDatabase = if (it.activeDatabase == url) null else it.activeDatabase
            )
        }
    }

    /**
     * Removes database entry from config and deletes its files from local storage.
     */
    fun removeDatabaseAndFiles(context: Context, url: String, localFileName: String? = null) {
        val fileName = localFileName ?: config.value.databases[url]?.localFileName
        removeDatabase(url)
        if (!fileName.isNullOrBlank()) {
            removeDatabaseFiles(context, fileName)
        }
    }

    fun updateDatabase(oldUrl: String, newUrl: String) {
        updateConfig { config ->
            val newDatabases = config.databases.toMutableMap().apply {
                remove(oldUrl)?.let { state ->
                    // Corrected: Update the copy's internal url property too!
                    put(newUrl, state.copy(url = newUrl, localFileName = DatabaseState.fromUrl(newUrl).localFileName))
                }
            }

            val newDbConfigs = config.dbConfigs.toMutableMap().apply {
                remove(oldUrl)?.let { dbConfig ->
                    put(newUrl, dbConfig)
                }
            }

            config.copy(
                databases = newDatabases,
                dbConfigs = newDbConfigs,
                activeDatabase = if (config.activeDatabase == oldUrl) newUrl else config.activeDatabase
            )
        }
    }

    /**
     * Removes the local database file and any associated SQLite sidecar files (-wal, -shm, -journal).
     */
    fun removeDatabaseFiles(context: Context, localFileName: String) {
        val baseFile = File(context.filesDir, localFileName)
        baseFile.delete()
        File(context.filesDir, "$localFileName-wal").delete()
        File(context.filesDir, "$localFileName-shm").delete()
        File(context.filesDir, "$localFileName-journal").delete()
    }

    /**
     * Safely saves database content (either from a local byte array or a remote download)
     * and updates the AppConfiguration maps.
     */
    suspend fun saveDatabaseSource(
        context: Context,
        url: String,
        content: ByteArray,
        oldUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = DateUtils.getCurrentIsoTimestamp()
        val newState = DatabaseState.fromUrl(url).copy(
            status = DatabaseStatus.READY,
            progress = 1.0f,
            dateCreated = now,
            dateLastRefresh = now
        )

        try {
            // Remove old/existing files and sidecars for this local database
            removeDatabaseFiles(context, newState.localFileName)

            // 1. Write the new file
            context.openFileOutput(newState.localFileName, Context.MODE_PRIVATE).use { output ->
                output.write(content)
            }

            // 2. Inspect database for configurationentry & searchview tables using domain models
            val dbFile = if (newState.extension == ".db") File(context.filesDir, newState.localFileName) else null
            val configEntry = if (dbFile != null) ConfigurationEntry.readFromDatabase(dbFile) else null
            val searchViewEntry = if (dbFile != null) SearchViewRepository.readDefaultFromDatabase(dbFile) else null

            // 3. Perform old file cleanup and configuration state transition
            updateConfig { config ->
                // Clean up old file if the URL actually changed
                if (oldUrl != null && oldUrl != url) {
                    val oldState = DatabaseState.fromUrl(oldUrl)
                    removeDatabaseFiles(context, oldState.localFileName)
                }

                // Prepare updated maps
                val newDatabases = config.databases.toMutableMap().apply {
                    if (oldUrl != null) {
                        remove(oldUrl)?.let { state ->
                            put(url, state.copy(
                                url = url,
                                localFileName = newState.localFileName,
                                status = DatabaseStatus.READY,
                                progress = 1.0f,
                                errorMessage = null,
                                dateCreated = state.dateCreated ?: now,
                                dateLastRefresh = now
                            ))
                        }
                    } else {
                        put(url, newState)
                    }
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
                    if (searchViewEntry?.orderBy != null) {
                        updatedConfig = updatedConfig.copy(orderBy = searchViewEntry.orderBy!!)
                    }
                    put(url, updatedConfig)
                }

                config.copy(
                    databases = newDatabases,
                    dbConfigs = newDbConfigs,
                    activeDatabase = if (config.activeDatabase == oldUrl) url else config.activeDatabase
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    fun updateDatabaseStatus(
        url: String,
        status: DatabaseStatus,
        errorMessage: String? = null,
        progress: Float? = null
    ) {
        updateConfig { config ->
            val newDatabases = config.databases.toMutableMap().apply {
                get(url)?.let { state ->
                    val calculatedProgress = progress ?: when (status) {
                        DatabaseStatus.READY -> 1.0f
                        DatabaseStatus.UNPACKING -> 0.75f
                        DatabaseStatus.DOWNLOADING -> 0.25f
                        DatabaseStatus.INIT -> 0.0f
                        DatabaseStatus.FAILED -> state.progress
                    }
                    put(
                        url,
                        state.copy(
                            status = status,
                            errorMessage = errorMessage,
                            progress = calculatedProgress
                        )
                    )
                }
            }
            config.copy(databases = newDatabases)
        }
    }

    /**
     * Reads database bytes from a local URI and saves them as a database source.
     */
    suspend fun saveDatabaseLocal(
        context: Context,
        url: String,
        uri: Uri,
        oldUrl: String? = null
    ) {
        if (oldUrl == null) {
            addDatabase(url)
        } else {
            updateDatabase(oldUrl, url)
        }

        try {
            val content = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.readBytes()
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: throw IOException("Failed to read file")

            if (url.endsWith(".db.zip", ignoreCase = true) || url.endsWith(".zip", ignoreCase = true)) {
                updateDatabaseStatus(url, DatabaseStatus.UNPACKING)
                val tempZipFile = File.createTempFile("local_temp_db", ".zip", context.cacheDir)
                val tempDbFile = File.createTempFile("local_unpacked_db", ".db", context.cacheDir)
                try {
                    tempZipFile.writeBytes(content)
                    unzipDatabaseToFile(tempZipFile, tempDbFile)
                    saveDatabaseSource(context, url, tempDbFile.readBytes(), oldUrl)
                } finally {
                    tempZipFile.delete()
                    tempDbFile.delete()
                }
            } else {
                saveDatabaseSource(context, url, content, oldUrl)
            }
        } catch (e: Exception) {
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    /**
     * Creates a new database initialized from table.db asset.
     */
    suspend fun createDatabaseFromAsset(
        context: Context,
        assetFileName: String = "table.db",
        customName: String? = null
    ) {
        val fileName = customName?.takeIf { it.isNotBlank() } ?: "new_database.db"
        val formattedFileName = if (fileName.endsWith(".db", ignoreCase = true)) fileName else "$fileName.db"
        val url = DatabaseState.toLocalUrl(formattedFileName)

        addDatabase(url)
        try {
            val content = withContext(Dispatchers.IO) {
                context.assets.open(assetFileName).use { inputStream ->
                    inputStream.readBytes()
                }
            }
            saveDatabaseSource(context, url, content)
        } catch (e: Exception) {
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    /**
     * Enqueues database refresh on the background worker
     * so that databases are downloaded/unpacked sequentially.
     * Returns false if this database is already queued or downloading.
     */
    fun refreshDatabaseInBackground(context: Context, url: String): Boolean {
        return io.github.rumcajs.offlinewebsearch.workers.DatabaseUpdateWorker.enqueueDatabase(context, url)
    }

    /**
     * Downloads a database from the internet, unzips it if needed, and saves it as a local database source.
     */
    suspend fun saveDatabaseFromInternet(
        context: Context,
        url: String,
        oldUrl: String? = null
    ) {
        val state = DatabaseState.fromUrl(url)
        val isZip = url.endsWith(".db.zip", ignoreCase = true) || url.endsWith(".zip", ignoreCase = true)

        if (state.extension != ".json" && state.extension != ".db" && !isZip) {
            throw IllegalArgumentException("URL must end with .json, .db, .zip, or .db.zip")
        }

        if (oldUrl == null) {
            addDatabase(url)
        } else {
            updateDatabase(oldUrl, url)
        }

        // Set status to DOWNLOADING
        updateDatabaseStatus(url, DatabaseStatus.DOWNLOADING)

        try {
            if (!NetworkUtils.verifyUrl(url)) {
                throw IOException("Invalid URL or server unreachable")
            }

            val response = NetworkUtils.executeRequestBinary(url)
            val content = if (response.isValid) response.bytes else null

            if (content == null) {
                throw IOException("Failed to download database files")
            }

            if (isZip) {
                updateDatabaseStatus(url, DatabaseStatus.UNPACKING)
                val tempZipFile =
                    withContext(Dispatchers.IO) {
                        File.createTempFile("temp_db", ".zip", context.cacheDir)
                    }
                val tempDbFile = withContext(Dispatchers.IO) {
                    File.createTempFile("unpacked_db", ".db", context.cacheDir)
                }
                try {
                    tempZipFile.writeBytes(content)
                    unzipDatabaseToFile(tempZipFile, tempDbFile)
                    saveDatabaseSourceFromFile(context, url, tempDbFile, oldUrl)
                } catch (e: Exception) {
                    val errorDescription = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Unknown error"}"
                    throw IOException("Failed to extract .db from zip file ($errorDescription)", e)
                } finally {
                    tempZipFile.delete()
                    tempDbFile.delete()
                }
            } else {
                saveDatabaseSource(context, url, content, oldUrl)
            }
        } catch (e: Exception) {
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    private suspend fun saveDatabaseSourceFromFile(
        context: Context,
        url: String,
        sourceFile: File,
        oldUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = DateUtils.getCurrentIsoTimestamp()
        val newState = DatabaseState.fromUrl(url).copy(
            status = DatabaseStatus.READY,
            progress = 1.0f,
            dateCreated = now,
            dateLastRefresh = now
        )

        try {
            // Remove old/existing files and sidecars for this local database
            removeDatabaseFiles(context, newState.localFileName)

            context.openFileOutput(newState.localFileName, Context.MODE_PRIVATE).use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            // Inspect unpacked database for configurationentry & searchview tables using domain models
            val dbFile = if (newState.extension == ".db") File(context.filesDir, newState.localFileName) else null
            val configEntry = if (dbFile != null) ConfigurationEntry.readFromDatabase(dbFile) else null
            val searchViewEntry = if (dbFile != null) SearchViewRepository.readDefaultFromDatabase(dbFile) else null

            updateConfig { config ->
                if (oldUrl != null && oldUrl != url) {
                    val oldState = DatabaseState.fromUrl(oldUrl)
                    removeDatabaseFiles(context, oldState.localFileName)
                }

                val newDatabases = config.databases.toMutableMap().apply {
                    if (oldUrl != null) {
                        remove(oldUrl)?.let { state ->
                            put(url, state.copy(
                                url = url,
                                localFileName = newState.localFileName,
                                status = DatabaseStatus.READY,
                                progress = 1.0f,
                                errorMessage = null,
                                dateCreated = state.dateCreated ?: now,
                                dateLastRefresh = now
                            ))
                        }
                    } else {
                        put(url, newState)
                    }
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
                    if (searchViewEntry?.orderBy != null) {
                        updatedConfig = updatedConfig.copy(orderBy = searchViewEntry.orderBy!!)
                    }
                    put(url, updatedConfig)
                }

                config.copy(
                    databases = newDatabases,
                    dbConfigs = newDbConfigs,
                    activeDatabase = if (config.activeDatabase == oldUrl) url else config.activeDatabase
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    /**
     * Unpacks a ZIP archive provided as a File and extracts the first database (.db) file found into [outputFile].
     */
    @Throws(IOException::class, NoSuchElementException::class)
    internal fun unzipDatabaseToFile(zipFile: File, outputFile: File) {
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

    @Throws(IOException::class, NoSuchElementException::class)
    internal fun unzipDatabaseBytes(zipBytes: ByteArray, cacheDir: File): ByteArray {
        val tempZipFile = File.createTempFile("temp_db", ".zip", cacheDir)
        val tempDbFile = File.createTempFile("unpacked_db", ".db", cacheDir)
        try {
            tempZipFile.writeBytes(zipBytes)
            unzipDatabaseToFile(tempZipFile, tempDbFile)
            return tempDbFile.readBytes()
        } finally {
            tempZipFile.delete()
            tempDbFile.delete()
        }
    }

    fun setActiveDatabase(url: String?) {
        updateConfig { it.copy(activeDatabase = url) }
    }

    /**
     * Creates a copy of the specified database and its configuration.
     * Generates a new local:// URL with "Copy" in its display name/URL.
     */
    suspend fun duplicateDatabase(context: Context, state: DatabaseState): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseName = if (state.displayName.isNotBlank()) state.displayName else "Database"
            var copyIndex = 1
            var newUrl: String
            var newLocalFileName: String

            val ext = state.extension
            val timestamp = System.currentTimeMillis()

            do {
                val candidateName = if (copyIndex == 1) "$baseName Copy" else "$baseName Copy $copyIndex"
                newUrl = DatabaseState.toLocalUrl("$candidateName$ext")
                newLocalFileName = "db_${timestamp}_$copyIndex$ext"
                copyIndex++
            } while (config.value.databases.containsKey(newUrl))

            // Copy physical file if present
            if (state.localFileName.isNotBlank()) {
                val sourceFile = File(context.filesDir, state.localFileName)
                if (sourceFile.exists()) {
                    val destFile = File(context.filesDir, newLocalFileName)
                    sourceFile.copyTo(destFile, overwrite = true)
                }
            } else {
                // If it's a bundled asset like places_0.json
                val destFile = File(context.filesDir, newLocalFileName)
                context.assets.open("places_0.json").use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val destFile = File(context.filesDir, newLocalFileName)
            val newSize = if (destFile.exists()) destFile.length() else 0L

            val copyState = DatabaseState(
                url = newUrl,
                localFileName = newLocalFileName,
                status = DatabaseStatus.READY,
                progress = 1.0f,
                errorMessage = null,
                sizeInBytes = newSize,
                isReadOnly = !newLocalFileName.endsWith(".db"),
                dateCreated = DateUtils.getCurrentIsoTimestamp(),
                dateLastRefresh = DateUtils.getCurrentIsoTimestamp()
            )

            updateConfig { currentConfig ->
                val existingDbConfig = currentConfig.dbConfigs[state.url] ?: currentConfig.defaultDbConfig
                currentConfig.copy(
                    databases = currentConfig.databases + (newUrl to copyState),
                    dbConfigs = currentConfig.dbConfigs + (newUrl to existingDbConfig)
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
