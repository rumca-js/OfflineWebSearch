package com.example.index.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Place(
    val title: String? = null,
    val description: String? = null,
    val link: String? = null,
    val tags: List<String>? = null,
    val page_rating_votes: Int? = 0,
    val page_rating: Int? = 0,
    val thumbnail: String? = null,
    val date_created: String? = null,
    val date_published: String? = null,
    val age: Int? = 0
)

private val jsonConfig = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

object PlaceRepository {
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

    suspend fun loadAllPlaces(context: Context, activeDatabaseUrl: String? = null): List<Place> = withContext(Dispatchers.IO) {
        val allPlaces = mutableListOf<Place>()

        if (activeDatabaseUrl == null) {
            // Load from assets if no external database is active
            assets.forEach { fileName ->
                try {
                    context.assets.open(fileName).bufferedReader().use { reader ->
                        val jsonString = reader.readText()
                        val places: List<Place> = jsonConfig.decodeFromString(jsonString)
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
                        val cursor = db.rawQuery("SELECT title, description, thumbnail, link, page_rating_votes, page_rating, date_created, date_published, age FROM linkdatamodel", null)
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
                                val age = it.getInt(it.getColumnIndexOrThrow("age"))
                                allPlaces.add(
                                    Place(
                                        title = title,
                                        description = description,
                                        thumbnail = thumbnail,
                                        link = link,
                                        page_rating_votes = votes,
                                        page_rating = rating,
                                        date_created = dateCreated,
                                        date_published = datePublished,
                                        age = age
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
                            val places: List<Place> = jsonConfig.decodeFromString(jsonString)
                            allPlaces.addAll(places)
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
