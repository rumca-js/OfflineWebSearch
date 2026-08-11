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
    val title: String = "",
    val thumbnail: String = ""
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
            val sqlText = "SELECT id, enabled, url, title, thumbnail FROM sourcedatamodel"
            val cursor = db.rawQuery(sqlText, null)
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("id"))
                    val enabledVal = it.getInt(it.getColumnIndexOrThrow("enabled"))
                    val url = it.getString(it.getColumnIndexOrThrow("url")) ?: ""
                    val title = it.getString(it.getColumnIndexOrThrow("title")) ?: ""
                    val thumbnail = it.getString(it.getColumnIndexOrThrow("thumbnail")) ?: ""

                    sources.add(
                        Source(
                            id = id,
                            enabled = enabledVal == 1,
                            url = url,
                            title = title,
                            thumbnail = thumbnail
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

    suspend fun updateSource(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long,
        title: String,
        url: String,
        enabled: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext false
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val values = android.content.ContentValues().apply {
                put("title", title)
                put("url", url)
                put("enabled", if (enabled) 1 else 0)
            }
            val rows = db.update("sourcedatamodel", values, "id = ?", arrayOf(id.toString()))
            db.close()
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteSource(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || activeDatabaseState.extension != ".db" || activeDatabaseState.isReadOnly) {
            return@withContext false
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext false

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val rows = db.delete("sourcedatamodel", "id = ?", arrayOf(id.toString()))
            db.close()
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
