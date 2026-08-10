package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Source(
    val id: Long? = null,
    val enabled: Boolean = true,
    val url: String = "",
    val title: String = ""
)

object SourceRepository {

    suspend fun loadSources(context: Context, activeDatabaseState: DatabaseState?): List<Source> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<Source>()
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db") {
            return@withContext sources
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext sources

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val sqlText = "SELECT id, enabled, url, title FROM sourcedatamodel"
            val cursor = db.rawQuery(sqlText, null)
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("id"))
                    val enabledVal = it.getInt(it.getColumnIndexOrThrow("enabled"))
                    val url = it.getString(it.getColumnIndexOrThrow("url")) ?: ""
                    val title = it.getString(it.getColumnIndexOrThrow("title")) ?: ""

                    sources.add(
                        Source(
                            id = id,
                            enabled = enabledVal == 1,
                            url = url,
                            title = title
                        )
                    )
                }
            }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sources
    }
}
