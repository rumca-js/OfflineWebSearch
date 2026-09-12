package io.github.rumcajs.offlinewebsearch.data

import kotlinx.serialization.Serializable

const val DATABASES_LIST: String = "https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases.txt"
const val DATABASES_LIST_INIT: String = "https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases_init.txt"
const val DEFAULT_DATABASE_NAME: String = "Default (Assets)"

@Serializable
data class NetworkConfig(
    val connectTimeout: Int = 10000,
    val readTimeout: Int = 10000,
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    val disabled: Boolean = false
)

@Serializable
data class AppConfiguration(
    // TOD do we need default db config?
    val defaultDbConfig: DatabaseConfiguration = DatabaseConfiguration(),
    val dbConfigs: Map<String, DatabaseConfiguration> = emptyMap(),

    // general app configuration, does
    val userAge: Int = 0,
    val networkConfig : NetworkConfig = NetworkConfig(),

    // main things
    val databases: Map<String, DatabaseState> = emptyMap(),
    val activeDatabase: String? = null,
    val supportedDatabasesExtensions: List<String> = listOf(".db",
        ".json",
        ".zip",      // contains json files
        ".db.zip"),  // contains db archived
    val isInitialized: Boolean = false
) {
    fun isSupportedFileName(fileName: String): Boolean {
        return supportedDatabasesExtensions.any { ext -> fileName.endsWith(ext, ignoreCase = true) }
    }

    val dbconfig: DatabaseConfiguration
        get() = activeDatabase?.let { dbConfigs[it] } ?: defaultDbConfig

    /**
     * Gets the state (downloading, unpacking, ready, etc.) of the currently active database.
     * Returns null if there is no active database or if it hasn't been registered in the map.
     */
    val activeDatabaseState: DatabaseState?
        get() = activeDatabase?.let { databases[it] }

    val activeDatabaseDisplayName: String
        get() = activeDatabaseState?.displayName ?: "Default (Assets)"

    fun updateActiveDbConfig(update: (DatabaseConfiguration) -> DatabaseConfiguration): AppConfiguration {
        val activeDb = activeDatabase
        return if (activeDb != null) {
            val currentDbConfig = dbConfigs[activeDb] ?: DatabaseConfiguration()
            val newDbConfig = update(currentDbConfig)
            this.copy(dbConfigs = dbConfigs + (activeDb to newDbConfig))
        } else {
            val newDefault = update(defaultDbConfig)
            this.copy(defaultDbConfig = newDefault)
        }
    }
}
