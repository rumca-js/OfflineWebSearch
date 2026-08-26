package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DatabaseStats(
    val linkDataModelCount: Long? = null,
    val searchViewCount: Long? = null,
    val sourceDataModelCount: Long? = null,
    val configurationEntryCount: Long? = null,
    val entryTransitionHistoryCount: Long? = null,
    val entryVisitHistoryCount: Long? = null,
    val socialDataCount: Long? = null
)

object DatabaseStatsRepository {

    suspend fun getStats(context: Context, state: DatabaseState): DatabaseStats = withContext(Dispatchers.IO) {
        if (state.extension != ".db") {
            return@withContext DatabaseStats()
        }

        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) {
            return@withContext DatabaseStats()
        }

        var linkDataCount: Long? = null
        var searchViewCount: Long? = null
        var sourceDataCount: Long? = null
        var configCount: Long? = null
        var transitionHistoryCount: Long? = null
        var visitHistoryCount: Long? = null
        var socialDataCount: Long? = null

        try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            db.use {
                linkDataCount = EntryRepository.getRowCount(it)
                searchViewCount = it.getTableCount("searchview")
                sourceDataCount = SourceRepository.getRowCount(it)
                configCount = it.getTableCount("configurationentry")
                transitionHistoryCount = EntryTransitionHistoryRepository.getRowCount(it)
                visitHistoryCount = EntryVisitHistoryRepository.getRowCount(it)
                socialDataCount = SocialDataRepository.getRowCount(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        DatabaseStats(
            linkDataModelCount = linkDataCount,
            searchViewCount = searchViewCount,
            sourceDataModelCount = sourceDataCount,
            configurationEntryCount = configCount,
            entryTransitionHistoryCount = transitionHistoryCount,
            entryVisitHistoryCount = visitHistoryCount,
            socialDataCount = socialDataCount
        )
    }

    /**
     * Retrieves row counts for all known repositories from [RepositoryList].
     *
     * @param context Application context.
     * @param state Target database state.
     * @return Map of repository to row count (or null if unavailable).
     */
    suspend fun getRepositoryCounts(
        context: Context,
        state: DatabaseState
    ): Map<RepositoryInterface, Long?> = withContext(Dispatchers.IO) {
        if (state.extension != ".db") {
            return@withContext emptyMap()
        }

        val file = File(context.filesDir, state.localFileName)
        if (!file.exists()) {
            return@withContext emptyMap()
        }

        val counts = mutableMapOf<RepositoryInterface, Long?>()
        try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            db.use { database ->
                for (repo in RepositoryList.repositories) {
                    counts[repo] = repo.getRowCount(database)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        counts
    }

    private fun SQLiteDatabase.getTableCount(tableName: String): Long? {
        return try {
            val cursor = rawQuery("SELECT COUNT(*) FROM $tableName", null)
            cursor.use { c ->
                if (c.moveToFirst()) {
                    c.getLong(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

