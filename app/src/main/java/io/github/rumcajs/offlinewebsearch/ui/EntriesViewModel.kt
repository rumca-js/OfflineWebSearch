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
import io.github.rumcajs.offlinewebsearch.data.DEFAULT_DATABASE_URL
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryVisitHistoryRepository
import io.github.rumcajs.offlinewebsearch.data.EntryOrderBy
import io.github.rumcajs.offlinewebsearch.data.repositories.SearchHistoryRepository
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel managing Entries state, search querying, filtering, pagination, and visit history.
 */
class EntriesViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var showSuggestions by mutableStateOf(false)
    var activeSearchQuery by mutableStateOf("")
    var searchHistory by mutableStateOf<List<String>>(emptyList())

    /** Currently active filter / order-by override selected via the filter button. */
    var activeFilter by mutableStateOf(EntrySearchFilter.None)
        private set

    /** Lazy list state preserved across navigation. */
    val listState = androidx.compose.foundation.lazy.LazyListState()

    /** Convenience accessors used by [fetchPage]. */
    val isFilterVisited: Boolean get() = activeFilter.filterByVisited
    val isFilterReadLater: Boolean get() = activeFilter.filterByReadLater
    val isFilterVisits: Boolean get() = activeFilter == EntrySearchFilter.ByVisits

    /**
     * Applies [filter] as the new active filter and re-fetches data if [context] is provided.
     * Selecting the already-active filter deactivates it (toggles back to None).
     */
    fun setFilter(context: Context? = null, filter: EntrySearchFilter) {
        activeFilter = if (activeFilter == filter) EntrySearchFilter.None else filter
        currentPage = 0
        if (context != null) {
            viewModelScope.launch {
                val config = AppConfigManager.config.first()
                fetchPage(
                    context,
                    config.activeDatabaseState,
                    activeFilter.orderByOverride() ?: config.dbconfig.orderBy,
                    config.dbconfig.effectiveLinksPerPage
                )
            }
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
    /** URL key of the database currently being viewed in DatabaseScreen. Defaults to [DEFAULT_DATABASE_URL]. */
    var selectedDatabaseUrl by mutableStateOf(DEFAULT_DATABASE_URL)
    var selectedDatabaseState by mutableStateOf<DatabaseState?>(null)

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
    private var currentOrderBy: EntryOrderBy? = null
    private var currentLinksPerPage: Int? = null
    private var isObservingConfig = false
    private var isObservingWorker = false

    // ──────────────────────────────────────────────────────────────────────────
    // Config & Worker watcher – reload when database, config, or source refresh changes
    // ──────────────────────────────────────────────────────────────────────────

    fun loadDataIfNeeded(context: Context) {
        if (!isObservingWorker) {
            isObservingWorker = true
            viewModelScope.launch {
                var wasRunning = false
                SourceRefreshWorker.progress.collect { progress ->
                    if (wasRunning && !progress.isRunning && progress.done > 0) {
                        refreshCurrentPage(context)
                    }
                    wasRunning = progress.isRunning
                }
            }
        }

        if (isObservingConfig) return
        isObservingConfig = true

        viewModelScope.launch {
            AppConfigManager.config.collect { config ->
                val activeLinksPerPage = config.dbconfig.effectiveLinksPerPage
                val dbChanged = config.activeDatabaseUrl != currentActiveDatabase
                val orderChanged = config.dbconfig.orderBy != currentOrderBy
                val linksPerPageChanged = activeLinksPerPage != currentLinksPerPage

                if (dbChanged || orderChanged || linksPerPageChanged) {
                    currentActiveDatabase = config.activeDatabaseUrl
                    currentOrderBy = config.dbconfig.orderBy
                    currentLinksPerPage = activeLinksPerPage
                    pageSize = activeLinksPerPage
                    currentPage = 0
                    val activeState = config.activeDatabaseState
                    if (activeState != null && activeState.isSQLite) {
                        val historyList = SearchHistoryRepository.getSearchHistory(context, activeState)
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
                    SearchHistoryRepository.insertSearch(context, config.activeDatabaseState, searchQuery)
                }
                fetchPage(
                    context,
                    config.activeDatabaseState,
                    activeFilter.orderByOverride() ?: config.dbconfig.orderBy,
                    config.dbconfig.effectiveLinksPerPage
                )
            }
        }
    }

    fun clearSearch() {
        searchQuery = ""
        currentPage = 0
    }

    /**
     * Resets screen states to their defaults (clears query, reset filters, selections,
     * pagination, and scroll state).
     */
    fun resetToDefaults() {
        searchQuery = ""
        activeSearchQuery = ""
        showSuggestions = false
        activeFilter = EntrySearchFilter.None
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
     * Updates the [bookmarked] flag for [entry] in [filteredData] in memory.
     *
     * Called when the user adds or removes an entry from Read Later on
     * [EntryDetailScreen] so that [EntryListScreen] immediately reflects the
     * correct bookmark icon and alpha without requiring a full page refresh.
     */
    fun updateEntryBookmarked(entry: Entry, bookmarked: Boolean) {
        filteredData = filteredData.map {
            if ((entry.id != null && it.id == entry.id) ||
                (!entry.link.isNullOrEmpty() && it.link == entry.link)
            ) {
                it.copy(bookmarked = bookmarked)
            } else {
                it
            }
        }
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
            EntryRepository.incrementVisit(
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
            val success = EntryRepository.deleteEntry(
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
            fetchPage(
                context,
                config.activeDatabaseState,
                activeFilter.orderByOverride() ?: config.dbconfig.orderBy,
                config.dbconfig.effectiveLinksPerPage
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core fetch
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun fetchPage(
        context: Context,
        activeDatabaseState: DatabaseState?,
        orderBy: EntryOrderBy,
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
        val page = EntryRepository.getEntries(
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
