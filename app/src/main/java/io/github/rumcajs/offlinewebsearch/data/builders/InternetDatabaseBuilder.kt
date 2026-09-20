package io.github.rumcajs.offlinewebsearch.data.builders

import android.content.Context
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus
import io.github.rumcajs.offlinewebsearch.data.converters.EntryJsonToDatabase
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Database builder for fetching databases from remote HTTP/HTTPS URLs.
 *
 * Supports remote `.db` files, compressed `.db.zip` archives, and `.json` files
 * or `.zip` archives containing JSON entries, converting them into local writable
 * SQLite databases.
 */
class InternetDatabaseBuilder(
    context: Context,
    url: String,
    oldUrl: String? = null,
    customFileName: String? = null
) : AbstractDatabaseBuilder(context, url, oldUrl, customFileName) {

    private var tempWorkingFile: File? = null
    private var tempZipFile: File? = null
    private var downloadedBytes: ByteArray? = null
    private var isZip: Boolean = false
    private var isJson: Boolean = false

    override suspend fun onInit() = withContext(Dispatchers.IO) {
        super.onInit()

        if (AppConfigManager.config.value.networkConfig.disabled) {
            throw IOException("Network communication is disabled in settings.")
        }

        isZip = url.endsWith(".db.zip", ignoreCase = true) || url.endsWith(".zip", ignoreCase = true)
        isJson = url.endsWith(".json", ignoreCase = true)

        if (!isJson && !isZip && !url.endsWith(".db", ignoreCase = true)) {
            throw IllegalArgumentException("URL must end with .json, .db, .zip, or .db.zip")
        }

        tempWorkingFile = File.createTempFile("internet_db_", ".db", context.cacheDir)
    }

    override suspend fun onDownloading() = withContext(Dispatchers.IO) {
        updateStatus(DatabaseStatus.DOWNLOADING, 0.25f)

        if (!NetworkUtils.verifyUrl(url)) {
            throw IOException("Invalid URL or server unreachable: $url")
        }

        val response = NetworkUtils.executeRequestBinary(url)
        val bytes = if (response.isValid) response.bytes else null

        if (bytes == null || bytes.isEmpty()) {
            throw IOException("Failed to download database files from $url (HTTP ${response.statusCode})")
        }

        downloadedBytes = bytes
    }

    override suspend fun onUnpacking() = withContext(Dispatchers.IO) {
        if (isZip && downloadedBytes != null) {
            updateStatus(DatabaseStatus.UNPACKING, 0.75f)
            tempZipFile = File.createTempFile("internet_temp_zip_", ".zip", context.cacheDir)
            tempZipFile!!.writeBytes(downloadedBytes!!)

            if (url.endsWith(".db.zip", ignoreCase = true)) {
                try {
                    unzipDatabaseToFile(tempZipFile!!, tempWorkingFile!!)
                } catch (e: Exception) {
                    val errorDescription = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Unknown error"}"
                    throw IOException("Failed to extract .db from zip file ($errorDescription)", e)
                }
            }
        }
    }

    override suspend fun onPopulatingTable(): Unit = withContext(Dispatchers.IO) {
        if (isJson && downloadedBytes != null) {
            updateStatus(DatabaseStatus.POPULATING_TABLE, 0.85f)
            copyAssetTableDb(tempWorkingFile!!)
            val jsonText = String(downloadedBytes!!, Charsets.UTF_8)
            val entries: List<Entry> = json.decodeFromString(jsonText)
            populateEntriesToDatabase(entries, tempWorkingFile!!)
        } else if (isZip && !url.endsWith(".db.zip", ignoreCase = true) && tempZipFile != null) {
            updateStatus(DatabaseStatus.POPULATING_TABLE, 0.85f)
            copyAssetTableDb(tempWorkingFile!!)
            EntryJsonToDatabase.importZipToDatabase(tempZipFile!!, tempWorkingFile!!)
        } else if (!isZip && !isJson && downloadedBytes != null) {
            // Direct SQLite .db file downloaded
            tempWorkingFile!!.writeBytes(downloadedBytes!!)
        }
    }

    override suspend fun onReady(): DatabaseState = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(context.filesDir, targetLocalFileName)
            AppConfigManager.removeDatabaseFiles(context, targetLocalFileName)
            if (tempWorkingFile != null && tempWorkingFile!!.exists()) {
                tempWorkingFile!!.copyTo(destinationFile, overwrite = true)
            }
            super.onReady()
        } finally {
            cleanup()
        }
    }

    override suspend fun onFailed(error: Throwable): DatabaseState = withContext(Dispatchers.IO) {
        val destinationFile = File(context.filesDir, targetLocalFileName)
        if (tempWorkingFile != null && tempWorkingFile!!.exists()) {
            AppConfigManager.removeDatabaseFiles(context, targetLocalFileName)
            tempWorkingFile!!.copyTo(destinationFile, overwrite = true)
        }
        cleanup()
        super.onFailed(error)
    }

    private fun cleanup() {
        try {
            tempWorkingFile?.delete()
            tempZipFile?.delete()
            downloadedBytes = null
        } catch (_: Exception) {
        }
    }
}
