package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.Source
import io.github.rumcajs.offlinewebsearch.data.SourceRepository
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
            val onFinished: ((Boolean, String?) -> Unit)? = null
        ) : RefreshTask()
        data class OutdatedSources(val context: Context, val dbState: DatabaseState, val onFinished: ((Int) -> Unit)? = null) : RefreshTask()
        data class BatchSources(val context: Context, val dbState: DatabaseState, val sources: List<Source>, val onFinished: ((Int) -> Unit)? = null) : RefreshTask()
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
     * Enqueues a list of sources for sequential background refresh.
     */
    fun enqueueSources(
        context: Context,
        dbState: DatabaseState,
        sources: List<Source>,
        onFinished: ((Int) -> Unit)? = null
    ) {
        val enabledSources = sources.filter { it.enabled && it.url.isNotBlank() }
        if (enabledSources.isEmpty()) {
            onFinished?.invoke(0)
            return
        }
        taskChannel.trySend(RefreshTask.BatchSources(context.applicationContext, dbState, enabledSources, onFinished))
    }

    /**
     * Enqueues a single source for background refresh.
     */
    fun enqueueSource(
        context: Context,
        dbState: DatabaseState,
        source: Source,
        onFinished: ((Boolean, String?) -> Unit)? = null
    ) {
        taskChannel.trySend(RefreshTask.SingleSource(context.applicationContext, dbState, source, onFinished))
    }

    /**
     * Enqueues a check and refresh of outdated sources for the given database state.
     */
    fun enqueueOutdatedSources(
        context: Context,
        dbState: DatabaseState,
        onFinished: ((Int) -> Unit)? = null
    ) {
        taskChannel.trySend(RefreshTask.OutdatedSources(context.applicationContext, dbState, onFinished))
    }

    private suspend fun processTask(task: RefreshTask) {
        when (task) {
            is RefreshTask.SingleSource -> {
                _progress.value = WorkerProgress(total = 1, done = 0, isRunning = true, currentItem = task.source.title)
                val (success, msg) = SourceRepository.updateSource(task.context, task.dbState, task.source)
                _progress.value = WorkerProgress(total = 1, done = 1, isRunning = false, currentItem = null)
                task.onFinished?.invoke(success, msg)
            }
            is RefreshTask.BatchSources -> {
                val total = task.sources.size
                _progress.value = WorkerProgress(total = total, done = 0, isRunning = true)
                var fetchedCount = 0
                for (src in task.sources) {
                    _progress.update { it.copy(currentItem = src.title) }
                    val (success, _) = SourceRepository.updateSource(
                        context = task.context,
                        activeDatabaseState = task.dbState,
                        source = src
                    )
                    if (success) fetchedCount++
                    _progress.update { it.copy(done = it.done + 1) }
                }
                _progress.value = WorkerProgress(total = total, done = total, isRunning = false)
                task.onFinished?.invoke(fetchedCount)
            }
            is RefreshTask.OutdatedSources -> {
                val refreshed = SourceRepository.fetchOutdatedSources(task.context, task.dbState)
                task.onFinished?.invoke(refreshed)
            }
        }
    }
}
