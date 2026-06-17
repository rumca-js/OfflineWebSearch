package com.example.index.data

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
    val orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
    val viewStyle: ViewStyle = ViewStyle.SEARCH_ENGINE,
    val userAge: Int = 0
)
