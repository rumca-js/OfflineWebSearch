package io.github.rumcajs.offlinewebsearch.data

import kotlinx.serialization.Serializable

const val DATABASES_LIST: String = "https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases.txt"
const val DATABASES_LIST_INIT: String = "https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases_init.txt"
const val DEFAULT_DATABASE_NAME: String = "Default (Assets)"
const val DEFAULT_DATABASE_FILE: String = "default.db"
const val ASSET_EMPTY_TABLE: String = "table.db"

/**
 * The URL key used for the built-in default database in [AppConfiguration.databases].
 * An empty string is used so that it can never collide with a real HTTP/local URL.
 */
const val DEFAULT_DATABASE_URL: String = ""

val defaultAssets = listOf(
    "places_0.json",
    "places_1.json",
    "places_2.json",
    "places_3.json",
    "places_4.json",
    "places_5.json",
    "places_6.json",
    "places_7.json",
    "places_8.json",
    "places_9.json",
    "places_10.json",
)

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
    val activeDatabaseUrl: String? = null, // relates to DatabaseState.url
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
        get() = activeDatabaseUrl?.let { dbConfigs[it] } ?: defaultDbConfig

    /**
     * Gets the state (downloading, unpacking, ready, etc.) of the currently active database.
     * The default database ([DEFAULT_DATABASE_URL]) is always present in [databases] after
     * [io.github.rumcajs.offlinewebsearch.data.AppConfigManager.ensureDefaultDatabase] runs.
     */
    val activeDatabaseState: DatabaseState?
        get() = databases[activeDatabaseUrl ?: DEFAULT_DATABASE_URL]
            ?: if (activeDatabaseUrl.isNullOrEmpty()) {
                DatabaseState(
                    url = DEFAULT_DATABASE_URL,
                    localFileName = DEFAULT_DATABASE_FILE,
                    displayNameField = DEFAULT_DATABASE_NAME,
                    isReadOnly = false
                )
            } else null

    val activeDatabaseDisplayName: String
        get() = activeDatabaseState?.displayName ?: DEFAULT_DATABASE_NAME

    fun updateActiveDbConfig(update: (DatabaseConfiguration) -> DatabaseConfiguration): AppConfiguration {
        val activeDb = activeDatabaseUrl
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
