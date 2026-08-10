package io.github.rumcajs.offlinewebsearch.ui

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var showSuggestions by mutableStateOf(false)
    var activeSearchQuery by mutableStateOf("")
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var allEntries by mutableStateOf<List<io.github.rumcajs.offlinewebsearch.data.Entry>>(emptyList())
    var isLoading by mutableStateOf(true)
        private set

    var selectedEntry by mutableStateOf<io.github.rumcajs.offlinewebsearch.data.Entry?>(null)
    var previewUrl by mutableStateOf<String?>(null)
    var selectedDatabaseUrl by mutableStateOf<String?>(null)
    var selectedDatabaseState by mutableStateOf<io.github.rumcajs.offlinewebsearch.data.DatabaseState?>(null)
    var selectedSource by mutableStateOf<io.github.rumcajs.offlinewebsearch.data.Source?>(null)

    var currentPage by mutableStateOf(0)
    private val pageSize = 20

    val isSearchButtonEnabled by derivedStateOf {
        searchQuery != activeSearchQuery
    }

    private var currentActiveDatabase: String? = null
    private var currentOrderBy: io.github.rumcajs.offlinewebsearch.data.OrderBy? = null

    fun loadDataIfNeeded(context: Context) {
        viewModelScope.launch {
            _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collect { config ->
                if (config.activeDatabase != currentActiveDatabase || config.dbconfig.orderBy != currentOrderBy || allEntries.isEmpty()) {
                    currentActiveDatabase = config.activeDatabase
                    currentOrderBy = config.dbconfig.orderBy
                    isLoading = true
                    currentPage = 0
                    val unsortedPlaces =
                        _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.EntryListRepository.loadAllEntries(
                            context,
                            config.activeDatabaseState,
                        )
                    allEntries = when (config.dbconfig.orderBy) {
                        _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.OrderBy.PAGE_RATING_VOTES -> unsortedPlaces.sortedByDescending { it.page_rating_votes ?: 0 }
                        _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.OrderBy.DATE_CREATED -> unsortedPlaces.sortedByDescending { it.date_created ?: "" }
                        _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.OrderBy.DATE_PUBLISHED -> unsortedPlaces.sortedByDescending { it.date_published ?: "" }
                    }
                    isLoading = false
                    performSearch() // Refresh filtered data
                }
            }
        }
    }

    fun performSearch() {
        showSuggestions = false
        activeSearchQuery = searchQuery
        currentPage = 0
        if (searchQuery.isNotBlank()) {
            val currentHistory = searchHistory.toMutableList()
            currentHistory.remove(searchQuery)
            currentHistory.add(0, searchQuery)
            if (currentHistory.size > 100) {
                searchHistory = currentHistory.take(100)
            } else {
                searchHistory = currentHistory
            }
        }
    }

    fun nextPage() {
        if ((currentPage + 1) * pageSize < totalSearchResults) {
            currentPage++
        }
    }

    fun previousPage() {
        if (currentPage > 0) {
            currentPage--
        }
    }

    fun clearSearch() {
        searchQuery = ""
        currentPage = 0
    }

    private val allSearchResults by derivedStateOf {
        if (activeSearchQuery.isBlank()) {
            allEntries
        } else {
            val query = activeSearchQuery.trim()
            val likeRegex = Regex("^(title|link|description|tag|tags)\\s+LIKE\\s+['\"]?%?([^%'\"]+)%?['\"]?$", RegexOption.IGNORE_CASE)
            val match = likeRegex.find(query)

            if (match != null) {
                val field = match.groupValues[1].lowercase()
                val searchTerm = match.groupValues[2].trim()

                allEntries.filter { entry ->
                    when (field) {
                        "title" -> entry.title?.contains(searchTerm, ignoreCase = true) == true
                        "link" -> entry.link?.contains(searchTerm, ignoreCase = true) == true
                        "description" -> entry.description?.contains(searchTerm, ignoreCase = true) == true
                        "tag", "tags" -> entry.tags?.any { it.contains(searchTerm, ignoreCase = true) } == true
                        else -> false
                    }
                }
            } else {
                allEntries.filter { entry ->
                    entry.title?.contains(query, ignoreCase = true) == true ||
                    entry.description?.contains(query, ignoreCase = true) == true ||
                    entry.link?.contains(query, ignoreCase = true) == true ||
                    entry.tags?.any { it.contains(query, ignoreCase = true) } == true
                }
            }
        }
    }

    val totalSearchResults by derivedStateOf {
        allSearchResults.size
    }

    val totalPages by derivedStateOf {
        if (totalSearchResults == 0) 1 else kotlin.math.ceil(totalSearchResults.toDouble() / pageSize).toInt()
    }

    val suggestions by derivedStateOf {
        if (!showSuggestions || searchQuery.isEmpty()) {
            emptyList()
        } else {
            searchHistory.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredData by derivedStateOf {
        val start = currentPage * pageSize
        allSearchResults.drop(start).take(pageSize)
    }

    fun updateEntryInMemory(updatedEntry: io.github.rumcajs.offlinewebsearch.data.Entry) {
        allEntries = allEntries.map { entry ->
            if (entry.id != null && entry.id == updatedEntry.id) {
                updatedEntry
            } else if (entry.link != null && entry.link == updatedEntry.link) {
                updatedEntry
            } else if (entry == selectedEntry) {
                updatedEntry
            } else {
                entry
            }
        }
    }
}
