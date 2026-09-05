package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.OrderBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repository for reading and filtering entries backed by JSON assets or local JSON files.
 * Inherits from [EntryRepository].
 */
object EntryJsonRepository : EntryRepository() {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

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

    override suspend fun countEntries(
        context: Context,
        activeDatabaseState: DatabaseState?,
        searchQuery: String,
        orderBy: OrderBy,
        filterByVisited: Boolean,
        filterByReadLater: Boolean
    ): Int = withContext(Dispatchers.IO) {
        val entries = if (activeDatabaseState == null) {
            getEntriesFromAssets(context, defaultAssets)
        } else {
            getEntriesFromJson(context, activeDatabaseState)
        }
        filterInMemory(entries, searchQuery, filterByVisited, filterByReadLater).size
    }

    override suspend fun getEntriesPage(
        context: Context,
        activeDatabaseState: DatabaseState?,
        searchQuery: String,
        orderBy: OrderBy,
        offset: Int,
        pageSize: Int,
        filterByVisited: Boolean,
        filterByReadLater: Boolean
    ): List<Entry> = withContext(Dispatchers.IO) {
        val entries = if (activeDatabaseState == null) {
            getEntriesFromAssets(context, defaultAssets)
        } else {
            getEntriesFromJson(context, activeDatabaseState)
        }
        val all = filterInMemory(entries, searchQuery, filterByVisited, filterByReadLater)
        all.sortedByOrderBy(orderBy).drop(offset).take(pageSize)
    }

    override suspend fun clear(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        Pair(false, "JSON databases do not support clear")
    }

    /**
     * Reads and deserializes entry records from APK bundled assets.
     */
    fun getEntriesFromAssets(context: Context, assets: List<String>): List<Entry> {
        val loaded = mutableListOf<Entry>()
        assets.forEach { fileName ->
            try {
                context.assets.open(fileName).bufferedReader().use { reader ->
                    val jsonString = reader.readText()
                    val places: List<Entry> = jsonConfig.decodeFromString(jsonString)
                    loaded.addAll(places)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return loaded
    }

    /**
     * Reads and deserializes entry records from a local JSON database file.
     */
    fun getEntriesFromJson(context: Context, state: DatabaseState): List<Entry> {
        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) return emptyList()
        return try {
            file.bufferedReader().use { reader ->
                jsonConfig.decodeFromString(reader.readText())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Filters a list of entries in-memory by search query and visited/read later filters.
     */
    fun filterInMemory(
        entries: List<Entry>,
        searchQuery: String,
        filterByVisited: Boolean = false,
        filterByReadLater: Boolean = false
    ): List<Entry> {
        val baseList = if (filterByVisited) {
            entries.filter { (it.page_rating_visits ?: 0) > 0 }
        } else if (filterByReadLater) {
            entries.filter { it.bookmarked == true }
        } else {
            entries
        }

        if (searchQuery.isBlank()) return baseList
        val query = searchQuery.trim()
        val likeRegex = Regex(
            """^(title|link|description|tag|tags)\s+LIKE\s+['"]?%?([^%'"]+)%?['"]?$""",
            RegexOption.IGNORE_CASE
        )
        val match = likeRegex.find(query)
        return if (match != null) {
            val field = match.groupValues[1].lowercase()
            val term = match.groupValues[2].trim()
            baseList.filter { entry ->
                when (field) {
                    "title" -> entry.title?.contains(term, ignoreCase = true) == true
                    "link" -> entry.link?.contains(term, ignoreCase = true) == true
                    "description" -> entry.description?.contains(term, ignoreCase = true) == true
                    "tag", "tags" -> entry.tags?.any { it.contains(term, ignoreCase = true) } == true
                    else -> false
                }
            }
        } else {
            baseList.filter { entry ->
                entry.title?.contains(query, ignoreCase = true) == true ||
                    entry.description?.contains(query, ignoreCase = true) == true ||
                    entry.link?.contains(query, ignoreCase = true) == true ||
                    entry.tags?.any { it.contains(query, ignoreCase = true) } == true
            }
        }
    }
}

/** Sorts a list of [Entry] for this [OrderBy] value. */
fun List<Entry>.sortedByOrderBy(orderBy: OrderBy): List<Entry> = when (orderBy) {
    OrderBy.PAGE_RATING_VOTES -> sortedByDescending { it.page_rating_votes ?: 0 }
    OrderBy.PAGE_RATING_VISITS_DESC -> sortedByDescending { it.page_rating_visits ?: 0 }
    OrderBy.PAGE_RATING_VISITS_ASC -> sortedBy { it.page_rating_visits ?: 0 }
    OrderBy.DATE_CREATED -> sortedByDescending { it.date_created ?: "" }
    OrderBy.DATE_PUBLISHED -> sortedByDescending { it.date_published ?: "" }
    OrderBy.STARS_DESC -> sortedByDescending { it.socialData?.stars ?: 0 }
    OrderBy.STARS_ASC -> sortedBy { it.socialData?.stars ?: 0 }
    OrderBy.FOLLOWERS_COUNT_DESC -> sortedByDescending { it.socialData?.followersCount ?: 0 }
    OrderBy.FOLLOWERS_COUNT_ASC -> sortedBy { it.socialData?.followersCount ?: 0 }
}
