package io.github.rumcajs.offlinewebsearch.workers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Common progress representation for background workers.
 *
 * @property total Total number of items queued for processing.
 * @property done  Number of items that have been processed so far.
 * @property isRunning True if worker is currently running.
 * @property currentItem Description or identifier of the current item being processed.
 */
data class WorkerProgress(
    val total: Int = 0,
    val done: Int = 0,
    val isRunning: Boolean = false,
    val currentItem: String? = null
) {
    /** Fraction complete in [0f, 1f]. Returns 0 when [total] is zero. */
    val fraction: Float
        get() = if (total == 0) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
