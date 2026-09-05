package io.github.rumcajs.offlinewebsearch.data.repositories

/**
 * Provides a central list of all known repositories implementing [RepositoryInterface].
 */
object RepositoryList {

    /**
     * List of all known [RepositoryInterface] repositories in the application.
     */
    val repositories: List<RepositoryInterface> by lazy {
        listOf(
            EntryRepository,
            SourceRepository,
            EntryTransitionHistoryRepository,
            EntryVisitHistoryRepository,
            ReadLaterRepository,
            SearchHistoryRepository,
            SocialDataRepository,
            SourceOperationalDataRepository,
            EntryCompactedTagsRepository,
            AppLoggingRepository
        )
    }
}