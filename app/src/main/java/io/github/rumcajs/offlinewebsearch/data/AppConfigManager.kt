package io.github.rumcajs.offlinewebsearch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton to manage app configuration.
 * Can be updated from various sources.
 */
object AppConfigManager {
    private val _config = MutableStateFlow(_root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfiguration())
    val config: StateFlow<io.github.rumcajs.offlinewebsearch.data.AppConfiguration> = _config.asStateFlow()

    fun updateConfig(update: (AppConfiguration) -> AppConfiguration) {
        _config.update(update)
    }

    fun setDirectLinks(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.copy(
                dbconfig = currentConfig.dbconfig.copy(directLinks = enabled)
            )
        }
    }

    fun setShowIcons(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.copy(
                dbconfig = currentConfig.dbconfig.copy(showIcons = enabled)
            )
        }
    }

    fun setVideoPreview(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.copy(
                dbconfig = currentConfig.dbconfig.copy(videoPreview = enabled)
            )
        }
    }

    fun setOrderBy(orderBy: OrderBy) {
        updateConfig { currentConfig ->
            currentConfig.copy(
                dbconfig = currentConfig.dbconfig.copy(orderBy = orderBy)
            )
        }
    }

    fun setViewStyle(viewStyle: ViewStyle) {
        updateConfig { currentConfig ->
            currentConfig.copy(
                dbconfig = currentConfig.dbconfig.copy(viewStyle = viewStyle)
            )
        }
    }

    fun setUserAge(age: Int) {
        updateConfig { it.copy(userAge = age) }
    }

    fun addDatabase(url: String) {
        updateConfig { it.copy(databases = it.databases + url) }
    }

    fun removeDatabase(url: String) {
        updateConfig {
            val newDatabases = it.databases - url
            it.copy(
                databases = newDatabases,
                activeDatabase = if (it.activeDatabase == url) null else it.activeDatabase
            )
        }
    }

    fun updateDatabase(oldUrl: String, newUrl: String) {
        updateConfig {
            val newDatabases = it.databases.map { url -> if (url == oldUrl) newUrl else url }
            it.copy(
                databases = newDatabases,
                activeDatabase = if (it.activeDatabase == oldUrl) newUrl else it.activeDatabase
            )
        }
    }

    fun setActiveDatabase(url: String?) {
        updateConfig { it.copy(activeDatabase = url) }
    }

    fun loadNetworkConfig(context: android.content.Context) {
        try {
            context.assets.open("network_config.json").bufferedReader().use { reader ->
                val jsonString = reader.readText()
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
                val networkConfig = json.decodeFromString<NetworkConfig>(jsonString)
                updateConfig {
                    it.copy(
                        connectTimeout = networkConfig.connectTimeout,
                        readTimeout = networkConfig.readTimeout,
                        userAgent = networkConfig.userAgent
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
