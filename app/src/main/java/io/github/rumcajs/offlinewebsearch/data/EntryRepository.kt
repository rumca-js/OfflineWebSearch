package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import kotlinx.serialization.Serializable

@Serializable
data class Entry(
    val id: Long? = null,
    val link: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    val album: String? = null,
    val language: String? = null,
    val tags: List<String>? = null,
    val page_rating_votes: Int? = 0,
    val page_rating_visits: Int? = 0,
    val page_rating: Int? = 0,
    val thumbnail: String? = null,
    val date_created: String? = null,
    val date_published: String? = null,
    val date_dead_since: String? = null,
    val age: Int? = 0,
    val status_code: Int? = 0,
    val manual_status_code: Int? = 0,
    val bookmarked: Boolean? = false,
    val source_id: Long? = null,
    val source_url: String? = null,
    val socialData: SocialData? = null
)

/**
 * Base abstract repository for entry operations.
 * Subclasses: [EntryJsonRepository] for JSON/assets and [EntrySqliteRepository] for SQLite.
 */
abstract class EntryRepository : RepositoryInterface {

    override fun getTableName(): String = "linkdatamodel"

    /**
     * Returns the total number of entries matching [searchQuery].
     */
    abstract suspend fun countEntries(
        context: Context,
        activeDatabaseState: DatabaseState? = null,
        searchQuery: String = "",
        orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
        filterByVisited: Boolean = false,
        filterByReadLater: Boolean = false
    ): Int

    /**
     * Returns a single page of [pageSize] entries starting at [offset].
     */
    abstract suspend fun getEntriesPage(
        context: Context,
        activeDatabaseState: DatabaseState? = null,
        searchQuery: String = "",
        orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
        offset: Int = 0,
        pageSize: Int = 20,
        filterByVisited: Boolean = false,
        filterByReadLater: Boolean = false
    ): List<Entry>

    companion object : RepositoryInterface {

        const val MIN_PAGE_RATING_VOTES = -100
        const val MAX_PAGE_RATING_VOTES = 100

        fun getMinPageRatingVotes(): Int = MIN_PAGE_RATING_VOTES
        fun getMaxPageRatingVotes(): Int = MAX_PAGE_RATING_VOTES

        override fun getTableName(): String = EntrySqliteRepository.getTableName()

        override suspend fun deleteById(
            context: Context,
            activeDatabaseState: DatabaseState?,
            id: Long
        ): Pair<Boolean, String?> = EntrySqliteRepository.deleteById(context, activeDatabaseState, id)

        override suspend fun clear(
            context: Context,
            activeDatabaseState: DatabaseState?
        ): Pair<Boolean, String?> = getRepository(activeDatabaseState).clear(context, activeDatabaseState)

        /**
         * Resolves the appropriate repository implementation based on [activeDatabaseState].
         */
        fun getRepository(activeDatabaseState: DatabaseState?): EntryRepository {
            return if (activeDatabaseState != null && activeDatabaseState.isSQLite) {
                EntrySqliteRepository
            } else {
                EntryJsonRepository
            }
        }

        // ──────────────────────────────────────────────────────────────────────────
        // Delegated Public API – paginated queries
        // ──────────────────────────────────────────────────────────────────────────

        /**
         * Returns the total number of entries matching [searchQuery].
         * Delegates to [EntrySqliteRepository] or [EntryJsonRepository] based on [activeDatabaseState].
         */
        suspend fun countEntries(
            context: Context,
            activeDatabaseState: DatabaseState? = null,
            searchQuery: String = "",
            orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
            filterByVisited: Boolean = false,
            filterByReadLater: Boolean = false
        ): Int = getRepository(activeDatabaseState).countEntries(
            context, activeDatabaseState, searchQuery, orderBy, filterByVisited, filterByReadLater
        )

        /**
         * Returns a single page of [pageSize] entries starting at [offset].
         * Delegates to [EntrySqliteRepository] or [EntryJsonRepository] based on [activeDatabaseState].
         */
        suspend fun getEntries(
            context: Context,
            activeDatabaseState: DatabaseState? = null,
            searchQuery: String = "",
            orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
            offset: Int = 0,
            pageSize: Int = 20,
            filterByVisited: Boolean = false,
            filterByReadLater: Boolean = false
        ): List<Entry> = getRepository(activeDatabaseState).getEntriesPage(
            context, activeDatabaseState, searchQuery, orderBy, offset, pageSize, filterByVisited, filterByReadLater
        )

        // ──────────────────────────────────────────────────────────────────────────
        // SQLite-specific Write Operations (Delegated to EntrySqliteRepository)
        // ──────────────────────────────────────────────────────────────────────────

        /**
         * Inserts a new entry (and its tags) into the SQLite database.
         * @return Triple(success, insertedRowId, errorMessage).
         *         [insertedRowId] is the primary key of the new row on success, or -1 on failure.
         */
        suspend fun add(
            context: Context,
            activeDatabaseState: DatabaseState,
            entry: Entry
        ): Triple<Boolean, Long, String?> = EntrySqliteRepository.addEntrySql(context, activeDatabaseState, entry)

        /**
         * Updates an existing entry's title and description in the database.
         * Entry is identified by its primary key [id] (or [originalLink] if [id] is null).
         */
        suspend fun update(
            context: Context,
            activeDatabaseState: DatabaseState,
            id: Long?,
            originalLink: String?,
            newTitle: String?,
            newDescription: String?
        ): Boolean = EntrySqliteRepository.updateEntrySql(context, activeDatabaseState, id, originalLink, newTitle, newDescription)

        /**
         * Sets the page_rating_votes count for an entry in the SQLite database to [vote] (clamped between MIN_PAGE_RATING_VOTES and MAX_PAGE_RATING_VOTES).
         * @return Pair where first is true on success and second is the new vote total (or null on failure).
         */
        suspend fun setVote(
            context: Context,
            activeDatabaseState: DatabaseState?,
            id: Long?,
            vote: Int
        ): Pair<Boolean, Int?> = EntrySqliteRepository.setVoteSql(context, activeDatabaseState, id, vote)

        /**
         * Increments the page_rating_visits count for an entry in the SQLite database.
         */
        suspend fun incrementVisit(
            context: Context,
            activeDatabaseState: DatabaseState?,
            id: Long?,
            link: String?
        ): Boolean = EntrySqliteRepository.incrementVisitSql(context, activeDatabaseState, id, link)

        /**
         * Deletes an entry (and its associated tags, history, social data) from the SQLite database.
         * Entry is identified by its primary key [id] (or [link] if [id] is null).
         * @return true if at least one row was deleted, false otherwise.
         */
        suspend fun deleteEntry(
            context: Context,
            activeDatabaseState: DatabaseState,
            id: Long?,
            link: String?
        ): Boolean = EntrySqliteRepository.deleteEntry(context, activeDatabaseState, id, link)
    }
}


