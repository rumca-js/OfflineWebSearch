package io.github.rumcajs.offlinewebsearch.ui

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOrder
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceWithOperationalData
import io.github.rumcajs.offlinewebsearch.ui.components.FilterOption
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Filter key constants for [SourcesViewModel] dropdown options. */
const val SOURCE_FILTER_KEY_BY_URL = "by_url"
const val SOURCE_FILTER_KEY_BY_TITLE = "by_title"
const val SOURCE_FILTER_KEY_BY_FETCH_TIME = "by_fetch_time"

val SOURCE_FILTER_OPTIONS = listOf(
    FilterOption(
        key = SOURCE_FILTER_KEY_BY_URL,
        label = "By Url",
        icon = Icons.Default.SortByAlpha
    ),
    FilterOption(
        key = SOURCE_FILTER_KEY_BY_TITLE,
        label = "By Title",
        icon = Icons.Default.SortByAlpha
    ),
    FilterOption(
        key = SOURCE_FILTER_KEY_BY_FETCH_TIME,
        label = "By Fetch Time",
        icon = Icons.Default.DateRange
    )
)

/**
 * ViewModel managing Sources state, querying, filtering, and background refresh progress.
 */
class SourcesViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var activeSearchQuery by mutableStateOf("")
    var sourceOrder by mutableStateOf(SourceOrder.ByUrl)
    var sourceItems by mutableStateOf<List<SourceWithOperationalData>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isRefreshingAll by mutableStateOf(false)
        private set
    var hasOutdatedSources by mutableStateOf(false)
        private set

    var selectedSource by mutableStateOf<Source?>(null)

    val listState = LazyListState()

    val isSearchButtonEnabled by derivedStateOf {
        searchQuery != activeSearchQuery
    }

    val activeFilterKey: String? by derivedStateOf {
        when (sourceOrder) {
            SourceOrder.ByUrl -> SOURCE_FILTER_KEY_BY_URL
            SourceOrder.ByTitle -> SOURCE_FILTER_KEY_BY_TITLE
            SourceOrder.ByFetchTime -> SOURCE_FILTER_KEY_BY_FETCH_TIME
        }
    }

    val filteredSources by derivedStateOf {
        val base = if (activeSearchQuery.isBlank()) {
            sourceItems
        } else {
            val query = activeSearchQuery.trim().lowercase()
            sourceItems.filter { item ->
                item.source.title.lowercase().contains(query) ||
                    item.source.url.lowercase().contains(query)
            }
        }
        when (sourceOrder) {
            SourceOrder.ByUrl -> base.sortedWith(compareBy<SourceWithOperationalData> { it.source.url.lowercase() }.thenBy { it.source.title.lowercase() })
            SourceOrder.ByTitle -> base.sortedWith(compareBy<SourceWithOperationalData> { it.source.title.lowercase() }.thenBy { it.source.url.lowercase() })
            SourceOrder.ByFetchTime -> base.sortedWith(
                compareBy<SourceWithOperationalData> { it.operationalData?.date_fetched ?: "" }
                    .thenBy { it.source.url.lowercase() }
            )
        }
    }

    private var currentActiveDatabase: String? = null
    private var isObserving = false

    fun loadDataIfNeeded(context: Context) {
        if (isObserving) return
        isObserving = true

        // Observe config for database switching
        viewModelScope.launch {
            AppConfigManager.config.collect { config ->
                if (config.activeDatabaseUrl != currentActiveDatabase) {
                    currentActiveDatabase = config.activeDatabaseUrl
                    loadSources(context)
                }
            }
        }

        // Observe SourceRefreshWorker progress
        viewModelScope.launch {
            var wasRunning = false
            SourceRefreshWorker.progress.collect { progress ->
                isRefreshingAll = progress.isRunning
                if (wasRunning && !progress.isRunning) {
                    loadSources(context)
                }
                wasRunning = progress.isRunning
            }
        }
    }

    fun loadSources(context: Context) {
        viewModelScope.launch {
            isLoading = true
            val config = AppConfigManager.config.first()
            val activeDbState = config.activeDatabaseState
            sourceItems = SourceRepository.getAllSourcesWithOperationalData(context, activeDbState, sourceOrder)
            hasOutdatedSources = if (!SourceRefreshWorker.progress.value.isRunning) {
                SourceRepository.hasOutdatedSources(context, activeDbState)
            } else {
                false
            }
            isLoading = false
        }
    }

    fun performSearch() {
        activeSearchQuery = searchQuery
    }

    fun clearSearch() {
        searchQuery = ""
    }

    fun setFilter(option: FilterOption) {
        sourceOrder = when (option.key) {
            SOURCE_FILTER_KEY_BY_URL -> SourceOrder.ByUrl
            SOURCE_FILTER_KEY_BY_TITLE ->
                if (sourceOrder == SourceOrder.ByTitle) SourceOrder.ByUrl else SourceOrder.ByTitle
            SOURCE_FILTER_KEY_BY_FETCH_TIME ->
                if (sourceOrder == SourceOrder.ByFetchTime) SourceOrder.ByUrl else SourceOrder.ByFetchTime
            else -> SourceOrder.ByUrl
        }
    }

    fun refreshAll(context: Context, onMessage: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val config = AppConfigManager.config.first()
            val activeDbState = config.activeDatabaseState
            if (config.networkConfig.disabled) {
                onMessage?.invoke("Network operations are disabled")
                return@launch
            }
            if (activeDbState == null || activeDbState.isReadOnly || !activeDbState.isSQLite) {
                onMessage?.invoke("Active database is read-only or not writable")
                return@launch
            }
            if (sourceItems.isEmpty()) {
                onMessage?.invoke("No sources to fetch")
                return@launch
            }
            SourceRefreshWorker.enqueueOutdatedSources(context, activeDbState)
        }
    }

    fun deleteSource(
        context: Context,
        sourceId: Long,
        deleteEntries: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val config = AppConfigManager.config.first()
            val activeDbState = config.activeDatabaseState
            val (success, err) = SourceRepository.deleteSource(
                context,
                activeDbState,
                sourceId,
                deleteEntries = deleteEntries
            )
            if (success) {
                loadSources(context)
            }
            onResult(success, err)
        }
    }
}
