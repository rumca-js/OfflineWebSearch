package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Source updater placeholder for Email sources.
 *
 * @param context Application context.
 * @param activeDatabaseState Current database state.
 * @param source The Email source to update.
 * @param force If true, forces the fetch even if the source was recently updated.
 */
class SourceUpdaterEmail(
    private val context: Context,
    private val activeDatabaseState: DatabaseState?,
    private val source: Source,
    private val force: Boolean = false
) : SourceUpdaterInterface {

    override suspend fun process(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!source.enabled) {
            return@withContext Pair(false, "Source is disabled")
        }
        // Placeholder implementation for email sources
        Pair(true, "Email source updater placeholder")
    }
}
