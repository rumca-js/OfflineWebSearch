package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.collections.forEach

@Serializable
data class Entry(
    val link: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    val album: String? = null,
    val language: String? = null,
    val tags: List<String>? = null,
    val page_rating_votes: Int? = 0,
    val page_rating: Int? = 0,
    val thumbnail: String? = null,
    val date_created: String? = null,
    val date_published: String? = null,
    val date_dead_since: String? = null,
    val age: Int? = 0,
    val status_code: Int? = 0,
    val manual_status_code: Int? = 0,
    val bookmarked: Boolean? = false
)

private val jsonConfig = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

object EntryListRepository {
    private val assets = listOf(
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

    suspend fun loadAllEntries(context: Context, activeDatabaseUrl: String? = null): List<io.github.rumcajs.offlinewebsearch.data.Entry> = withContext(Dispatchers.IO) {
        val allPlaces = mutableListOf<io.github.rumcajs.offlinewebsearch.data.Entry>()

        if (activeDatabaseUrl == null) {
            // Load from assets if no external database is active
            assets.forEach { fileName ->
                try {
                    context.assets.open(fileName).bufferedReader().use { reader ->
                        val jsonString = reader.readText()
                        val places: List<io.github.rumcajs.offlinewebsearch.data.Entry> = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.jsonConfig.decodeFromString(jsonString)
                        allPlaces.addAll(places)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Load ONLY the active database from local storage
            val isSqlite = activeDatabaseUrl.endsWith(".db")
            val extension = if (isSqlite) ".db" else ".json"
            val fileName = "db_${activeDatabaseUrl.hashCode()}$extension"
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                if (isSqlite) {
                    try {
                        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                        val cursor = db.rawQuery("""
                            SELECT 
                                l.title, l.description, l.thumbnail, l.link, l.page_rating_votes, l.page_rating, l.date_created, l.date_published, l.date_dead_since,
                                l.age, l.author, l.album, l.language, l.status_code, l.manual_status_code, l.bookmarked,
                                t.tag 
                            FROM linkdatamodel l 
                            LEFT JOIN entrycompactedtags t ON l.id = t.entry_id
                        """.trimIndent(), null)
                        cursor.use {
                            while (it.moveToNext()) {
                                val title = it.getString(it.getColumnIndexOrThrow("title"))
                                val description = it.getString(it.getColumnIndexOrThrow("description"))
                                val thumbnail = it.getString(it.getColumnIndexOrThrow("thumbnail"))
                                val link = it.getString(it.getColumnIndexOrThrow("link"))
                                val votes = it.getInt(it.getColumnIndexOrThrow("page_rating_votes"))
                                val rating = it.getInt(it.getColumnIndexOrThrow("page_rating"))
                                val dateCreated = it.getString(it.getColumnIndexOrThrow("date_created"))
                                val datePublished = it.getString(it.getColumnIndexOrThrow("date_published"))
                                val dateDeadSince = it.getString(it.getColumnIndexOrThrow("date_dead_since"))
                                val author = it.getString(it.getColumnIndexOrThrow("author"))
                                val album = it.getString(it.getColumnIndexOrThrow("album"))
                                val language = it.getString(it.getColumnIndexOrThrow("language"))
                                val age = it.getInt(it.getColumnIndexOrThrow("age"))
                                val statusCode = it.getInt(it.getColumnIndexOrThrow("status_code"))
                                val manualStatusCode = it.getInt(it.getColumnIndexOrThrow("manual_status_code"))
                                val bookmarked = it.getInt(it.getColumnIndexOrThrow("bookmarked")) == 1
                                val tagString = it.getString(it.getColumnIndexOrThrow("tag"))
                                val tags = tagString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

                                allPlaces.add(
                                    _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.Entry(
                                        link = link,
                                        title = title,
                                        description = description,
                                        thumbnail = thumbnail,
                                        author = author,
                                        album = album,
                                        language = language,
                                        page_rating_votes = votes,
                                        page_rating = rating,
                                        date_created = dateCreated,
                                        date_published = datePublished,
                                        date_dead_since = dateDeadSince,
                                        age = age,
                                        status_code = statusCode,
                                        manual_status_code = manualStatusCode,
                                        bookmarked = bookmarked,
                                        tags = tags
                                    )
                                )
                            }
                        }
                        db.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    try {
                        file.bufferedReader().use { reader ->
                            val jsonString = reader.readText()
                            val entries: List<io.github.rumcajs.offlinewebsearch.data.Entry> = _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.jsonConfig.decodeFromString(jsonString)
                            allPlaces.addAll(entries)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        allPlaces
    }
}
