package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.net.Uri
import io.github.rumcajs.offlinewebsearch.data.builders.DefaultDatabaseBuilder
import io.github.rumcajs.offlinewebsearch.data.builders.InternetDatabaseBuilder
import io.github.rumcajs.offlinewebsearch.data.builders.LocalDatabaseBuilder
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import io.github.rumcajs.offlinewebsearch.workers.DatabaseUpdateWorker
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

/**
 * Singleton managing the application configuration, preference states,
 * and database registrations.
 *
 * Database creation, downloading, unpacking, and table population are
 * delegated to specialized [io.github.rumcajs.offlinewebsearch.data.builders.DatabaseBuilder] implementations.
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

    /**
     * Initializes configuration from local persisted storage and bundled network config.
     * Also ensures the built-in default database (`default.db`) is populated on first launch.
     */
    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext

        // Load configurations synchronously so state is populated before first UI frame
        loadPersistedConfigSync(applicationContext)
        loadNetworkConfigSync(applicationContext)

        // Ensure default SQLite database exists in internal storage
        configScope.launch {
            try {
                ensureDefaultDatabase(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Ensures that the default database file (`default.db`) is built and ready in application storage.
     */
    suspend fun ensureDefaultDatabase(context: Context): DatabaseState {
        val builder = DefaultDatabaseBuilder(context)
        return builder.build()
    }

    /**
     * Updates the initialization state of the application.
     *
     * @param initialized True if initial setup/wizard has been completed, false otherwise.
     */
    fun setInitialized(initialized: Boolean = true) {
        updateConfig { it.copy(isInitialized = initialized) }
        setActiveDatabase(null)
    }

    /**
     * Fetches startup databases from [DATABASES_LIST_INIT], registers and enqueues them
     * with [DatabaseUpdateWorker] for background downloading, sets the active database if unset,
     * and marks the application as initialized.
     *
     * @param context Application context for worker queuing and resource access.
     * @return Result containing the list of configured database URLs, or failure exception.
     */
    suspend fun initializeStartupDatabases(context: Context): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (config.value.networkConfig.disabled) {
                return@withContext Result.failure(IOException("Network communication is disabled in settings."))
            }

            val response = NetworkUtils.executeRequest(DATABASES_LIST_INIT)
            val text = if (response.isValid) response.text else null
            if (text.isNullOrBlank()) {
                return@withContext Result.failure(IOException("Failed to download startup databases list."))
            }

            val urls = text.lines()
                .map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }

            if (urls.isEmpty()) {
                return@withContext Result.failure(IOException("No valid database URLs found in startup list."))
            }

            withContext(Dispatchers.Main) {
                urls.forEach { url ->
                    DatabaseUpdateWorker.enqueueDatabase(context, url)
                }
                if (config.value.activeDatabaseUrl == null) {
                    setActiveDatabase(urls.first())
                }
                setInitialized(true)
            }
            Result.success(urls)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateConfig(update: (AppConfiguration) -> AppConfiguration) {
        _config.update(update)
        saveConfigAsync()
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

    fun setEntriesVisitAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0f, 1f)
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(entriesVisitAlpha = clamped) }
        }
    }

    fun setEntriesDeadAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0f, 1f)
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(entriesDeadAlpha = clamped) }
        }
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
                activeDatabaseUrl = if (it.activeDatabaseUrl == url) null else it.activeDatabaseUrl
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
                activeDatabaseUrl = if (config.activeDatabaseUrl == oldUrl) newUrl else config.activeDatabaseUrl
            )
        }
    }

    /**
     * Updates the display name field of a configured database.
     *
     * @param url The database URL or key identifier.
     * @param displayName The new display name for the database.
     */
    fun updateDatabaseDisplayName(url: String, displayName: String) {
        updateConfig { config ->
            val newDatabases = config.databases.toMutableMap().apply {
                get(url)?.let { state ->
                    put(url, state.copy(displayNameField = displayName.trim()))
                }
            }
            config.copy(databases = newDatabases)
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
     * Safely saves database content and updates the AppConfiguration maps using [LocalDatabaseBuilder].
     */
    suspend fun saveDatabaseSource(
        context: Context,
        url: String,
        content: ByteArray,
        oldUrl: String? = null
    ) {
        val builder = LocalDatabaseBuilder.fromBytes(context, url, content, oldUrl)
        builder.build()
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
                        DatabaseStatus.POPULATING_TABLE -> 0.85f
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
     * Reads database bytes from a local URI and saves them as an SQLite database using [LocalDatabaseBuilder].
     */
    suspend fun saveDatabaseLocal(
        context: Context,
        url: String,
        uri: Uri,
        oldUrl: String? = null
    ) {
        val builder = LocalDatabaseBuilder(
            context = context,
            url = url,
            uri = uri,
            oldUrl = oldUrl
        )
        builder.build()
    }

    /**
     * Creates a new database initialized from table.db asset using [LocalDatabaseBuilder].
     */
    suspend fun createDatabaseFromAsset(
        context: Context,
        assetFileName: String = ASSET_EMPTY_TABLE,
        customName: String? = null
    ) {
        val builder = LocalDatabaseBuilder.fromAsset(
            context = context,
            customName = customName,
            assetFileName = assetFileName
        )
        builder.build()
    }

    /**
     * Enqueues database refresh on the background worker
     * so that databases are downloaded/unpacked sequentially.
     * Returns false if this database is already queued or downloading.
     */
    fun refreshDatabaseInBackground(context: Context, url: String): Boolean {
        return DatabaseUpdateWorker.enqueueDatabase(context, url)
    }

    /**
     * Downloads a database from the internet, unzips/populates it if needed,
     * and saves it as a local SQLite database using [InternetDatabaseBuilder].
     */
    suspend fun saveDatabaseFromInternet(
        context: Context,
        url: String,
        oldUrl: String? = null
    ) {
        val builder = InternetDatabaseBuilder(
            context = context,
            url = url,
            oldUrl = oldUrl
        )
        builder.build()
    }

    fun setActiveDatabase(url: String?) {
        updateConfig { it.copy(activeDatabaseUrl = url) }
    }

    /**
     * Creates a copy of the specified database and its configuration using [LocalDatabaseBuilder].
     */
    suspend fun duplicateDatabase(context: Context, state: DatabaseState): Boolean = withContext(Dispatchers.IO) {
        try {
            val builder = LocalDatabaseBuilder.duplicate(context, state)
            builder.build()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
