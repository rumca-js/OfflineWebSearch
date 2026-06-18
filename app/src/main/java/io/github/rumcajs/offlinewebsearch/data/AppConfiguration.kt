package io.github.rumcajs.offlinewebsearch.data

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
    val directLinks: Boolean = false,
    val showIcons: Boolean = false,
    val databases: List<String> = emptyList(),
    val activeDatabase: String? = null,
    val orderBy: io.github.rumcajs.offlinewebsearch.data.OrderBy = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.OrderBy.PAGE_RATING_VOTES,
    val viewStyle: io.github.rumcajs.offlinewebsearch.data.ViewStyle = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.ViewStyle.SEARCH_ENGINE,
    val userAge: Int = 0
)
