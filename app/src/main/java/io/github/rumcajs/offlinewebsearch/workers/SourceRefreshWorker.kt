package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalDataRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOrder
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background worker responsible for refreshing sources sequentially.
 *
 * Implements a queue accepting new sources or database states to refresh.
 * Progress is exposed via [progress] StateFlow for UI feedback.
 */
object SourceRefreshWorker {

    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _progress = MutableStateFlow(WorkerProgress())
    val progress: StateFlow<WorkerProgress> = _progress.asStateFlow()

    private sealed class RefreshTask {
        data class SingleSource(
            val context: Context,
            val dbState: DatabaseState,
            val source: Source,
            val force: Boolean = false,
            val onFinished: ((Boolean, String?) -> Unit)? = null
        ) : RefreshTask()
        data class OutdatedSources(
            val context: Context,
            val dbState: DatabaseState,
            val refetchErrors: Boolean = false,
            val onFinished: ((Int) -> Unit)? = null
        ) : RefreshTask()
        data class BatchSources(
            val context: Context,
            val dbState: DatabaseState,
            val sources: List<Source>,
            val force: Boolean = false,
            val onFinished: ((Int) -> Unit)? = null
        ) : RefreshTask()
    }

    private val taskChannel = Channel<RefreshTask>(Channel.UNLIMITED)

    init {
        workerScope.launch {
            for (task in taskChannel) {
                mutex.withLock {
                    processTask(task)
                }
            }
        }
    }

    /**
     * Enqueues a single source for background refresh.
     *
     * @param context Application context.
     * @param dbState Target database state.
     * @param source Source to refresh.
     * @param force If true, forces the refresh even if the source was fetched recently.
     * @param onFinished Callback invoked upon completion with success flag and message.
     */
    fun enqueueSource(
        context: Context,
        dbState: DatabaseState,
        source: Source,
        force: Boolean = false,
        onFinished: ((Boolean, String?) -> Unit)? = null
    ) {
        taskChannel.trySend(RefreshTask.SingleSource(context.applicationContext, dbState, source, force, onFinished))
    }

    /**
     * Enqueues a check and refresh of outdated sources for the given database state.
     *
     * @param context Application context.
     * @param dbState Target database state.
     * @param refetchErrors If true, also forces refresh for sources with consecutive errors.
     * @param onFinished Callback invoked upon completion with total count of successfully fetched sources.
     */
    fun enqueueOutdatedSources(
        context: Context,
        dbState: DatabaseState,
        refetchErrors: Boolean = false,
        onFinished: ((Int) -> Unit)? = null
    ) {
        taskChannel.trySend(RefreshTask.OutdatedSources(context.applicationContext, dbState, refetchErrors, onFinished))
    }

    /**
     * Dispatches the processing of an incoming [RefreshTask] to its specialized handler.
     */
    private suspend fun processTask(task: RefreshTask) {
        when (task) {
            is RefreshTask.SingleSource -> processSingleSource(task)
            is RefreshTask.BatchSources -> processBatchSources(task)
            is RefreshTask.OutdatedSources -> processOutdatedSources(task)
        }
    }

    /**
     * Checks whether the target database is writable and eligible for source refresh operations.
     */
    private fun isDatabaseWritable(dbState: DatabaseState): Boolean {
        val config = AppConfigManager.config.value
        return !config.networkConfig.disabled && !dbState.isReadOnly && dbState.isSQLite && config.activeDatabaseUrl == dbState.url
    }

    /**
     * Processes a [RefreshTask.SingleSource] task.
     */
    private suspend fun processSingleSource(task: RefreshTask.SingleSource) {
        if (!isDatabaseWritable(task.dbState)) {
            task.onFinished?.invoke(true, "")
            return
        }

        _progress.value = WorkerProgress(total = 1, done = 0, isRunning = true, currentItem = task.source.title)
        val (success, msg) = SourceUpdater.updateSource(task.context, task.dbState, task.source, force = task.force)
        _progress.value = WorkerProgress(total = 1, done = 1, isRunning = false, currentItem = null)
        task.onFinished?.invoke(success, msg)
    }

    /**
     * Processes a [RefreshTask.BatchSources] task.
     */
    private suspend fun processBatchSources(task: RefreshTask.BatchSources) {
        if (!isDatabaseWritable(task.dbState)) {
            task.onFinished?.invoke(0)
            return
        }

        val sourcesToFetch = task.sources.map { it to task.force }
        val fetchedCount = executeSourcesRefresh(task.context, task.dbState, sourcesToFetch)
        task.onFinished?.invoke(fetchedCount)
    }

    /**
     * Processes a [RefreshTask.OutdatedSources] task.
     */
    private suspend fun processOutdatedSources(task: RefreshTask.OutdatedSources) {
        if (!isDatabaseWritable(task.dbState)) {
            task.onFinished?.invoke(0)
            return
        }

        val sourcesToFetch = getOutdatedSourcesToProcess(task.context, task.dbState, task.refetchErrors)
        if (sourcesToFetch.isEmpty()) {
            task.onFinished?.invoke(0)
            return
        }

        val fetchedCount = executeSourcesRefresh(task.context, task.dbState, sourcesToFetch)
        task.onFinished?.invoke(fetchedCount)
    }

    /**
     * Queries and filters sources that are outdated or eligible for error refetching.
     *
     * @return List of [Pair] containing each source and its force flag.
     */
    private suspend fun getOutdatedSourcesToProcess(
        context: Context,
        dbState: DatabaseState,
        refetchErrors: Boolean
    ): List<Pair<Source, Boolean>> {
        val allSources = SourceRepository.getAllSourcesWithOperationalData(context, dbState, SourceOrder.ByFetchTime)
            .filter { it.source.enabled && it.source.url.isNotBlank() }

        return allSources.mapNotNull { item ->
            val isOutdated = SourceOperationalDataRepository.isFetchOutdated(
                item.operationalData?.date_fetched,
                item.source.fetch_period
            )
            val hasErrors = (item.operationalData?.consecutive_errors ?: 0) > 0
            if (isOutdated || (refetchErrors && hasErrors)) {
                val force = refetchErrors && hasErrors
                item.source to force
            } else {
                null
            }
        }
    }

    /**
     * Sequentially executes source updates for a list of sources, updating worker progress.
     *
     * @param context Application context.
     * @param dbState Target database state.
     * @param sourcesWithForce List of sources with individual force flags.
     * @return Total count of successfully fetched sources.
     */
    private suspend fun executeSourcesRefresh(
        context: Context,
        dbState: DatabaseState,
        sourcesWithForce: List<Pair<Source, Boolean>>
    ): Int {
        val total = sourcesWithForce.size
        if (total == 0) return 0

        _progress.value = WorkerProgress(total = total, done = 0, isRunning = true)
        var fetchedCount = 0

        for ((src, force) in sourcesWithForce) {
            _progress.update { it.copy(currentItem = src.title) }

            val currentConfig = AppConfigManager.config.value
            if (currentConfig.activeDatabaseUrl != dbState.url) {
                continue
            }

            val (success, _) = SourceUpdater.updateSource(
                context = context,
                activeDatabaseState = dbState,
                source = src,
                force = force
            )
            if (success) fetchedCount++
            _progress.update { it.copy(done = it.done + 1) }
        }

        _progress.value = WorkerProgress(total = total, done = total, isRunning = false)
        return fetchedCount
    }
}
