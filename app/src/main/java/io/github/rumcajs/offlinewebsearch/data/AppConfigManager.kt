package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
        val newState = DatabaseState.fromUrl(url)

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
                            remove(oldUrl)?.let { state -> put(url, state.copy(url = url, localFileName = newState.localFileName)) }
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
            }
        }
    }

    fun setActiveDatabase(url: String?) {
        updateConfig { it.copy(activeDatabase = url) }
    }
}
