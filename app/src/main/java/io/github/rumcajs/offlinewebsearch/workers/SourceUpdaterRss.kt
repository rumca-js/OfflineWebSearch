package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRulesRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.util.EntryRuleUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Source updater responsible for updating an RSS/Atom [Source] from its remote feed.
 *
 * @param context Application context.
 * @param activeDatabaseState Current database state.
 * @param source The RSS source to be updated.
 * @param urlFactory Optional factory to create [Url] instances (useful for testing and mocking).
 * @param force If true, skips the outdated/fetch-period check and forces a fetch.
 */
class SourceUpdaterRss(
    private val context: Context,
    private val activeDatabaseState: DatabaseState?,
    private val source: Source,
    private val urlFactory: ((String) -> Url)? = null,
    private val force: Boolean = false
) : SourceUpdaterInterface {

    /**
     * Executes the update sequence for the RSS source:
     * 1. Validates source readiness and fetch requirement via [SourceRepository.isFetchRequired] (unless [force] is true).
     * 2. Creates [Url] object and fetches feed response.
     * 3. Calls [SourceRepository.updateSourceMetadata] to update title and favicon.
     * 4. Obtains [Entry] list from [Url.getEntries].
     * 5. Filters entries using active [EntryRule]s using [EntryRuleUtils].
     * 6. Calls [SourceRepository.insertSourceEntries] to insert the filtered entries into `linkdatamodel`.
     * 7. Updates operational fetch data via [SourceRepository.updateFetchData].
     *
     * @return Pair of success flag and result message.
     */
    override suspend fun process(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (source.url.isBlank()) {
            return@withContext Pair(false, "Source URL is empty")
        }
        if (!source.enabled) {
            return@withContext Pair(false, "Source is disabled")
        }
        if (!force && !SourceRepository.isFetchRequired(context, activeDatabaseState, source)) {
            return@withContext Pair(false, "Source was fetched recently (less than 1 hour ago)")
        }

        val urlObj = urlFactory?.invoke(source.url) ?: Url(source.url)
        val response = urlObj.getResponse()
        if (!response.isValid) {
            AppLoggingRepository.error(
                context,
                activeDatabaseState,
                "Failed to fetch source: ${source.url}",
                "Status code:${response.statusCode} Error:${response.error}"
            )
        }

        // 1. Update Source metadata in SourceRepository
        SourceRepository.updateSourceMetadata(context, activeDatabaseState, urlObj)

        // 2. Obtain entries from Url object
        val rawEntries = try {
            urlObj.getEntries()
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(
                context,
                activeDatabaseState,
                "Exception obtaining entries in $functionName for ${urlObj.url}",
                e.message
            )
            emptyList()
        }

        // 3. Filter entries using EntryRules
        val rules = EntryRulesRepository.getRules(context, activeDatabaseState, enabledOnly = true)
        val filteredEntries = filterEntries(rawEntries, rules)

        // 4. Call SourceRepository to add entries
        val (insertOk, insertedCount) = SourceRepository.insertSourceEntries(
            context = context,
            activeDatabaseState = activeDatabaseState,
            entries = filteredEntries,
            source = source
        )

        // 5. Update fetch operational data
        SourceRepository.updateFetchData(context, activeDatabaseState, urlObj, source)

        if (insertOk) {
            Pair(true, "Successfully updated source with $insertedCount entries")
        } else {
            Pair(false, "Failed to insert entries for source")
        }
    }

    /**
     * Filters [entries] against active [rules] using [EntryRuleUtils].
     * For each entry matching enabled rules:
     * - The matching rules' hit counters are incremented.
     * - If any matching rule has `block == true`, the entry is discarded.
     *
     * @param entries List of candidate entries.
     * @param rules List of active entry rules.
     * @return Filtered list of allowed entries.
     */
    suspend fun filterEntries(entries: List<Entry>, rules: List<EntryRule>): List<Entry> {
        val activeUrlRules = rules.filter { it.enabled && it.trigger_rule_url.isNotBlank() }
        if (activeUrlRules.isEmpty()) {
            return entries
        }

        val allowed = mutableListOf<Entry>()
        for (entry in entries) {
            val link = entry.link ?: ""
            if (link.isBlank()) {
                allowed.add(entry)
                continue
            }

            val matchingRules = EntryRuleUtils.getMatchingRulesForLink(link, activeUrlRules)
            for (rule in matchingRules) {
                rule.id?.let { ruleId ->
                    EntryRulesRepository.incrementTriggerHits(context, activeDatabaseState, ruleId)
                }
            }

            if (!matchingRules.any { it.block }) {
                allowed.add(entry)
            }
        }
        return allowed
    }
}
