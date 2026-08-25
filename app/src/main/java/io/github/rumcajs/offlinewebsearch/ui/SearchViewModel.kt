package io.github.rumcajs.offlinewebsearch.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.data.EntryRepository
import io.github.rumcajs.offlinewebsearch.data.EntryVisitHistoryRepository
import io.github.rumcajs.offlinewebsearch.data.OrderBy
import io.github.rumcajs.offlinewebsearch.data.SearchHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var showSuggestions by mutableStateOf(false)
    var activeSearchQuery by mutableStateOf("")
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var isFilterVisited by mutableStateOf(false)
    var isFilterReadLater by mutableStateOf(false)

    fun toggleVisitedFilter() {
        isFilterVisited = !isFilterVisited
        if (isFilterVisited) {
            isFilterReadLater = false
        }
    }

    fun toggleReadLaterFilter() {
        isFilterReadLater = !isFilterReadLater
        if (isFilterReadLater) {
            isFilterVisited = false
        }
    }

    var isLoading by mutableStateOf(true)
        private set

    /** The current page of entries fetched from the database. */
    var filteredData by mutableStateOf<List<Entry>>(emptyList())
        private set

    /** Total number of matching entries (for pagination). */
    var totalSearchResults by mutableIntStateOf(0)
        private set

    var selectedEntry by mutableStateOf<Entry?>(null)
    var previewUrl by mutableStateOf<String?>(null)
    var selectedDatabaseUrl by mutableStateOf<String?>(null)
    var selectedDatabaseState by mutableStateOf<DatabaseState?>(null)
    var selectedSource by mutableStateOf<io.github.rumcajs.offlinewebsearch.data.Source?>(null)

    var currentPage by mutableIntStateOf(0)
    var pageSize by mutableIntStateOf(DatabaseConfiguration.MIN_LINKS_PER_PAGE)
        private set

    val isSearchButtonEnabled by derivedStateOf {
        searchQuery != activeSearchQuery
    }

    val totalPages by derivedStateOf {
        if (totalSearchResults == 0) 1
        else kotlin.math.ceil(totalSearchResults.toDouble() / pageSize).toInt()
    }

    val suggestions by derivedStateOf {
        if (!showSuggestions || searchQuery.isEmpty()) emptyList()
        else searchHistory.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    private var currentActiveDatabase: String? = null
    private var currentOrderBy: OrderBy? = null
    private var currentLinksPerPage: Int? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Config watcher – reload when database, order, or linksPerPage changes
    // ──────────────────────────────────────────────────────────────────────────

    fun loadDataIfNeeded(context: Context) {
        viewModelScope.launch {
            AppConfigManager.config.collect { config ->
                val activeLinksPerPage = config.dbconfig.effectiveLinksPerPage
                val dbChanged = config.activeDatabase != currentActiveDatabase
                val orderChanged = config.dbconfig.orderBy != currentOrderBy
                val linksPerPageChanged = activeLinksPerPage != currentLinksPerPage

                if (dbChanged || orderChanged || linksPerPageChanged) {
                    currentActiveDatabase = config.activeDatabase
                    currentOrderBy = config.dbconfig.orderBy
                    currentLinksPerPage = activeLinksPerPage
                    pageSize = activeLinksPerPage
                    currentPage = 0
                    val activeState = config.activeDatabaseState
                    if (activeState != null && activeState.extension == ".db") {
                        val historyList = SearchHistoryRepository.loadSearchHistory(context, activeState)
                        searchHistory = historyList.map { it.search_query }
                    }
                    fetchPage(context, config.activeDatabaseState, config.dbconfig.orderBy, activeLinksPerPage)
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Search
    // ──────────────────────────────────────────────────────────────────────────

    fun performSearch(context: Context? = null) {
        showSuggestions = false
        activeSearchQuery = searchQuery
        currentPage = 0
        if (searchQuery.isNotBlank()) {
            val history = searchHistory.toMutableList()
            history.remove(searchQuery)
            history.add(0, searchQuery)
            searchHistory = if (history.size > 100) history.take(100) else history
        }
        if (context != null) {
            viewModelScope.launch {
                val config = AppConfigManager.config.first()
                if (searchQuery.isNotBlank() && config.dbconfig.trackUserSearches) {
                    SearchHistoryRepository.recordSearch(context, config.activeDatabaseState, searchQuery)
                }
                fetchPage(context, config.activeDatabaseState, config.dbconfig.orderBy, config.dbconfig.effectiveLinksPerPage)
            }
        }
    }

    fun clearSearch() {
        searchQuery = ""
        currentPage = 0
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pagination
    // ──────────────────────────────────────────────────────────────────────────

    /** Re-fetches the current page (e.g. after an add or edit). */
    fun refreshPage(context: Context) {
        refreshCurrentPage(context)
    }

    /**
     * Increments the visit count for [entry] in memory and in SQLite DB (if editable).
     */
    fun recordVisit(context: Context, entry: Entry) {
        val updatedVisits = (entry.page_rating_visits ?: 0) + 1
        val updatedEntry = entry.copy(page_rating_visits = updatedVisits)
        selectedEntry = updatedEntry

        filteredData = filteredData.map {
            if ((entry.id != null && it.id == entry.id) || (!entry.link.isNullOrEmpty() && it.link == entry.link)) {
                it.copy(page_rating_visits = updatedVisits)
            } else {
                it
            }
        }

        viewModelScope.launch {
            val config = AppConfigManager.config.first()
            EntryRepository.incrementVisitInSql(
                context = context,
                activeDatabaseState = config.activeDatabaseState,
                id = entry.id,
                link = entry.link
            )
            if (entry.id != null && config.dbconfig.trackUserNavigation) {
                EntryVisitHistoryRepository.recordVisit(
                    context = context,
                    activeDatabaseState = config.activeDatabaseState,
                    entryId = entry.id
                )
            }
        }
    }

    /**
     * Deletes [entry] from the active database and refreshes the list.
     * Calls [onResult] with true on success, false on failure.
     */
    fun deleteEntry(context: Context, entry: Entry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val config = AppConfigManager.config.first()
            val dbState = config.activeDatabaseState
            if (dbState == null) {
                onResult(false)
                return@launch
            }
            val success = EntryRepository.deleteEntryFromSql(
                context = context,
                activeDatabaseState = dbState,
                id = entry.id,
                link = entry.link
            )
            if (success) {
                refreshCurrentPage(context)
            }
            onResult(success)
        }
    }

    fun nextPage(context: Context) {
        if (currentPage + 1 < totalPages) {
            currentPage++
            refreshCurrentPage(context)
        }
    }

    fun previousPage(context: Context) {
        if (currentPage > 0) {
            currentPage--
            refreshCurrentPage(context)
        }
    }

    private fun refreshCurrentPage(context: Context) {
        viewModelScope.launch {
            val config = AppConfigManager.config.first()
            fetchPage(context, config.activeDatabaseState, config.dbconfig.orderBy, config.dbconfig.effectiveLinksPerPage)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core fetch
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun fetchPage(
        context: Context,
        activeDatabaseState: DatabaseState?,
        orderBy: OrderBy,
        effectivePageSize: Int = pageSize
    ) {
        isLoading = true
        pageSize = effectivePageSize
        val offset = currentPage * effectivePageSize
        val count = EntryRepository.countEntries(
            context = context,
            activeDatabaseState = activeDatabaseState,
            searchQuery = activeSearchQuery,
            orderBy = orderBy,
            filterByVisited = isFilterVisited,
            filterByReadLater = isFilterReadLater
        )
        val page = EntryRepository.loadEntriesPage(
            context = context,
            activeDatabaseState = activeDatabaseState,
            searchQuery = activeSearchQuery,
            orderBy = orderBy,
            offset = offset,
            pageSize = effectivePageSize,
            filterByVisited = isFilterVisited,
            filterByReadLater = isFilterReadLater
        )
        totalSearchResults = count
        filteredData = page
        isLoading = false
    }
}
