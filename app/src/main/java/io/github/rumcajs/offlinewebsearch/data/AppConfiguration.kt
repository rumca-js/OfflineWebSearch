package io.github.rumcajs.offlinewebsearch.data

import kotlinx.serialization.Serializable

@Serializable
enum class OrderBy(val displayName: String) {
    PAGE_RATING_VOTES("Page Rating Votes"),
    DATE_CREATED("Date Created"),
    DATE_PUBLISHED("Date Published")
}

@Serializable
enum class DatabaseStatus {
    INIT,
    DOWNLOADING,
    UNPACKING,
    READY,
    FAILED
}

@Serializable
data class DatabaseState(
    /** Network URL or local:// source path */
    val url: String = "",
    /** File name used in app internal storage (e.g. "db_12345.db") */
    val localFileName: String = "",
    val status: DatabaseStatus = DatabaseStatus.INIT,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val sizeInBytes: Long = 0L
) {
    /** The storage extension of the local file: ".db" or ".json" */
    val extension: String
        get() = if (localFileName.endsWith(".db")) ".db" else ".json"

    val isLocal: Boolean
        get() = url.startsWith("local://")

    val displayName: String
        get() = when {
            isLocal -> url.removePrefix("local://")
            url.isEmpty() -> "Default (Assets)"
            else -> url
        }

    companion object {
        /**
         * Derives the local file name from a source URL.
         * .db.zip URLs are stored as .db after unpacking.
         */
        private fun deriveLocalFileName(url: String): String {
            val ext = when {
                url.endsWith(".db.zip", ignoreCase = true) -> ".db"
                url.endsWith(".db", ignoreCase = true) -> ".db"
                else -> ".json"
            }
            return "db_${url.hashCode()}$ext"
        }

        /** Creates a DatabaseState with a localFileName derived from the URL */
        fun fromUrl(url: String): DatabaseState {
            return DatabaseState(
                url = url,
                localFileName = deriveLocalFileName(url)
            )
        }
    }
}


@Serializable
enum class ViewStyle(val displayName: String) {
    GALLERY("Gallery"),
    SEARCH_ENGINE("Search Engine"),
    STANDARD("Standard")
}

@Serializable
data class DatabaseConfiguration(
    val directLinks: Boolean = false,
    val showIcons: Boolean = false,
    val videoPreview: Boolean = false,
    val orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
    val viewStyle: ViewStyle = ViewStyle.SEARCH_ENGINE,
)

@Serializable
data class NetworkConfig(
    val connectTimeout: Int = 10000,
    val readTimeout: Int = 10000,
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
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
) {
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

