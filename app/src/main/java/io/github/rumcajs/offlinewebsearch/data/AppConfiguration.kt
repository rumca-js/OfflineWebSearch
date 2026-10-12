package io.github.rumcajs.offlinewebsearch.data

import kotlinx.serialization.Serializable

const val DATABASES_LIST_JSON: String = "https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases.json"
const val DATABASES_LIST_INIT: String = "https://raw.githubusercontent.com/rumca-js/rumca-js.github.io/main/data/databases_init.txt"
const val DEFAULT_DATABASE_NAME: String = "Default (Assets)"
const val DEFAULT_DATABASE_FILE: String = "default.db"
const val ASSET_EMPTY_TABLE: String = "table.db"
const val DEFAULT_OUTDATED_FETCH_THRESHOLD_SECONDS: Long = 3600L // 1 hour

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
    val outdatedFetchThresholdSeconds: Long = DEFAULT_OUTDATED_FETCH_THRESHOLD_SECONDS,

    // main things
    val databases: Map<String, DatabaseState> = emptyMap(),

    /**
     * Identifies the active database by its local file name (e.g. "default.db", "db_12345.db").
     * Local file names are unique per database instance in app storage, unlike remote URLs which
     * can be shared by multiple database entries.
     */
    val activeDatabase: String? = null,

    /**
     * @deprecated Use [activeDatabase] (local file name) to identify the active database.
     * Multiple databases can share the same remote URL, making URL an unreliable identifier.
     * Kept for JSON backward-compatibility with persisted configs from older versions.
     */
    @Deprecated("Use activeDatabase (local file name) instead.")
    val activeDatabaseUrl: String? = null,

    val supportedDatabasesExtensions: List<String> = listOf(
        ".db",
        ".json",
        ".zip",      // contains json files
        ".sources",
        ".entries",
        ".opml",
        ".db.zip"    // contains db archived
    ),
    val isInitialized: Boolean = false
) {
    fun isSupportedFileName(fileName: String): Boolean {
        return supportedDatabasesExtensions.any { ext -> fileName.endsWith(ext, ignoreCase = true) }
    }

    /**
     * Resolves the currently active [DatabaseState] by local file name ([activeDatabase]).
     * Falls back to [activeDatabaseUrl] for backward-compatibility with persisted configs
     * from older app versions that had not yet migrated to the new identifier.
     *
     * The default database ([DEFAULT_DATABASE_URL]) is always present in [databases] after
     * [io.github.rumcajs.offlinewebsearch.data.AppConfigManager.ensureDefaultDatabase] runs.
     */
    val activeDatabaseState: DatabaseState?
        get() {
            val localFile = activeDatabase
            if (localFile != null) {
                // Primary path: find by local file name
                val found = databases.values.firstOrNull { it.localFileName == localFile }
                if (found != null) return found
                // Local file is set but not yet in the map (e.g. default before first load)
                if (localFile == DEFAULT_DATABASE_FILE) {
                    return DatabaseState(
                        url = DEFAULT_DATABASE_URL,
                        localFileName = DEFAULT_DATABASE_FILE,
                        displayNameField = DEFAULT_DATABASE_NAME,
                        isReadOnly = false
                    )
                }
                return null
            }
            // Legacy fallback: resolve via deprecated activeDatabaseUrl
            @Suppress("DEPRECATION")
            val legacyUrl = activeDatabaseUrl
            return databases[legacyUrl ?: DEFAULT_DATABASE_URL]
                ?: if (legacyUrl.isNullOrEmpty()) {
                    DatabaseState(
                        url = DEFAULT_DATABASE_URL,
                        localFileName = DEFAULT_DATABASE_FILE,
                        displayNameField = DEFAULT_DATABASE_NAME,
                        isReadOnly = false
                    )
                } else null
        }

    val activeDatabaseDisplayName: String
        get() = activeDatabaseState?.displayName ?: DEFAULT_DATABASE_NAME

    /**
     * Returns the [DatabaseConfiguration] for the currently active database,
     * resolved via [activeDatabaseState]. Falls back to [defaultDbConfig].
     */
    val dbconfig: DatabaseConfiguration
        get() {
            val activeUrl = activeDatabaseState?.url
            return activeUrl?.let { dbConfigs[it] } ?: defaultDbConfig
        }

    fun updateActiveDbConfig(update: (DatabaseConfiguration) -> DatabaseConfiguration): AppConfiguration {
        val activeUrl = activeDatabaseState?.url
        return if (activeUrl != null) {
            val currentDbConfig = dbConfigs[activeUrl] ?: DatabaseConfiguration()
            val newDbConfig = update(currentDbConfig)
            this.copy(dbConfigs = dbConfigs + (activeUrl to newDbConfig))
        } else {
            val newDefault = update(defaultDbConfig)
            this.copy(defaultDbConfig = newDefault)
        }
    }
}
