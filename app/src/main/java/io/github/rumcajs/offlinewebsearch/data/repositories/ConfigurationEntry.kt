package io.github.rumcajs.offlinewebsearch.data.repositories

import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.ViewStyle
import java.io.File

data class ConfigurationEntry(
    val instanceTitle: String? = null,
    val instanceDescription: String? = null,

    val showIcons: Boolean? = null,
    val displayType: String? = null,
    val linksPerPage: Int? = null,
    val trackUserSearches: Boolean? = null,
    val trackUserNavigation: Boolean? = null,
    // Capability flags
    val enableKeywordSupport: Boolean? = null,
    val enableDomainSupport: Boolean? = null,
    val enableFileSupport: Boolean? = null,
    val enableLinkArchiving: Boolean? = null,
    val enableSourceArchiving: Boolean? = null,
    val enableCrawling: Boolean? = null,
    val enableSocialData: Boolean? = null,
    // Link acceptance policy
    val acceptDeadLinks: Boolean? = null,
    val acceptIpLinks: Boolean? = null,
    val acceptDomainLinks: Boolean? = null,
    val acceptNonDomainLinks: Boolean? = null,
    val acceptUnknownLinks: Boolean? = null,
    val acceptOnionLinks: Boolean? = null,
    val acceptSameHashes: Boolean? = null,
    // Visual / display settings
    val highlightBookmarks: Boolean? = null,
    val entriesVisitAlpha: Float? = null,
    val entriesDeadAlpha: Float? = null
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
        /** Reads a single boolean column by name, returning null if the column is absent or NULL. */
        private fun readBool(c: android.database.Cursor, col: String): Boolean? {
            val idx = c.getColumnIndex(col)
            return if (idx != -1 && !c.isNull(idx)) c.getInt(idx) == 1 else null
        }

        /** Reads a single int column by name, returning null if the column is absent or NULL. */
        private fun readInt(c: android.database.Cursor, col: String): Int? {
            val idx = c.getColumnIndex(col)
            return if (idx != -1 && !c.isNull(idx)) c.getInt(idx) else null
        }

        /** Reads a single string column by name, returning null if the column is absent or NULL. */
        private fun readString(c: android.database.Cursor, col: String): String? {
            val idx = c.getColumnIndex(col)
            return if (idx != -1 && !c.isNull(idx)) c.getString(idx) else null
        }

        /** Reads a single float column by name, returning null if the column is absent or NULL. */
        private fun readFloat(c: android.database.Cursor, col: String): Float? {
            val idx = c.getColumnIndex(col)
            return if (idx != -1 && !c.isNull(idx)) c.getFloat(idx) else null
        }

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
                                ConfigurationEntry(
                                    instanceTitle = readString(c, "instance_title"),
                                    instanceDescription = readString(c, "instance_description"),
                                    showIcons = readBool(c, "show_icons"),
                                    displayType = readString(c, "display_type"),
                                    linksPerPage = readInt(c, "links_per_page"),
                                    trackUserSearches = readBool(c, "track_user_searches"),
                                    trackUserNavigation = readBool(c, "track_user_navigation"),
                                    enableKeywordSupport = readBool(c, "enable_keyword_support"),
                                    enableDomainSupport = readBool(c, "enable_domain_support"),
                                    enableFileSupport = readBool(c, "enable_file_support"),
                                    enableLinkArchiving = readBool(c, "enable_link_archiving"),
                                    enableSourceArchiving = readBool(c, "enable_source_archiving"),
                                    enableCrawling = readBool(c, "enable_crawling"),
                                    enableSocialData = readBool(c, "enable_social_data"),
                                    acceptDeadLinks = readBool(c, "accept_dead_links"),
                                    acceptIpLinks = readBool(c, "accept_ip_links"),
                                    acceptDomainLinks = readBool(c, "accept_domain_links"),
                                    acceptNonDomainLinks = readBool(c, "accept_non_domain_links"),
                                    acceptUnknownLinks = readBool(c, "accept_unknown_links"),
                                    acceptOnionLinks = readBool(c, "accept_onion_links"),
                                    acceptSameHashes = readBool(c, "accept_same_hashes"),
                                    highlightBookmarks = readBool(c, "highlight_bookmarks"),
                                    entriesVisitAlpha = readFloat(c, "entries_visit_alpha"),
                                    entriesDeadAlpha = readFloat(c, "entries_dead_alpha")
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