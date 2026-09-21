package io.github.rumcajs.offlinewebsearch.data.builders

import android.content.Context
import android.net.Uri
import io.github.rumcajs.offlinewebsearch.data.ASSET_EMPTY_TABLE
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus
import io.github.rumcajs.offlinewebsearch.data.converters.EntryJsonToDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Database builder for local inputs including filesystem URIs, byte arrays,
 * asset templates, and existing database duplicates.
 *
 * Automatically converts local JSON files and archives into fully-functional,
 * writable SQLite database files using the `table.db` schema template.
 */
class LocalDatabaseBuilder(
    context: Context,
    url: String,
    private val uri: Uri? = null,
    private val rawContent: ByteArray? = null,
    private val assetFileName: String? = null,
    private val duplicateSourceState: DatabaseState? = null,
    oldUrl: String? = null,
    customFileName: String? = null
) : AbstractDatabaseBuilder(context, url, oldUrl, customFileName) {

    private var tempWorkingFile: File? = null
    private var tempZipFile: File? = null
    private var contentBytes: ByteArray? = null
    private var isJsonInput: Boolean = false
    private var isZipInput: Boolean = false

    override suspend fun onInit() = withContext(Dispatchers.IO) {
        super.onInit()

        isZipInput = url.endsWith(".db.zip", ignoreCase = true) || url.endsWith(".zip", ignoreCase = true)
        isJsonInput = url.endsWith(".json", ignoreCase = true)

        // Read content bytes from URI or rawContent if provided
        if (rawContent != null) {
            contentBytes = rawContent
        } else if (uri != null) {
            contentBytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                throw IOException("Failed to read file from URI: $uri", e)
            } ?: throw IOException("Could not open input stream for URI: $uri")
        } else if (assetFileName != null) {
            contentBytes = context.assets.open(assetFileName).use { it.readBytes() }
        }

        tempWorkingFile = File.createTempFile("local_build_db_", ".db", context.cacheDir)
    }

    override suspend fun onDownloading() {
        // No-op for local database builder
    }

    override suspend fun onUnpacking() = withContext(Dispatchers.IO) {
        if (duplicateSourceState != null) {
            // Duplication flow: copy from existing source file or asset
            val sourceFile = File(context.filesDir, duplicateSourceState.localFileName)
            if (sourceFile.exists()) {
                sourceFile.copyTo(tempWorkingFile!!, overwrite = true)
            } else {
                copyAssetTableDb(tempWorkingFile!!)
            }
            return@withContext
        }

        if (assetFileName != null && assetFileName.endsWith(".db", ignoreCase = true)) {
            // Creating directly from SQLite asset (e.g. table.db)
            copyAssetTableDb(tempWorkingFile!!, assetFileName)
            return@withContext
        }

        if (isZipInput) {
            updateStatus(DatabaseStatus.UNPACKING, 0.75f)
            tempZipFile = File.createTempFile("local_temp_zip_", ".zip", context.cacheDir)
            contentBytes?.let { tempZipFile!!.writeBytes(it) }

            if (url.endsWith(".db.zip", ignoreCase = true)) {
                unzipDatabaseToFile(tempZipFile!!, tempWorkingFile!!)
            }
        }
    }

    override suspend fun onPopulatingTable(): Unit = withContext(Dispatchers.IO) {
        if (isJsonInput && contentBytes != null) {
            updateStatus(DatabaseStatus.POPULATING_TABLE, 0.85f)
            copyAssetTableDb(tempWorkingFile!!)
            val entries = EntryJsonToDatabase.parseJson(String(contentBytes!!, Charsets.UTF_8))
            populateEntriesToDatabase(entries, tempWorkingFile!!)
        } else if (isZipInput && !url.endsWith(".db.zip", ignoreCase = true) && tempZipFile != null) {
            // General zip containing JSON files
            updateStatus(DatabaseStatus.POPULATING_TABLE, 0.85f)
            copyAssetTableDb(tempWorkingFile!!)
            EntryJsonToDatabase.importZipToDatabase(tempZipFile!!, tempWorkingFile!!)
        } else if (contentBytes != null && !isZipInput && !isJsonInput && assetFileName == null) {
            // Direct SQLite file bytes
            tempWorkingFile!!.writeBytes(contentBytes!!)
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
            contentBytes = null
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * Creates a builder for initializing a new SQLite database from an asset template (e.g. `table.db`).
         */
        fun fromAsset(
            context: Context,
            customName: String? = null,
            assetFileName: String = ASSET_EMPTY_TABLE
        ): LocalDatabaseBuilder {
            val fileName = customName?.takeIf { it.isNotBlank() } ?: "new_database.db"
            val formattedFileName = if (fileName.endsWith(".db", ignoreCase = true)) fileName else "$fileName.db"
            val url = DatabaseState.toLocalUrl(formattedFileName)

            return LocalDatabaseBuilder(
                context = context,
                url = url,
                assetFileName = assetFileName,
                customFileName = formattedFileName
            )
        }

        /**
         * Creates a builder for duplicating an existing database.
         */
        fun duplicate(
            context: Context,
            state: DatabaseState
        ): LocalDatabaseBuilder {
            val baseName = if (state.displayName.isNotBlank()) state.displayName else "Database"
            var copyIndex = 1
            var newUrl: String
            var newLocalFileName: String
            val timestamp = System.currentTimeMillis()

            do {
                val candidateName = if (copyIndex == 1) "$baseName Copy" else "$baseName Copy $copyIndex"
                newUrl = DatabaseState.toLocalUrl("$candidateName.db")
                newLocalFileName = "db_${timestamp}_$copyIndex.db"
                copyIndex++
            } while (AppConfigManager.config.value.databases.containsKey(newUrl))

            return LocalDatabaseBuilder(
                context = context,
                url = newUrl,
                duplicateSourceState = state,
                customFileName = newLocalFileName
            )
        }

        /**
         * Creates a builder for saving database from raw byte array.
         */
        fun fromBytes(
            context: Context,
            url: String,
            content: ByteArray,
            oldUrl: String? = null
        ): LocalDatabaseBuilder {
            return LocalDatabaseBuilder(
                context = context,
                url = url,
                rawContent = content,
                oldUrl = oldUrl
            )
        }
    }
}
