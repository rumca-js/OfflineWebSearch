package com.example.index.ui

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.index.data.Place
import com.example.index.data.PlaceRepository.loadAllPlaces
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var showSuggestions by mutableStateOf(false)
    var activeSearchQuery by mutableStateOf("")
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var allPlaces by mutableStateOf<List<Place>>(emptyList())
    var isLoading by mutableStateOf(true)
        private set

    var selectedPlace by mutableStateOf<Place?>(null)

    var currentPage by mutableStateOf(0)
    private val pageSize = 20

    val isSearchButtonEnabled by derivedStateOf {
        searchQuery != activeSearchQuery
    }

    private var currentActiveDatabase: String? = null
    private var currentOrderBy: com.example.index.data.OrderBy? = null

    fun loadDataIfNeeded(context: Context) {
        viewModelScope.launch {
            com.example.index.data.AppConfigManager.config.collect { config ->
                if (config.activeDatabase != currentActiveDatabase || config.orderBy != currentOrderBy || allPlaces.isEmpty()) {
                    currentActiveDatabase = config.activeDatabase
                    currentOrderBy = config.orderBy
                    isLoading = true
                    currentPage = 0
                    val unsortedPlaces = loadAllPlaces(context, config.activeDatabase)
                    allPlaces = when (config.orderBy) {
                        com.example.index.data.OrderBy.PAGE_RATING_VOTES -> unsortedPlaces.sortedByDescending { it.page_rating_votes ?: 0 }
                        com.example.index.data.OrderBy.DATE_CREATED -> unsortedPlaces.sortedByDescending { it.date_created ?: "" }
                        com.example.index.data.OrderBy.DATE_PUBLISHED -> unsortedPlaces.sortedByDescending { it.date_published ?: "" }
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
            allPlaces
        } else {
            allPlaces.filter { place ->
                place.title?.contains(activeSearchQuery, ignoreCase = true) == true ||
                place.description?.contains(activeSearchQuery, ignoreCase = true) == true ||
                place.link?.contains(activeSearchQuery, ignoreCase = true) == true ||
                place.tags?.any { it.contains(activeSearchQuery, ignoreCase = true) } == true
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
}
