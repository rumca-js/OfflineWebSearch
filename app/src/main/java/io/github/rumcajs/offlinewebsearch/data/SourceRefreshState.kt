package io.github.rumcajs.offlinewebsearch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global observable state for background source refresh operations.
 *
 * Screens that trigger a refresh update this state so that any other screen
 * (e.g. OptionsScreen) can observe progress without being coupled to the
 * screen that started the operation.
 *
 * [progress] is null when no refresh is running; otherwise it holds the
 * current [SourceRefreshProgress] with [total] and [done] counts.
 */
object SourceRefreshState {

    private val _progress = MutableStateFlow<SourceRefreshProgress?>(null)

    /** Emits the current refresh progress, or null when idle. */
    val progress: StateFlow<SourceRefreshProgress?> = _progress.asStateFlow()

    /** Call before starting a batch refresh with the number of sources to process. */
    fun start(total: Int) {
        _progress.value = SourceRefreshProgress(total = total, done = 0)
    }

    /** Call each time one source has finished processing. */
    fun increment() {
        val current = _progress.value ?: return
        _progress.value = current.copy(done = current.done + 1)
    }

    /** Call when the batch refresh is complete. Resets state to idle. */
    fun finish() {
        _progress.value = null
    }
}

/**
 * Snapshot of a running source refresh operation.
 *
 * @property total Total number of sources queued for refresh.
 * @property done  Number of sources that have been processed so far.
 */
data class SourceRefreshProgress(
    val total: Int,
    val done: Int
) {
    /** Fraction complete in [0f, 1f]. Returns 0 when [total] is zero. */
    val fraction: Float
        get() = if (total == 0) 0f else done.toFloat() / total.toFloat()
}
