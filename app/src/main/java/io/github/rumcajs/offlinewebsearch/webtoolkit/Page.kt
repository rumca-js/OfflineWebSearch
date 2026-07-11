package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.Entry

interface Page {
    fun getTitle(): String?
    fun getDescription(): String?
    fun getDatePublished(): String?
    fun getEntries(): List<Entry>
}
