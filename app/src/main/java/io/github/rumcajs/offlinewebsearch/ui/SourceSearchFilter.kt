package io.github.rumcajs.offlinewebsearch.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SortByAlpha
import io.github.rumcajs.offlinewebsearch.data.EntryOrderBy
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOrder
import io.github.rumcajs.offlinewebsearch.ui.components.FilterOption


/** Filter key constants for [SourcesViewModel] dropdown options. */
const val SOURCE_FILTER_KEY_BY_URL = "by_url"
const val SOURCE_FILTER_KEY_BY_TITLE = "by_title"
const val SOURCE_FILTER_KEY_BY_FETCH_TIME = "by_fetch_time"

const val SOURCE_FILTER_KEY_BY_CONSECUTIVE_ERRORS = "by_consecutive_errors"


val SOURCE_FILTER_OPTIONS = listOf(
    FilterOption(
        key = SOURCE_FILTER_KEY_BY_TITLE,
        label = "By Title",
        icon = Icons.Default.SortByAlpha
    ),
    FilterOption(
        key = SOURCE_FILTER_KEY_BY_FETCH_TIME,
        label = "By Fetch Time",
        icon = Icons.Default.DateRange
    ),
    FilterOption(
        key = SOURCE_FILTER_KEY_BY_CONSECUTIVE_ERRORS,
        label = "By Errors",
        icon = Icons.Default.Error
    ),
    FilterOption(
        key = SOURCE_FILTER_KEY_BY_URL,
        label = "By Url",
        icon = Icons.Default.SortByAlpha
    ),
)

fun orderToKey(order: SourceOrder): String {
    return when (order) {
        SourceOrder.ByUrl -> SOURCE_FILTER_KEY_BY_URL
        SourceOrder.ByTitle -> SOURCE_FILTER_KEY_BY_TITLE
        SourceOrder.ByFetchTime -> SOURCE_FILTER_KEY_BY_FETCH_TIME
        SourceOrder.ByConsecutiveErrors -> SOURCE_FILTER_KEY_BY_CONSECUTIVE_ERRORS
    }
}
fun keyToOrder(key: String?): SourceOrder {
    return when (key) {
        SOURCE_FILTER_KEY_BY_URL -> SourceOrder.ByUrl
        SOURCE_FILTER_KEY_BY_TITLE -> SourceOrder.ByTitle
        SOURCE_FILTER_KEY_BY_FETCH_TIME -> SourceOrder.ByFetchTime
        SOURCE_FILTER_KEY_BY_CONSECUTIVE_ERRORS -> SourceOrder.ByConsecutiveErrors
        else -> SourceOrder.ByTitle
    }
}