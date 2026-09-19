package io.github.rumcajs.offlinewebsearch.data.builders

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.ASSET_EMPTY_TABLE
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DEFAULT_DATABASE_NAME
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus
import io.github.rumcajs.offlinewebsearch.data.defaultAssets
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Database builder that initializes the built-in default SQLite database (`default.db`)
 * by reading bundled JSON assets (`places_0.json` .. `places_10.json`) into an empty
 * `table.db` SQLite schema template.
 *
 * This ensures that the default database is a writable SQLite database rather than
 * read-only in-memory JSON.
 */
class DefaultDatabaseBuilder(
    context: Context,
    private val assetList: List<String> = defaultAssets,
    private val forceRebuild: Boolean = false
) : AbstractDatabaseBuilder(
    context = context,
    url = "",
    customFileName = DEFAULT_DATABASE_FILE
) {

    private var tempWorkingFile: File? = null

    override suspend fun onInit() = withContext(Dispatchers.IO) {
        // Register the default database entry (url = "") in config.databases so that
        // subsequent updateDatabaseStatus("") calls can find it by key and propagate
        // intermediate states (INIT, POPULATING_TABLE, …) to the UI.
        // The base-class onInit() does this for regular databases, but DefaultDatabaseBuilder
        // overrides it entirely, so we must ensure it happens here before any updateStatus call.
        // Only add when absent to avoid overwriting preserved fields (displayName, dateCreated).
        if (!AppConfigManager.config.value.databases.containsKey(url)) {
            AppConfigManager.addDatabase(url)
        }

        updateStatus(DatabaseStatus.INIT, 0.0f)
        val destFile = File(context.filesDir, DEFAULT_DATABASE_FILE)
        if (destFile.exists() && !forceRebuild) {
            updateStatus(DatabaseStatus.READY, 1.0f)
            return@withContext
        }
        tempWorkingFile = File.createTempFile("default_build_db_", ".db", context.cacheDir)
    }

    override suspend fun onDownloading() {
        // No-op: default assets are bundled in APK
    }

    override suspend fun onUnpacking() {
        // No-op: bundled assets are plain JSON
    }

    override suspend fun onPopulatingTable() = withContext(Dispatchers.IO) {
        if (_currentStatus == DatabaseStatus.READY && !forceRebuild) return@withContext

        updateStatus(DatabaseStatus.POPULATING_TABLE, 0.5f)
        copyAssetTableDb(tempWorkingFile!!, ASSET_EMPTY_TABLE)

        val totalAssets = assetList.size
        assetList.forEachIndexed { index, fileName ->
            try {
                context.assets.open(fileName).bufferedReader().use { reader ->
                    val jsonString = reader.readText()
                    val entries: List<Entry> = json.decodeFromString(jsonString)
                    populateEntriesToDatabase(entries, tempWorkingFile!!)
                }
                val progress = 0.5f + ((index + 1).toFloat() / totalAssets) * 0.45f
                updateStatus(DatabaseStatus.POPULATING_TABLE, progress)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun onReady(): DatabaseState = withContext(Dispatchers.IO) {
        val destFile = File(context.filesDir, DEFAULT_DATABASE_FILE)
        if (tempWorkingFile != null && tempWorkingFile!!.exists()) {
            AppConfigManager.removeDatabaseFiles(context, DEFAULT_DATABASE_FILE)
            tempWorkingFile!!.copyTo(destFile, overwrite = true)
        }

        val now = DateUtils.getCurrentIsoTimestamp()
        val size = if (destFile.exists()) destFile.length() else 0L

        cleanup()
        updateStatus(DatabaseStatus.READY, 1.0f)

        DatabaseState(
            url = "",
            localFileName = DEFAULT_DATABASE_FILE,
            displayNameField = DEFAULT_DATABASE_NAME,
            status = DatabaseStatus.READY,
            progress = 1.0f,
            errorMessage = null,
            sizeInBytes = size,
            isReadOnly = false,
            dateCreated = now,
            dateLastRefresh = now
        )
    }

    override suspend fun onFailed(error: Throwable): DatabaseState = withContext(Dispatchers.IO) {
        cleanup()
        super.onFailed(error)
    }

    private fun cleanup() {
        try {
            tempWorkingFile?.delete()
            tempWorkingFile = null
        } catch (_: Exception) {
        }
    }

    companion object {
        const val DEFAULT_DATABASE_FILE = "default.db"
    }
}
