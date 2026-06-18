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

    fun updateConfig(update: (io.github.rumcajs.offlinewebsearch.data.AppConfiguration) -> io.github.rumcajs.offlinewebsearch.data.AppConfiguration) {
        _config.update(update)
    }

    fun setDirectLinks(enabled: Boolean) {
        updateConfig { it.copy(directLinks = enabled) }
    }

    fun setShowIcons(enabled: Boolean) {
        updateConfig { it.copy(showIcons = enabled) }
    }

    fun setOrderBy(orderBy: io.github.rumcajs.offlinewebsearch.data.OrderBy) {
        updateConfig { it.copy(orderBy = orderBy) }
    }

    fun setViewStyle(viewStyle: io.github.rumcajs.offlinewebsearch.data.ViewStyle) {
        updateConfig { it.copy(viewStyle = viewStyle) }
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
}
