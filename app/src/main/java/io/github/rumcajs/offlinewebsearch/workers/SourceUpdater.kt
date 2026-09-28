package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.util.EntryRuleUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url

/**
 * Delegating updater and factory for [Source] instances.
 * Selects the appropriate [SourceUpdaterInterface] implementation based on [Source.source_type].
 *
 * @param context Application context.
 * @param activeDatabaseState Current database state.
 * @param source The source to be updated.
 * @param urlFactory Optional factory to create [Url] instances (useful for testing and mocking).
 * @param force If true, skips the outdated/fetch-period check and forces a fetch.
 */
class SourceUpdater(
    private val context: Context,
    private val activeDatabaseState: DatabaseState?,
    private val source: Source,
    private val urlFactory: ((String) -> Url)? = null,
    private val force: Boolean = false
) : SourceUpdaterInterface {

    private val delegate: SourceUpdaterInterface = createUpdater(
        context = context,
        activeDatabaseState = activeDatabaseState,
        source = source,
        urlFactory = urlFactory,
        force = force
    )

    /**
     * Executes the update sequence by delegating to the appropriate [SourceUpdaterInterface] implementation.
     */
    override suspend fun process(): Pair<Boolean, String> = delegate.process()

    /**
     * Alias for [process] to maintain backward compatibility.
     */
    suspend fun update(): Pair<Boolean, String> = process()

    /**
     * Filters [entries] against active [rules] using [SourceUpdaterRss.filterEntries].
     */
    suspend fun filterEntries(entries: List<Entry>, rules: List<EntryRule>): List<Entry> {
        return if (delegate is SourceUpdaterRss) {
            delegate.filterEntries(entries, rules)
        } else {
            SourceUpdaterRss(context, activeDatabaseState, source, urlFactory, force).filterEntries(entries, rules)
        }
    }

    companion object {
        /**
         * Creates an appropriate [SourceUpdaterInterface] instance based on [Source.source_type].
         *
         * @param context Application context.
         * @param activeDatabaseState Current database state.
         * @param source The source to update.
         * @param urlFactory Optional factory to supply custom [Url] instances.
         * @param force If true, forces the source update even if not outdated.
         * @return An instance of [SourceUpdaterInterface] ([SourceUpdaterEmail] or [SourceUpdaterRss]).
         */
        fun createUpdater(
            context: Context,
            activeDatabaseState: DatabaseState?,
            source: Source,
            urlFactory: ((String) -> Url)? = null,
            force: Boolean = false
        ): SourceUpdaterInterface {
            val sourceType = source.source_type?.trim() ?: ""
            return when {
                sourceType.equals(SourceRepository.SOURCE_TYPE_EMAIL, ignoreCase = true) -> {
                    SourceUpdaterEmail(context, activeDatabaseState, source, force)
                }
                else -> {
                    SourceUpdaterRss(context, activeDatabaseState, source, urlFactory, force)
                }
            }
        }

        /**
         * Matches [link] against [pattern] using regular expression matching by delegating to [EntryRuleUtils.matchesRuleUrl].
         * Supports comma-separated patterns, standard regex, wildcard, and substring matching.
         *
         * @param link The entry URL string to test.
         * @param pattern Regular expression pattern(s) from [EntryRule.trigger_rule_url].
         * @return True if the link matches any pattern.
         */
        fun matchesRuleUrl(link: String, pattern: String): Boolean = EntryRuleUtils.matchesRuleUrl(link, pattern)

        /**
         * Convenience static method to update a source using [SourceUpdater].
         *
         * @param context Application context.
         * @param activeDatabaseState Current database state.
         * @param source The source to update.
         * @param urlFactory Optional factory to supply custom [Url] instances.
         * @param force If true, forces the source update even if not outdated.
         * @return Pair(success, resultMessage).
         */
        suspend fun updateSource(
            context: Context,
            activeDatabaseState: DatabaseState?,
            source: Source,
            urlFactory: ((String) -> Url)? = null,
            force: Boolean = false
        ): Pair<Boolean, String> {
            return SourceUpdater(context, activeDatabaseState, source, urlFactory, force).process()
        }
    }
}
