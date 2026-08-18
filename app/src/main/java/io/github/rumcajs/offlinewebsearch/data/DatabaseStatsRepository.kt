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
    val entryVisitHistoryCount: Long? = null
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

        try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            db.use {
                linkDataCount = getTableCount(it, "linkdatamodel")
                searchViewCount = getTableCount(it, "searchview")
                sourceDataCount = getTableCount(it, "sourcedatamodel")
                configCount = getTableCount(it, "configurationentry")
                transitionHistoryCount = getTableCount(it, "entrytransitionhistory")
                visitHistoryCount = getTableCount(it, "entryvisithistory")
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
            entryVisitHistoryCount = visitHistoryCount
        )
    }

    private fun getTableCount(db: SQLiteDatabase, tableName: String): Long? {
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
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
