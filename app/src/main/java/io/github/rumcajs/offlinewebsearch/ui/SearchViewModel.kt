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
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.data.EntryListRepository
import io.github.rumcajs.offlinewebsearch.data.OrderBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var showSuggestions by mutableStateOf(false)
    var activeSearchQuery by mutableStateOf("")
    var searchHistory by mutableStateOf<List<String>>(emptyList())

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
    private val pageSize = 20

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

    // ──────────────────────────────────────────────────────────────────────────
    // Config watcher – reload when database or order changes
    // ──────────────────────────────────────────────────────────────────────────

    fun loadDataIfNeeded(context: Context) {
        viewModelScope.launch {
            AppConfigManager.config.collect { config ->
                val dbChanged = config.activeDatabase != currentActiveDatabase
                val orderChanged = config.dbconfig.orderBy != currentOrderBy
                if (dbChanged || orderChanged) {
                    currentActiveDatabase = config.activeDatabase
                    currentOrderBy = config.dbconfig.orderBy
                    currentPage = 0
                    fetchPage(context, config.activeDatabaseState, config.dbconfig.orderBy)
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
                fetchPage(context, config.activeDatabaseState, config.dbconfig.orderBy)
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
            fetchPage(context, config.activeDatabaseState, config.dbconfig.orderBy)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core fetch
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun fetchPage(
        context: Context,
        activeDatabaseState: DatabaseState?,
        orderBy: OrderBy
    ) {
        isLoading = true
        val offset = currentPage * pageSize
        val count = EntryListRepository.countEntries(
            context = context,
            activeDatabaseState = activeDatabaseState,
            searchQuery = activeSearchQuery,
            orderBy = orderBy
        )
        val page = EntryListRepository.loadEntriesPage(
            context = context,
            activeDatabaseState = activeDatabaseState,
            searchQuery = activeSearchQuery,
            orderBy = orderBy,
            offset = offset,
            pageSize = pageSize
        )
        totalSearchResults = count
        filteredData = page
        isLoading = false
    }
}
