package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** How long a successful network fetch is considered fresh (1 hour). */
private const val PRESETS_CACHE_TTL_MS: Long = 3_600_000L

/**
 * Repository responsible for loading and caching preselected database presets
 * from bundled assets (databases.json) and remote [DATABASES_LIST_JSON].
 *
 * The remote list is refreshed at most once per [PRESETS_CACHE_TTL_MS] (1 hour).
 * Callers can bypass the TTL by passing `forceNetwork = true` to [loadPresets].
 *
 * Provides comparison methods between a remote preset's `date_updated` and a local
 * database's `dateLastRefresh` to indicate when a database is outdated.
 */
object DatabasePresetRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // -------------------------------------------------------------------------
    // In-memory cache
    // -------------------------------------------------------------------------

    /** Cached preset list. Empty until the first successful load. */
    private var cachedPresets: List<DatabasePreset> = emptyList()

    /**
     * Wall-clock timestamp (ms) of the last successful **network** fetch,
     * or `null` if no network fetch has been performed in this process lifetime.
     *
     * Asset-based data does not set this field so that a network refresh is
     * always attempted on the first online opportunity.
     */
    private var fetchedAtMs: Long? = null

    private val _presets = MutableStateFlow<Map<String, DatabasePreset>>(emptyMap())

    /**
     * Flow emitting the currently cached map of database presets keyed by URL.
     * Updated after every successful asset or network load.
     */
    val presets: StateFlow<Map<String, DatabasePreset>> = _presets.asStateFlow()

    // -------------------------------------------------------------------------
    // Staleness helpers
    // -------------------------------------------------------------------------

    /**
     * Returns `true` when no successful network fetch has been performed yet, or when
     * the last fetch happened more than [PRESETS_CACHE_TTL_MS] milliseconds ago.
     */
    private fun isStale(): Boolean {
        val ts = fetchedAtMs ?: return true
        return System.currentTimeMillis() - ts > PRESETS_CACHE_TTL_MS
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Loads presets and returns them as a URL-keyed map.
     *
     * Loading strategy:
     * 1. Asset baseline — read once when the cache is empty so the list is
     *    immediately available offline without waiting for a network round-trip.
     * 2. Network refresh — attempted when [isStale] is `true` or when
     *    [forceNetwork] is `true`.  Skipped if network is disabled in config.
     *
     * @param context Application context for reading assets.
     * @param forceNetwork When `true`, bypasses the 1-hour TTL and always tries the network.
     * @return Map of URL → [DatabasePreset] reflecting the current cache contents.
     */
    suspend fun loadPresets(context: Context, forceNetwork: Boolean = false): Map<String, DatabasePreset> = withContext(Dispatchers.IO) {
        // Step 1: asset baseline.
        if (cachedPresets.isEmpty()) {
            val assetPresets = loadFromAssets(context)
            if (assetPresets.isNotEmpty()) {
                cachedPresets = assetPresets
                syncFlow()
            }
        }

        // Step 2: network refresh when stale or explicitly requested.
        val networkDisabled = AppConfigManager.config.value.networkConfig.disabled
        if (!networkDisabled && (forceNetwork || isStale())) {
            val remotePresets = loadFromNetwork()
            if (!remotePresets.isNullOrEmpty()) {
                cachedPresets = remotePresets
                fetchedAtMs = System.currentTimeMillis()
                syncFlow()
            }
        }

        _presets.value
    }

    /**
     * Reads presets from bundled assets/databases.json.
     *
     * @param context Application context.
     * @return List of [DatabasePreset] parsed from assets.
     */
    fun loadFromAssets(context: Context): List<DatabasePreset> {
        return try {
            val jsonString = context.assets.open("databases.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<DatabasePreset>>(jsonString)
                .filter { it.url.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches presets from the remote [DATABASES_LIST_JSON] URL.
     *
     * @return List of [DatabasePreset] parsed from the network response, or `null` on failure.
     */
    suspend fun loadFromNetwork(): List<DatabasePreset>? = withContext(Dispatchers.IO) {
        try {
            val response = NetworkUtils.executeRequest(DATABASES_LIST_JSON)
            val text = if (response.isValid) response.text else null
            if (!text.isNullOrBlank()) {
                json.decodeFromString<List<DatabasePreset>>(text)
                    .filter { it.url.isNotBlank() && (it.url.startsWith("http://") || it.url.startsWith("https://")) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Outdated-database helpers
    // -------------------------------------------------------------------------

    /**
     * Determines whether a local database is outdated by comparing its [dateLastRefresh]
     * against the preset's [DatabasePreset.dateUpdated].
     *
     * @param dateLastRefresh ISO-8601 UTC timestamp of the most recent local refresh.
     * @param dateUpdated ISO-8601 UTC timestamp of the remote database update.
     * @return `true` if [dateUpdated] is present and [dateLastRefresh] is `null` or earlier.
     */
    fun isOutdated(dateLastRefresh: String?, dateUpdated: String?): Boolean {
        if (dateUpdated.isNullOrBlank()) return false
        if (dateLastRefresh.isNullOrBlank()) return true

        val updatedMillis = DateUtils.parseIsoTimestamp(dateUpdated)
        val refreshMillis = DateUtils.parseIsoTimestamp(dateLastRefresh)

        return if (updatedMillis != null && refreshMillis != null) {
            refreshMillis < updatedMillis
        } else {
            dateLastRefresh < dateUpdated
        }
    }

    /**
     * Checks if a database is outdated given its URL and [dateLastRefresh].
     *
     * @param url URL of the database to look up in [presetsMap].
     * @param dateLastRefresh ISO-8601 UTC timestamp of the local refresh.
     * @param presetsMap Preset lookup map (keyed by URL); defaults to the live cache.
     * @return `true` if the database corresponds to a preset and is outdated.
     */
    fun isDatabaseOutdated(
        url: String,
        dateLastRefresh: String?,
        presetsMap: Map<String, DatabasePreset> = _presets.value
    ): Boolean {
        val preset = presetsMap[url] ?: presetsMap[url.trim()] ?: return false
        return isOutdated(dateLastRefresh, preset.dateUpdated)
    }

    /**
     * Checks if a [DatabaseState] is outdated based on the provided or cached presets.
     *
     * @param state The database state to check.
     * @param presetsMap Preset lookup map (keyed by URL); defaults to the live cache.
     * @return `true` if the database is outdated.
     */
    fun isDatabaseOutdated(
        state: DatabaseState,
        presetsMap: Map<String, DatabasePreset> = _presets.value
    ): Boolean {
        return isDatabaseOutdated(state.url, state.dateLastRefresh, presetsMap)
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Mirrors [cachedPresets] into [_presets] so UI flow consumers receive updates. */
    private fun syncFlow() {
        _presets.value = cachedPresets.associateBy { it.url }
    }
}
