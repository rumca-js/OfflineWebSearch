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

/**
 * Repository responsible for loading and caching preselected database presets
 * from bundled assets (databases.json) and remote DATABASES_LIST_JSON.
 *
 * Provides comparison methods between a remote preset's `date_updated` and a local
 * database's `dateLastRefresh` to indicate when a database is outdated.
 */
object DatabasePresetRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _presets = MutableStateFlow<Map<String, DatabasePreset>>(emptyMap())

    /**
     * Flow emitting the currently cached map of database presets keyed by URL.
     */
    val presets: StateFlow<Map<String, DatabasePreset>> = _presets.asStateFlow()

    /**
     * Loads presets from assets/databases.json if not yet populated,
     * and refreshes from remote DATABASES_LIST_JSON when network is available.
     *
     * @param context Application context for reading assets.
     * @param forceNetwork If true, attempts to fetch remote JSON even if cached.
     * @return Map of URL to DatabasePreset.
     */
    suspend fun loadPresets(context: Context, forceNetwork: Boolean = false): Map<String, DatabasePreset> = withContext(Dispatchers.IO) {
        if (_presets.value.isEmpty() || forceNetwork) {
            // Baseline: load bundled assets first so presets are immediately available offline
            val assetPresets = loadFromAssets(context)
            if (assetPresets.isNotEmpty() && _presets.value.isEmpty()) {
                _presets.value = assetPresets.associateBy { it.url }
            }

            // Check if network communication is enabled
            val networkDisabled = AppConfigManager.config.value.networkConfig.disabled
            if (!networkDisabled) {
                val remotePresets = loadFromNetwork()
                if (!remotePresets.isNullOrEmpty()) {
                    _presets.value = remotePresets.associateBy { it.url }
                }
            }
        }
        _presets.value
    }

    /**
     * Reads presets from bundled assets/databases.json.
     *
     * @param context Application context.
     * @return List of DatabasePreset parsed from assets.
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
     * Fetches presets from remote DATABASES_LIST_JSON URL.
     *
     * @return List of DatabasePreset parsed from network response, or null on failure.
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

    /**
     * Determines whether a local database is outdated by comparing its [dateLastRefresh]
     * against the preset's [DatabasePreset.dateUpdated].
     *
     * @param dateLastRefresh ISO-8601 UTC timestamp of the most recent local refresh.
     * @param dateUpdated ISO-8601 UTC timestamp of the remote database update.
     * @return true if dateUpdated is present and dateLastRefresh is null or earlier than dateUpdated.
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
     * Checks if a database is outdated given its URL and dateLastRefresh, using the provided presets map.
     *
     * @param url URL of the database.
     * @param dateLastRefresh ISO-8601 UTC timestamp of local refresh.
     * @param presetsMap Preset lookup map (keyed by URL).
     * @return true if database corresponds to a preset and is outdated.
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
     * @param presetsMap Preset lookup map (keyed by URL).
     * @return true if database is outdated.
     */
    fun isDatabaseOutdated(
        state: DatabaseState,
        presetsMap: Map<String, DatabasePreset> = _presets.value
    ): Boolean {
        return isDatabaseOutdated(state.url, state.dateLastRefresh, presetsMap)
    }
}
