package io.github.rumcajs.offlinewebsearch.data.builders

import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus

/**
 * Shared interface for builders that create, download, unpack, and populate
 * local SQLite databases from various sources (local files, remote URLs, assets).
 *
 * Each builder provides explicit lifecycle functions corresponding to the
 * states defined in [DatabaseStatus].
 */
interface DatabaseBuilder {
    val url: String
    val currentStatus: DatabaseStatus

    /**
     * Handles the [DatabaseStatus.INIT] state.
     * Prepares initial state, registers configurations, and performs pre-checks.
     */
    suspend fun onInit()

    /**
     * Handles the [DatabaseStatus.DOWNLOADING] state.
     * Fetches database data or archive from the internet when applicable.
     */
    suspend fun onDownloading()

    /**
     * Handles the [DatabaseStatus.UNPACKING] state.
     * Extracts database files from compressed archives (.zip / .db.zip).
     */
    suspend fun onUnpacking()

    /**
     * Handles the [DatabaseStatus.POPULATING_TABLE] state.
     * Reads JSON entry records into an empty SQLite database created from `table.db`.
     */
    suspend fun onPopulatingTable()

    /**
     * Handles the [DatabaseStatus.READY] state.
     * Finalizes database files, inspects metadata, and updates application configuration.
     *
     * @return The finalized [DatabaseState] marked as [DatabaseStatus.READY].
     */
    suspend fun onReady(): DatabaseState

    /**
     * Handles the [DatabaseStatus.FAILED] state.
     * Cleans up temporary files and records the failure error message.
     *
     * @param error The exception that caused the failure.
     * @return The [DatabaseState] marked as [DatabaseStatus.FAILED].
     */
    suspend fun onFailed(error: Throwable): DatabaseState

    /**
     * Executes the complete database build lifecycle sequentially across each state.
     *
     * @return The final [DatabaseState].
     */
    suspend fun build(): DatabaseState
}
