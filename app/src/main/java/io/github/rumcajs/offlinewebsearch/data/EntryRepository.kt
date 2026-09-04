package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    abstract suspend fun getEntriesPageSql(
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
        suspend fun getEntriesPageSql(
            context: Context,
            activeDatabaseState: DatabaseState? = null,
            searchQuery: String = "",
            orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
            offset: Int = 0,
            pageSize: Int = 20,
            filterByVisited: Boolean = false,
            filterByReadLater: Boolean = false
        ): List<Entry> = getRepository(activeDatabaseState).getEntriesPageSql(
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
        suspend fun addEntrySql(
            context: Context,
            activeDatabaseState: DatabaseState,
            entry: Entry
        ): Triple<Boolean, Long, String?> = EntrySqliteRepository.addEntrySql(context, activeDatabaseState, entry)

        /**
         * Updates an existing entry's title and description in the database.
         * Entry is identified by its primary key [id] (or [originalLink] if [id] is null).
         */
        suspend fun updateEntrySql(
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
        suspend fun setVoteSql(
            context: Context,
            activeDatabaseState: DatabaseState?,
            id: Long?,
            vote: Int
        ): Pair<Boolean, Int?> = EntrySqliteRepository.setVoteSql(context, activeDatabaseState, id, vote)

        /**
         * Increments the page_rating_visits count for an entry in the SQLite database.
         */
        suspend fun incrementVisitSql(
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

        // ──────────────────────────────────────────────────────────────────────────
        // SQL Constants and Cursor Helpers
        // ──────────────────────────────────────────────────────────────────────────

        /** Standard column selection list for queries on `linkdatamodel` (aliased as `l`). */
        const val ENTRY_SELECT_COLUMNS = "l.id, l.link, l.title, l.description, l.author, l.album, l.language, l.page_rating_votes, l.page_rating_visits, l.page_rating, l.thumbnail, l.date_created, l.date_published, l.date_dead_since, l.age, l.status_code, l.manual_status_code, l.bookmarked, l.source_id, l.source_url"

        /** Column selection list for `socialdata` (aliased as `s`). */
        const val SOCIAL_DATA_SELECT_COLUMNS = "s.id AS s_id, s.entry_id AS s_entry_id, s.thumbs_up, s.thumbs_down, s.view_count, s.rating AS s_rating, s.upvote_ratio, s.upvote_diff, s.upvote_view_ratio, s.stars, s.followers_count, s.date_updated"

        /** Maps a cursor row to an [Entry]. */
        fun cursorToEntry(c: android.database.Cursor): Entry {
            val id = c.getLong(c.getColumnIndexOrThrow("id"))
            val title = c.getString(c.getColumnIndexOrThrow("title"))
            val description = c.getString(c.getColumnIndexOrThrow("description"))
            val thumbnail = c.getString(c.getColumnIndexOrThrow("thumbnail"))
            val link = c.getString(c.getColumnIndexOrThrow("link"))
            val votes = c.getInt(c.getColumnIndexOrThrow("page_rating_votes"))
            val visits = c.getInt(c.getColumnIndexOrThrow("page_rating_visits"))
            val rating = c.getInt(c.getColumnIndexOrThrow("page_rating"))
            val dateCreated = c.getString(c.getColumnIndexOrThrow("date_created"))
            val datePublished = c.getString(c.getColumnIndexOrThrow("date_published"))
            val dateDeadSince = c.getString(c.getColumnIndexOrThrow("date_dead_since"))
            val author = c.getString(c.getColumnIndexOrThrow("author"))
            val album = c.getString(c.getColumnIndexOrThrow("album"))
            val language = c.getString(c.getColumnIndexOrThrow("language"))
            val age = c.getInt(c.getColumnIndexOrThrow("age"))
            val statusCode = c.getInt(c.getColumnIndexOrThrow("status_code"))
            val manualStatusCode = c.getInt(c.getColumnIndexOrThrow("manual_status_code"))
            val bookmarked = c.getInt(c.getColumnIndexOrThrow("bookmarked")) == 1
            val sourceIdIndex = c.getColumnIndex("source_id")
            val sourceId = if (sourceIdIndex != -1 && !c.isNull(sourceIdIndex)) c.getLong(sourceIdIndex) else null
            val sourceUrlIndex = c.getColumnIndex("source_url")
            val sourceUrl = if (sourceUrlIndex != -1 && !c.isNull(sourceUrlIndex)) c.getString(sourceUrlIndex) else null
            val tagIndex = c.getColumnIndex("tag")
            val tagString = if (tagIndex != -1 && !c.isNull(tagIndex)) c.getString(tagIndex) else null
            val tags = tagString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

            val sIdIndex = c.getColumnIndex("s_id")
            val socialData = if (sIdIndex != -1 && !c.isNull(sIdIndex)) {
                SocialData(
                    id = c.getLong(sIdIndex),
                    entryId = if (c.isNull(c.getColumnIndexOrThrow("s_entry_id"))) null else c.getLong(c.getColumnIndexOrThrow("s_entry_id")),
                    thumbsUp = if (c.isNull(c.getColumnIndexOrThrow("thumbs_up"))) null else c.getInt(c.getColumnIndexOrThrow("thumbs_up")),
                    thumbsDown = if (c.isNull(c.getColumnIndexOrThrow("thumbs_down"))) null else c.getInt(c.getColumnIndexOrThrow("thumbs_down")),
                    viewCount = if (c.isNull(c.getColumnIndexOrThrow("view_count"))) null else c.getInt(c.getColumnIndexOrThrow("view_count")),
                    rating = if (c.isNull(c.getColumnIndexOrThrow("s_rating"))) null else c.getInt(c.getColumnIndexOrThrow("s_rating")),
                    upvoteRatio = if (c.isNull(c.getColumnIndexOrThrow("upvote_ratio"))) null else c.getInt(c.getColumnIndexOrThrow("upvote_ratio")),
                    upvoteDiff = if (c.isNull(c.getColumnIndexOrThrow("upvote_diff"))) null else c.getInt(c.getColumnIndexOrThrow("upvote_diff")),
                    upvoteViewRatio = if (c.isNull(c.getColumnIndexOrThrow("upvote_view_ratio"))) null else c.getInt(c.getColumnIndexOrThrow("upvote_view_ratio")),
                    stars = if (c.isNull(c.getColumnIndexOrThrow("stars"))) null else c.getInt(c.getColumnIndexOrThrow("stars")),
                    followersCount = if (c.isNull(c.getColumnIndexOrThrow("followers_count"))) null else c.getInt(c.getColumnIndexOrThrow("followers_count")),
                    dateUpdated = c.getString(c.getColumnIndexOrThrow("date_updated"))
                )
            } else null

            return Entry(
                id = id,
                link = link,
                title = title,
                description = description,
                thumbnail = thumbnail,
                author = author,
                album = album,
                language = language,
                page_rating_votes = votes,
                page_rating_visits = visits,
                page_rating = rating,
                date_created = dateCreated,
                date_published = datePublished,
                date_dead_since = dateDeadSince,
                age = age,
                status_code = statusCode,
                manual_status_code = manualStatusCode,
                bookmarked = bookmarked,
                source_id = sourceId,
                source_url = sourceUrl,
                tags = tags,
                socialData = socialData
            )
        }
    }
}

