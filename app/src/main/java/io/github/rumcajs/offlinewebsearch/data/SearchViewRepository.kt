package io.github.rumcajs.offlinewebsearch.data

import android.database.sqlite.SQLiteDatabase
import java.io.File

data class SearchViewRepository(
    val name: String? = null,
    val isDefault: Boolean = false,
    val orderByStr: String? = null
) {
    val orderBy: OrderBy?
        get() {
            val firstOrder = orderByStr?.split(",")?.firstOrNull()?.lowercase()?.trim()
            return when (firstOrder) {
                "page_rating_votes","-page_rating_votes" -> OrderBy.PAGE_RATING_VOTES
                "date_created", "-date_created" -> OrderBy.DATE_CREATED
                "date_published","-date_published" -> OrderBy.DATE_PUBLISHED
                else -> null
            }
        }

    companion object {
        fun readDefaultFromDatabase(file: File): SearchViewRepository? {
            if (!file.exists()) return null
            try {
                val db = SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                return db.use { sqliteDb ->
                    val tableCursor = sqliteDb.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='searchview'",
                        null
                    )
                    val tableExists = tableCursor.use { c -> c.moveToFirst() }
                    if (!tableExists) return null

                    // Query for the row where `default` boolean is true (1)
                    val cursor = sqliteDb.rawQuery(
                        "SELECT name, `default`, order_by FROM searchview WHERE `default` = 1 LIMIT 1",
                        null
                    )
                    cursor.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex("name")
                            val defaultIndex = c.getColumnIndex("default")
                            val orderByIndex = c.getColumnIndex("order_by")

                            val name = if (nameIndex != -1 && !c.isNull(nameIndex)) c.getString(nameIndex) else null
                            val isDefault = if (defaultIndex != -1 && !c.isNull(defaultIndex)) c.getInt(defaultIndex) == 1 else false
                            val orderByStr = if (orderByIndex != -1 && !c.isNull(orderByIndex)) c.getString(orderByIndex) else null

                            SearchViewRepository(
                                name = name,
                                isDefault = isDefault,
                                orderByStr = orderByStr
                            )
                        } else {
                            // Fallback: read first row if no explicit default row found
                            val fallbackCursor = sqliteDb.rawQuery("SELECT name, `default`, order_by FROM searchview LIMIT 1", null)
                            fallbackCursor.use { fc ->
                                if (fc.moveToFirst()) {
                                    val nameIndex = fc.getColumnIndex("name")
                                    val defaultIndex = fc.getColumnIndex("default")
                                    val orderByIndex = fc.getColumnIndex("order_by")

                                    val name = if (nameIndex != -1 && !fc.isNull(nameIndex)) fc.getString(nameIndex) else null
                                    val isDefault = if (defaultIndex != -1 && !fc.isNull(defaultIndex)) fc.getInt(defaultIndex) == 1 else false
                                    val orderByStr = if (orderByIndex != -1 && !fc.isNull(orderByIndex)) fc.getString(orderByIndex) else null

                                    SearchViewRepository(
                                        name = name,
                                        isDefault = isDefault,
                                        orderByStr = orderByStr
                                    )
                                } else null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}
