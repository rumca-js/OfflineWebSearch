package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.Entry
import java.util.Date

interface Page {
    fun getTitle(): String?
    fun getDescription(): String?
    /**
     * Should be in format Year-Month-Day Hour:Minutes
     */
    fun getDatePublished(): Date?
    fun getEntries(): List<Entry>
    // TODO refactor. It should be string
    fun getThumbnails(): List<String>
}
