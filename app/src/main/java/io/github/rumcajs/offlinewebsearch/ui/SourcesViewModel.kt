package io.github.rumcajs.offlinewebsearch.ui

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
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

/**
 * Sentinel used by [SourcesViewModel] so the very first config emission always triggers
 * an initial data load, even when [activeDatabaseUrl] starts as null (default database).
 */
private const val SENTINEL_NO_DATABASE = "\$__no_database__"


/**
 * ViewModel managing Sources state, querying, filtering, and background refresh progress.
 */
class SourcesViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var activeSearchQuery by mutableStateOf("")
    var sourceOrder by mutableStateOf(SourceOrder.ByUrl)
    var filteredSources by mutableStateOf<List<SourceWithOperationalData>>(emptyList())
        private set
    val sourceItems: List<SourceWithOperationalData>
        get() = filteredSources
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
        orderToKey(sourceOrder)
    }

    /**
     * Sentinel value used to ensure the very first config emission always triggers
     * a data load — even when [activeDatabaseUrl] is null (default database).
     */
    private var currentActiveDatabase: String? = SENTINEL_NO_DATABASE
    private var isObserving = false

    fun loadDataIfNeeded(context: Context) {
        if (isObserving) return
        isObserving = true

        // Observe config for database switching.
        // currentActiveDatabase starts at SENTINEL_NO_DATABASE so the first
        // emission always triggers loadSources(), even when activeDatabaseUrl is null.
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
            filteredSources = SourceRepository.getAllSourcesWithOperationalData(
                context = context,
                activeDatabaseState = activeDbState,
                orderBy = sourceOrder,
                searchQuery = activeSearchQuery
            )
            hasOutdatedSources = if (!SourceRefreshWorker.progress.value.isRunning) {
                SourceRepository.hasOutdatedSources(context, activeDbState)
            } else {
                false
            }
            isLoading = false
        }
    }

    fun performSearch(context: Context? = null) {
        activeSearchQuery = searchQuery
        if (context != null) {
            loadSources(context)
        }
    }

    fun clearSearch(context: Context? = null) {
        searchQuery = ""
        if (activeSearchQuery.isNotEmpty()) {
            activeSearchQuery = ""
            if (context != null) {
                loadSources(context)
            }
        }
    }

    fun setFilter(option: FilterOption, context: Context? = null) {
        val newOrder = keyToOrder(option.key)
        sourceOrder = if (sourceOrder == newOrder) SourceOrder.ByUrl else newOrder
        if (context != null) {
            loadSources(context)
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
