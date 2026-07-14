package io.github.rumcajs.offlinewebsearch.data

import kotlinx.serialization.Serializable

enum class OrderBy(val displayName: String) {
    PAGE_RATING_VOTES("Page Rating Votes"),
    DATE_CREATED("Date Created"),
    DATE_PUBLISHED("Date Published")
}

enum class ViewStyle(val displayName: String) {
    GALLERY("Gallery"),
    SEARCH_ENGINE("Search Engine"),
    STANDARD("Standard")
}

data class AppConfiguration(
    val dbconfig: DatabaseConfiguration = DatabaseConfiguration(),

    // App configuration
    val userAge: Int = 0,

    // TODO networking config - redundant? we have NetworkConfig
    val connectTimeout: Int = 10000,
    val readTimeout: Int = 10000,
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",

    // main things
    val databases: List<String> = emptyList(),
    val activeDatabase: String? = null,
)
{
    data class DatabaseConfiguration(
        val directLinks: Boolean = false,
        val showIcons: Boolean = false,
        val videoPreview: Boolean = false,
        val orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
        val viewStyle: ViewStyle = ViewStyle.SEARCH_ENGINE,
    )
}

@Serializable
data class NetworkConfig(
    val connectTimeout: Int = 10000,
    val readTimeout: Int = 10000,
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
