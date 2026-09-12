package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import java.util.Date

interface Page {
    fun getTitle(): String?
    fun getDescription(): String?
    fun getLanguage(): String?
    /**
     * Should be in format Year-Month-Day Hour:Minutes
     */
    fun getDatePublished(): Date?
    fun getEntries(): List<Entry>
    // TODO refactor. It should be string, not List
    fun getThumbnails(): List<String>
    fun getFeeds(): List<String> = emptyList()

    /**
     * Returns a hash of the full page content (meta + body combined), or null if not implemented.
     */
    fun getHash(): ByteArray? = null

    /**
     * Returns a hash of the page meta/header section only, or null if not implemented.
     */
    fun getMetaHash(): ByteArray? = null

    /**
     * Returns a hash of the page body/content section only, or null if not implemented.
     */
    fun getBodyHash(): ByteArray? = null
}
