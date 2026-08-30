package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import android.widget.Toast
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus
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
 * Background worker responsible for updating / downloading databases sequentially.
 *
 * Multiple databases are never downloaded in parallel; incoming requests are queued
 * and processed in FIFO order. Progress and current downloading database URL are exposed
 * through [progress] StateFlow.
 */
object DatabaseUpdateWorker {

    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _progress = MutableStateFlow(WorkerProgress())
    val progress: StateFlow<WorkerProgress> = _progress.asStateFlow()

    private val taskChannel = Channel<Pair<Context, String>>(Channel.UNLIMITED)

    init {
        workerScope.launch {
            for ((context, url) in taskChannel) {
                mutex.withLock {
                    processDatabaseDownload(context, url)
                }
            }
        }
    }

    /**
     * Enqueues a database URL for sequential background download / refresh.
     * Returns true if successfully queued, false if it's already downloading or unpacking.
     */
    fun enqueueDatabase(context: Context, url: String): Boolean {
        val currentDbState = AppConfigManager.config.value.databases[url]
        if (currentDbState?.status == DatabaseStatus.DOWNLOADING || currentDbState?.status == DatabaseStatus.UNPACKING) {
            Toast.makeText(context, "Database is already downloading or unpacking", Toast.LENGTH_SHORT).show()
            return false
        }

        // Add to configuration immediately so UI shows status reactively
        AppConfigManager.addDatabase(url)
        _progress.update { it.copy(total = it.total + 1, isRunning = true) }
        taskChannel.trySend(Pair(context.applicationContext, url))
        return true
    }

    private suspend fun processDatabaseDownload(context: Context, url: String) {
        _progress.update { it.copy(currentItem = url, isRunning = true) }
        try {
            AppConfigManager.saveDatabaseFromInternet(context, url, oldUrl = url)
        } catch (_: Exception) {
            // Status is updated to FAILED inside saveDatabaseFromInternet
        } finally {
            _progress.update {
                val newDone = it.done + 1
                val stillRunning = newDone < it.total
                it.copy(
                    done = newDone,
                    isRunning = stillRunning,
                    currentItem = if (stillRunning) it.currentItem else null
                )
            }
        }
    }
}
