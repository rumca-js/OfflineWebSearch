package io.github.rumcajs.offlinewebsearch.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import io.github.rumcajs.offlinewebsearch.data.OrderBy
import io.github.rumcajs.offlinewebsearch.ui.components.FilterOption

/**
 * Represents the active filter/order-by override for the [EntryListScreen].
 *
 * [None] means no override is active; the global config order is used and all
 * entries are shown.
 * [Visited] restricts results to entries the user has visited.
 * [ReadLater] restricts results to entries saved for reading later.
 * [ByDatePublished], [ByVotes], [ByVisits] change the sort order without
 * restricting the result set.
 */
enum class SearchFilter(val label: String) {
    None("None"),
    Visited("Visited"),
    ReadLater("Read Later"),
    ByDatePublished("By Date Published"),
    ByVotes("By Votes"),
    ByVisits("By Visits");

    /** Whether this filter restricts results to the visited-entries table. */
    val filterByVisited: Boolean get() = this == Visited

    /** Whether this filter restricts results to the read-later table. */
    val filterByReadLater: Boolean get() = this == ReadLater

    /**
     * Returns the [OrderBy] override for this filter, or null to fall back to
     * the global configuration order.
     */
    fun orderByOverride(): OrderBy? = when (this) {
        ByDatePublished -> OrderBy.DATE_PUBLISHED
        ByVotes -> OrderBy.PAGE_RATING_VOTES
        ByVisits -> OrderBy.PAGE_RATING_VISITS_DESC
        else -> null
    }

    /** Converts this enum value to a [FilterOption] for use with [SearchContainer]. */
    fun toFilterOption(): FilterOption = FilterOption(
        key = name,
        label = label,
        icon = when (this) {
            Visited -> Icons.Default.History
            ReadLater -> Icons.Default.Bookmark
            ByDatePublished -> Icons.Default.DateRange
            ByVotes -> Icons.Default.Star
            ByVisits -> Icons.Default.Visibility
            None -> Icons.Default.Star // never shown directly
        }
    )

    companion object {
        /**
         * Builds the list of [FilterOption] objects to pass to [SearchContainer]
         * for [EntryListScreen].
         *
         * @param showVisited Include the "Visited" option (requires visit tracking enabled).
         * @param showReadLater Include the "Read Later" option (requires writable db).
         */
        fun entryFilterOptions(showVisited: Boolean, showReadLater: Boolean): List<FilterOption> =
            buildList {
                if (showVisited) add(Visited.toFilterOption())
                if (showReadLater) add(ReadLater.toFilterOption())
                add(ByDatePublished.toFilterOption())
                add(ByVotes.toFilterOption())
                add(ByVisits.toFilterOption())
            }

        /** Returns the [SearchFilter] that matches [key], or [None] if not found. */
        fun fromKey(key: String): SearchFilter =
            entries.firstOrNull { it.name == key } ?: None
    }
}
