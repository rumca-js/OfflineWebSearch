package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRulesRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.PatternSyntaxException

/**
 * Updater class responsible for updating a [Source] from its remote feed.
 *
 * It creates a [Url] object, calls [SourceRepository] to update source metadata,
 * obtains [Entry] items from the [Url] object, filters them using active [EntryRule]s
 * matching the `trigger_rule_url` regular expression, and inserts the filtered entries
 * through [SourceRepository].
 *
 * @param context Application context.
 * @param activeDatabaseState Current database state.
 * @param source The source to be updated.
 * @param urlFactory Optional factory to create [Url] instances (useful for testing and mocking).
 */
class SourceUpdater(
    private val context: Context,
    private val activeDatabaseState: DatabaseState?,
    private val source: Source,
    private val urlFactory: ((String) -> Url)? = null
) {

    /**
     * Executes the update sequence for the source:
     * 1. Validates source readiness and fetch requirement via [SourceRepository.isFetchRequired].
     * 2. Creates [Url] object and fetches feed response.
     * 3. Calls [SourceRepository.updateSourceMetadata] to update title and favicon.
     * 4. Obtains [Entry] list from [Url.getEntries].
     * 5. Filters entries using active [EntryRule]s matching `trigger_rule_url` regex.
     * 6. Calls [SourceRepository.insertSourceEntries] to insert the filtered entries into `linkdatamodel`.
     * 7. Updates operational fetch data via [SourceRepository.updateFetchData].
     *
     * @return Pair of success flag and result message.
     */
    suspend fun update(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (source.url.isBlank()) {
            return@withContext Pair(false, "Source URL is empty")
        }
        if (!source.enabled) {
            return@withContext Pair(false, "Source is disabled")
        }
        if (!SourceRepository.isFetchRequired(context, activeDatabaseState, source)) {
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
     * Filters [entries] against active [rules].
     * If an entry matches an enabled rule with a non-blank `trigger_rule_url` regular expression
     * and `rule.block == true`, the entry is discarded and the rule's hit counter is incremented.
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

            var blocked = false
            for (rule in activeUrlRules) {
                if (matchesRuleUrl(link, rule.trigger_rule_url)) {
                    if (rule.block) {
                        blocked = true
                        rule.id?.let { ruleId ->
                            EntryRulesRepository.incrementTriggerHits(context, activeDatabaseState, ruleId)
                        }
                        break
                    }
                }
            }
            if (!blocked) {
                allowed.add(entry)
            }
        }
        return allowed
    }

    companion object {
        /**
         * Matches [link] against a [pattern] using regular expression matching.
         * Supports standard regex patterns, with fallback to wildcard and substring matches.
         *
         * @param link The entry URL string to test.
         * @param pattern Regular expression pattern from [EntryRule.trigger_rule_url].
         * @return True if the link matches the pattern.
         */
        fun matchesRuleUrl(link: String, pattern: String): Boolean {
            if (pattern.isBlank() || link.isBlank()) return false
            return try {
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                regex.containsMatchIn(link) || regex.matches(link)
            } catch (e: PatternSyntaxException) {
                val wildcardRegex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".")
                try {
                    Regex(wildcardRegex, RegexOption.IGNORE_CASE).containsMatchIn(link)
                } catch (e2: Exception) {
                    link.contains(pattern, ignoreCase = true)
                }
            } catch (e: Exception) {
                link.contains(pattern, ignoreCase = true)
            }
        }

        /**
         * Convenience static method to update a source using [SourceUpdater].
         *
         * @param context Application context.
         * @param activeDatabaseState Current database state.
         * @param source The source to update.
         * @param urlFactory Optional factory to supply custom [Url] instances.
         * @return Pair(success, resultMessage).
         */
        suspend fun updateSource(
            context: Context,
            activeDatabaseState: DatabaseState?,
            source: Source,
            urlFactory: ((String) -> Url)? = null
        ): Pair<Boolean, String> {
            return SourceUpdater(context, activeDatabaseState, source, urlFactory).update()
        }
    }
}
