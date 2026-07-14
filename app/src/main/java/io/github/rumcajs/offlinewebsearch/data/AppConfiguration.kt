package io.github.rumcajs.offlinewebsearch.data

import kotlinx.serialization.Serializable

@Serializable
enum class OrderBy(val displayName: String) {
    PAGE_RATING_VOTES("Page Rating Votes"),
    DATE_CREATED("Date Created"),
    DATE_PUBLISHED("Date Published")
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
    val defaultDbConfig: DatabaseConfiguration = DatabaseConfiguration(),
    val dbConfigs: Map<String, DatabaseConfiguration> = emptyMap(),

    // general app configuration, does
    val userAge: Int = 0,

    val networkConfig : NetworkConfig = NetworkConfig(),

    // main things
    val databases: List<String> = emptyList(),
    val activeDatabase: String? = null,
) {
    val dbconfig: DatabaseConfiguration
        get() = activeDatabase?.let { dbConfigs[it] } ?: defaultDbConfig

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

