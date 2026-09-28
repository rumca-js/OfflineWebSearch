package io.github.rumcajs.offlinewebsearch.workers

/**
 * Interface defining operations for updating a source and processing its entries.
 */
interface SourceUpdaterInterface {

    /**
     * Processes the source, fetching or reading data, extracting entries, filtering them
     * against active entry rules, and updating the database.
     *
     * @return Pair where first is a success flag, and second is a result/status message.
     */
    suspend fun process(): Pair<Boolean, String>
}
