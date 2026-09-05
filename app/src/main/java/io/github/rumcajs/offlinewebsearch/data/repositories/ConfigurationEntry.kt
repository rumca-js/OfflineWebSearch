package io.github.rumcajs.offlinewebsearch.data.repositories

import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.ViewStyle
import java.io.File

data class ConfigurationEntry(
    val showIcons: Boolean? = null,
    val displayType: String? = null,
    val linksPerPage: Int? = null,
    val trackUserSearches: Boolean? = null,
    val trackUserNavigation: Boolean? = null
) {
    val isShowIcons: Boolean
        get() = showIcons == true

    val viewStyle: ViewStyle?
        get() = when (displayType?.lowercase()) {
            "gallery" -> ViewStyle.GALLERY
            "search-engine", "search_engine", "searchengine", "search engine" -> ViewStyle.SEARCH_ENGINE
            "standard", "news" -> ViewStyle.STANDARD
            else -> null
        }

    companion object {
        fun readFromDatabase(file: File): ConfigurationEntry? {
            if (!file.exists()) return null
            try {
                val db = SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                return db.use { sqliteDb ->
                    val tableCursor = sqliteDb.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='configurationentry'",
                        null
                    )
                    val tableExists = tableCursor.use { c -> c.moveToFirst() }
                    if (tableExists) {
                        val cursor = sqliteDb.rawQuery("SELECT * FROM configurationentry LIMIT 1", null)
                        cursor.use { c ->
                            if (c.moveToFirst()) {
                                val showIconsIndex = c.getColumnIndex("show_icons")
                                val showIcons = if (showIconsIndex != -1 && !c.isNull(showIconsIndex)) {
                                    c.getInt(showIconsIndex) == 1
                                } else null

                                val displayTypeIndex = c.getColumnIndex("display_type")
                                val displayType = if (displayTypeIndex != -1 && !c.isNull(displayTypeIndex)) {
                                    c.getString(displayTypeIndex)
                                } else null

                                val linksPerPageIndex = c.getColumnIndex("links_per_page")
                                val linksPerPage = if (linksPerPageIndex != -1 && !c.isNull(linksPerPageIndex)) {
                                    c.getInt(linksPerPageIndex)
                                } else null

                                val trackUserSearchesIndex = c.getColumnIndex("track_user_searches")
                                val trackUserSearches = if (trackUserSearchesIndex != -1 && !c.isNull(trackUserSearchesIndex)) {
                                    c.getInt(trackUserSearchesIndex) == 1
                                } else null

                                val trackUserNavigationIndex = c.getColumnIndex("track_user_navigation")
                                val trackUserNavigation = if (trackUserNavigationIndex != -1 && !c.isNull(trackUserNavigationIndex)) {
                                    c.getInt(trackUserNavigationIndex) == 1
                                } else null

                                ConfigurationEntry(
                                    showIcons = showIcons,
                                    displayType = displayType,
                                    linksPerPage = linksPerPage,
                                    trackUserSearches = trackUserSearches,
                                    trackUserNavigation = trackUserNavigation
                                )
                            } else {
                                ConfigurationEntry()
                            }
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}