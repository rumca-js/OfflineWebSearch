package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.CredentialsRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRulesRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalDataRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.email.EmailClient
import io.github.rumcajs.offlinewebsearch.email.EmailConnectionConfig
import io.github.rumcajs.offlinewebsearch.email.ImapClient
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.util.EntryRuleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Source updater for Email sources (IMAP/IMAPS).
 *
 * Connects to the configured email server without OAuth (using standard username/password credentials),
 * fetches new email messages with a 24-hour lookback window from [SourceOperationalData.date_fetched],
 * converts each message into an [Entry], applies active [EntryRule] filters, and persists entries into the database.
 *
 * @param context Application context.
 * @param activeDatabaseState Current database state.
 * @param source The Email source to update.
 * @param force If true, skips the outdated/fetch-period check and forces a fetch.
 * @param clientFactory Optional factory to supply custom [EmailClient] instances (useful for testing and mocking).
 */
class SourceUpdaterEmail(
    private val context: Context,
    private val activeDatabaseState: DatabaseState?,
    private val source: Source,
    private val force: Boolean = false,
    private val clientFactory: ((EmailConnectionConfig) -> EmailClient)? = null
) : SourceUpdaterInterface {

    companion object {
        /**
         * Lookback window duration (24 hours) subtracted when calculating `sinceDate` from `date_fetched`
         * so that older emails from the last 24 hours have a chance to be fetched and retried.
         */
        const val EMAIL_LOOKBACK_WINDOW_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Executes the update sequence for the Email source:
     * 1. Validates source enabled status and fetch requirement.
     * 2. Resolves email credentials from [CredentialsRepository].
     * 3. Retrieves the last fetched timestamp from [SourceOperationalData.date_fetched] with a 24-hour lookback window.
     * 4. Connects to the email server, authenticates, and reads messages newer than `sinceDate`.
     * 5. Converts messages into [Entry] instances with fake links (`email://{source.url}/{source.id}/{message.id}`).
     * 6. Filters entries against active [EntryRule]s.
     * 7. Inserts entries into `linkdatamodel` and updates operational fetch metadata.
     *
     * @return Pair where first is a success flag, and second is a result/status message.
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

        // 1. Resolve credentials
        val credentials = if (source.credentials_id != null) {
            CredentialsRepository.getCredentialById(context, activeDatabaseState, source.credentials_id)
        } else {
            CredentialsRepository.getCredentialByName(context, activeDatabaseState, source.url)
                ?: CredentialsRepository.getCredentialByName(context, activeDatabaseState, source.title)
        }

        val config = EmailConnectionConfig.fromSource(source, credentials)
        if (config.username.isBlank() || config.password.isBlank()) {
            val errorMsg = "No credentials found for email source: ${source.title.ifBlank { source.url }}"
            AppLoggingRepository.error(context, activeDatabaseState, errorMsg, "Credentials ID: ${source.credentials_id}")
            source.id?.let {
                SourceOperationalDataRepository.setSourceFetch(
                    context = context,
                    activeDatabaseState = activeDatabaseState,
                    sourceObjId = it,
                    isError = true
                )
            }
            return@withContext Pair(false, errorMsg)
        }

        // 2. Resolve sinceDate from SourceOperationalData.date_fetched with 24-hour lookback window
        val opData = source.id?.let {
            SourceOperationalDataRepository.getOperationalDataBySourceId(context, activeDatabaseState, it)
        }
        val dateFetchedStr = opData?.date_fetched
        val sinceDate: Date? = dateFetchedStr?.let {
            val fetchMillis = DateUtils.parseIsoTimestamp(it)
                ?: DateUtils.parseDateString(it)?.time
            fetchMillis?.let { millis -> Date(maxOf(0L, millis - EMAIL_LOOKBACK_WINDOW_MS)) }
        }

        // 3. Connect to email server and fetch messages
        val client = clientFactory?.invoke(config) ?: ImapClient(config)
        val rawMessages = try {
            if (!client.connect()) {
                val errorMsg = "Failed to connect to email server at ${config.host}:${config.port}"
                AppLoggingRepository.error(context, activeDatabaseState, errorMsg)
                recordFetchError(source)
                return@withContext Pair(false, errorMsg)
            }

            if (!client.login(config.username, config.password)) {
                val errorMsg = "Authentication failed for email source user: ${config.username}"
                AppLoggingRepository.error(context, activeDatabaseState, errorMsg)
                recordFetchError(source)
                return@withContext Pair(false, errorMsg)
            }

            val count = client.selectFolder(config.folder)
            if (count < 0) {
                val errorMsg = "Failed to select folder '${config.folder}' on email server"
                AppLoggingRepository.error(context, activeDatabaseState, errorMsg)
                recordFetchError(source)
                return@withContext Pair(false, errorMsg)
            }

            client.fetchMessages(sinceDate = sinceDate)
        } catch (e: Exception) {
            val errorMsg = "Exception during email source fetch: ${e.message}"
            AppLoggingRepository.error(context, activeDatabaseState, errorMsg, e.stackTraceToString())
            recordFetchError(source)
            return@withContext Pair(false, errorMsg)
        } finally {
            try {
                client.disconnect()
            } catch (_: Exception) { }
            client.close()
        }

        // 4. Convert email messages to Entry objects
        val rawEntries = rawMessages.map { it.toEntry(source) }

        // 5. Filter entries using active EntryRules
        val rules = EntryRulesRepository.getRules(context, activeDatabaseState, enabledOnly = true)
        val filteredEntries = filterEntries(rawEntries, rules)

        // 6. Insert entries into database
        val (insertOk, insertedCount) = SourceRepository.insertSourceEntries(
            context = context,
            activeDatabaseState = activeDatabaseState,
            entries = filteredEntries,
            source = source
        )

        // 7. Update operational fetch data
        if (source.id != null) {
            SourceOperationalDataRepository.setSourceFetch(
                context = context,
                activeDatabaseState = activeDatabaseState,
                sourceObjId = source.id,
                numberOfEntries = rawEntries.size,
                isError = false
            )
        } else {
            SourceOperationalDataRepository.setSourceFetchByUrl(
                context = context,
                activeDatabaseState = activeDatabaseState,
                sourceUrl = source.url,
                numberOfEntries = rawEntries.size,
                isError = false
            )
        }

        if (insertOk) {
            Pair(true, "Successfully updated email source with $insertedCount entries")
        } else {
            Pair(false, "Failed to insert entries for email source")
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

    private suspend fun recordFetchError(source: Source) {
        if (source.id != null) {
            SourceOperationalDataRepository.setSourceFetch(
                context = context,
                activeDatabaseState = activeDatabaseState,
                sourceObjId = source.id,
                isError = true
            )
        } else {
            SourceOperationalDataRepository.setSourceFetchByUrl(
                context = context,
                activeDatabaseState = activeDatabaseState,
                sourceUrl = source.url,
                isError = true
            )
        }
    }
}
