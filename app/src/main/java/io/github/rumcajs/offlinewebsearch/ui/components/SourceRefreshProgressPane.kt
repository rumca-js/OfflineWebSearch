package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.workers.SourceRefreshWorker
import io.github.rumcajs.offlinewebsearch.workers.WorkerProgress

/**
 * Pane displaying the progress of [SourceRefreshWorker].
 *
 * Automatically observes [SourceRefreshWorker.progress] StateFlow and displays
 * a linear progress indicator with status details when sources are being refreshed.
 *
 * @param modifier Optional [Modifier] for configuring the layout.
 */
@Composable
fun SourceRefreshProgressPane(
    modifier: Modifier = Modifier
) {
    val progress by SourceRefreshWorker.progress.collectAsState()
    SourceRefreshProgressContent(
        progress = progress,
        modifier = modifier
    )
}

/**
 * Stateless content renderer for [SourceRefreshWorker] progress.
 *
 * @param progress The current [WorkerProgress] snapshot.
 * @param modifier Optional [Modifier] for layout adjustments.
 */
@Composable
fun SourceRefreshProgressContent(
    progress: WorkerProgress,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = progress.isRunning,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            val label = progress.currentItem?.let { " ($it)" } ?: ""
            Text(
                text = "Refreshing sources: ${progress.done} / ${progress.total}$label",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
