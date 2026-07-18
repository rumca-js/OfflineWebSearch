package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.net.Uri
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

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
     * Safely saves database content (either from a local byte array or a remote download)
     * and updates the AppConfiguration maps.
     */
    fun saveDatabaseSource(
        context: Context,
        url: String,
        content: ByteArray,
        oldUrl: String? = null
    ) {
        val newState = DatabaseState.fromUrl(url).copy(status = DatabaseStatus.READY)

        configScope.launch {
            try {
                // 1. Write the new file
                context.openFileOutput(newState.localFileName, Context.MODE_PRIVATE).use { output ->
                    output.write(content)
                }

                // 2. Perform old file cleanup and configuration state transition
                updateConfig { config ->
                    // Clean up old file if the URL actually changed
                    if (oldUrl != null && oldUrl != url) {
                        val oldState = DatabaseState.fromUrl(oldUrl)
                        File(context.filesDir, oldState.localFileName).delete()
                    }

                    // Prepare updated maps
                    val newDatabases = config.databases.toMutableMap().apply {
                        if (oldUrl != null) {
                            remove(oldUrl)?.let { state ->
                                put(url, state.copy(
                                    url = url,
                                    localFileName = newState.localFileName,
                                    status = DatabaseStatus.READY,
                                    errorMessage = null
                                ))
                            }
                        } else {
                            put(url, newState)
                        }
                    }

                    val newDbConfigs = config.dbConfigs.toMutableMap().apply {
                        if (oldUrl != null) {
                            remove(oldUrl)?.let { dbConfig -> put(url, dbConfig) }
                        }
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
            }
        }
    }

    fun updateDatabaseStatus(url: String, status: DatabaseStatus, errorMessage: String? = null) {
        updateConfig { config ->
            val newDatabases = config.databases.toMutableMap().apply {
                get(url)?.let { state ->
                    put(url, state.copy(status = status, errorMessage = errorMessage))
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

            saveDatabaseSource(context, url, content, oldUrl)
        } catch (e: Exception) {
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
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
        val isZip = url.endsWith(".db.zip", ignoreCase = true)

        if (state.extension != ".json" && state.extension != ".db" && !isZip) {
            throw IllegalArgumentException("URL must end with .json, .db, or .db.zip")
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
            var content = if (response.isValid) response.bytes else null

            if (content == null) {
                throw IOException("Failed to download database files")
            }

            if (isZip) {
                // Set status to UNPACKING
                updateDatabaseStatus(url, DatabaseStatus.UNPACKING)
                val unpackedContent = unzipDatabaseBytes(content)
                if (unpackedContent != null) {
                    content = unpackedContent
                } else {
                    throw IOException("Failed to extract .db from zip file")
                }
            }

            saveDatabaseSource(context, url, content, oldUrl)
        } catch (e: Exception) {
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    private fun unzipDatabaseBytes(zipBytes: ByteArray): ByteArray? {
        return try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    // Find the actual database file inside the zip
                    if (!entry.isDirectory && entry.name.endsWith(".db")) {
                        val buffer = ByteArray(1024)
                        val out = ByteArrayOutputStream()
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            out.write(buffer, 0, len)
                        }
                        return out.toByteArray()
                    }
                    entry = zis.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun setActiveDatabase(url: String?) {
        updateConfig { it.copy(activeDatabase = url) }
    }
}
