package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import io.github.rumcajs.offlinewebsearch.webtoolkit.NetworkUtils


/**
 * Singleton to manage app configuration.
 * Can be updated from various sources.
 */
object AppConfigManager {
    private const val APP_CONFIG_FILE_NAME = "app_config.json"
    private const val NETWORK_CONFIG_FILE_NAME = "network_config.json"
    private var appContext: Context? = null

    // Reuse a single Scope for background disk I/O tasks
    private val configScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Reuse a single Json instance to allow internal serialization caching
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _config = MutableStateFlow(AppConfiguration())
    val config: StateFlow<AppConfiguration> = _config.asStateFlow()

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext

        // Load configurations sequentially on the background thread to avoid state races
        configScope.launch {
            loadPersistedConfigSync(applicationContext)
            loadNetworkConfigSync(applicationContext)
        }
    }

    fun updateConfig(update: (AppConfiguration) -> AppConfiguration) {
        _config.update(update)
        saveConfigAsync() // Offloaded to background thread
    }

    private fun loadPersistedConfigSync(context: Context) {
        try {
            val file = context.getFileStreamPath(APP_CONFIG_FILE_NAME)
            if (file != null && file.exists()) {
                context.openFileInput(APP_CONFIG_FILE_NAME).bufferedReader().use { reader ->
                    val jsonString = reader.readText()
                    val persistedConfig = json.decodeFromString<AppConfiguration>(jsonString)
                    _config.update { currentConfig ->
                        persistedConfig.copy(networkConfig = currentConfig.networkConfig)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadNetworkConfigSync(context: Context) {
        try {
            context.assets.open(NETWORK_CONFIG_FILE_NAME).bufferedReader().use { reader ->
                val jsonString = reader.readText()
                val networkConfig = json.decodeFromString<NetworkConfig>(jsonString)
                _config.update {
                    it.copy(networkConfig = networkConfig)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveConfigAsync() {
        val context = appContext ?: return
        val currentConfig = config.value
        configScope.launch {
            try {
                val jsonString = json.encodeToString(currentConfig)
                context.openFileOutput(APP_CONFIG_FILE_NAME, Context.MODE_PRIVATE).use { output ->
                    output.write(jsonString.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDirectLinks(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(directLinks = enabled) }
        }
    }

    fun setShowIcons(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(showIcons = enabled) }
        }
    }

    fun setVideoPreview(enabled: Boolean) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(videoPreview = enabled) }
        }
    }

    fun setOrderBy(orderBy: OrderBy) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(orderBy = orderBy) }
        }
    }

    fun setViewStyle(viewStyle: ViewStyle) {
        updateConfig { currentConfig ->
            currentConfig.updateActiveDbConfig { it.copy(viewStyle = viewStyle) }
        }
    }

    fun setUserAge(age: Int) {
        updateConfig { it.copy(userAge = age) }
    }

    fun addDatabase(url: String) {
        updateConfig {
            it.copy(databases = it.databases + (url to DatabaseState.fromUrl(url)))
        }
    }

    fun removeDatabase(url: String) {
        updateConfig {
            val newDatabases = it.databases - url
            val newDbConfigs = it.dbConfigs - url
            it.copy(
                databases = newDatabases,
                dbConfigs = newDbConfigs,
                activeDatabase = if (it.activeDatabase == url) null else it.activeDatabase
            )
        }
    }

    fun updateDatabase(oldUrl: String, newUrl: String) {
        updateConfig { config ->
            val newDatabases = config.databases.toMutableMap().apply {
                remove(oldUrl)?.let { state ->
                    // Corrected: Update the copy's internal url property too!
                    put(newUrl, state.copy(url = newUrl, localFileName = DatabaseState.fromUrl(newUrl).localFileName))
                }
            }

            val newDbConfigs = config.dbConfigs.toMutableMap().apply {
                remove(oldUrl)?.let { dbConfig ->
                    put(newUrl, dbConfig)
                }
            }

            config.copy(
                databases = newDatabases,
                dbConfigs = newDbConfigs,
                activeDatabase = if (config.activeDatabase == oldUrl) newUrl else config.activeDatabase
            )
        }
    }

    /**
     * Safely saves database content (either from a local byte array or a remote download)
     * and updates the AppConfiguration maps.
     */
    fun saveDatabaseSource(
        context: Context,
        url: String,
        content: ByteArray,
        oldUrl: String? = null
    ) {
        val newState = DatabaseState.fromUrl(url).copy(status = DatabaseStatus.READY)

        configScope.launch {
            try {
                // 1. Write the new file
                context.openFileOutput(newState.localFileName, Context.MODE_PRIVATE).use { output ->
                    output.write(content)
                }

                // 2. Perform old file cleanup and configuration state transition
                updateConfig { config ->
                    // Clean up old file if the URL actually changed
                    if (oldUrl != null && oldUrl != url) {
                        val oldState = DatabaseState.fromUrl(oldUrl)
                        File(context.filesDir, oldState.localFileName).delete()
                    }

                    // Prepare updated maps
                    val newDatabases = config.databases.toMutableMap().apply {
                        if (oldUrl != null) {
                            remove(oldUrl)?.let { state ->
                                put(url, state.copy(
                                    url = url,
                                    localFileName = newState.localFileName,
                                    status = DatabaseStatus.READY,
                                    errorMessage = null
                                ))
                            }
                        } else {
                            put(url, newState)
                        }
                    }

                    val newDbConfigs = config.dbConfigs.toMutableMap().apply {
                        if (oldUrl != null) {
                            remove(oldUrl)?.let { dbConfig -> put(url, dbConfig) }
                        }
                    }

                    config.copy(
                        databases = newDatabases,
                        dbConfigs = newDbConfigs,
                        activeDatabase = if (config.activeDatabase == oldUrl) url else config.activeDatabase
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            }
        }
    }

    fun updateDatabaseStatus(url: String, status: DatabaseStatus, errorMessage: String? = null) {
        updateConfig { config ->
            val newDatabases = config.databases.toMutableMap().apply {
                get(url)?.let { state ->
                    put(url, state.copy(status = status, errorMessage = errorMessage))
                }
            }
            config.copy(databases = newDatabases)
        }
    }

    /**
     * Reads database bytes from a local URI and saves them as a database source.
     */
    suspend fun saveDatabaseLocal(
        context: Context,
        url: String,
        uri: Uri,
        oldUrl: String? = null
    ) {
        if (oldUrl == null) {
            addDatabase(url)
        } else {
            updateDatabase(oldUrl, url)
        }

        try {
            val content = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.readBytes()
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: throw IOException("Failed to read file")

            saveDatabaseSource(context, url, content, oldUrl)
        } catch (e: Exception) {
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    /**
     * Downloads a database from the internet, unzips it if needed, and saves it as a local database source.
     */
    suspend fun saveDatabaseFromInternet(
        context: Context,
        url: String,
        oldUrl: String? = null
    ) {
        val state = DatabaseState.fromUrl(url)
        val isZip = url.endsWith(".db.zip", ignoreCase = true)

        if (state.extension != ".json" && state.extension != ".db" && !isZip) {
            throw IllegalArgumentException("URL must end with .json, .db, or .db.zip")
        }

        if (oldUrl == null) {
            addDatabase(url)
        } else {
            updateDatabase(oldUrl, url)
        }

        // Set status to DOWNLOADING
        updateDatabaseStatus(url, DatabaseStatus.DOWNLOADING)

        try {
            if (!NetworkUtils.verifyUrl(url)) {
                throw IOException("Invalid URL or server unreachable")
            }

            val response = NetworkUtils.executeRequestBinary(url)
            var content = if (response.isValid) response.bytes else null

            if (content == null) {
                throw IOException("Failed to download database files")
            }

            if (isZip) {
                updateDatabaseStatus(url, DatabaseStatus.UNPACKING)
                try {
                    // This call will now directly throw the underlying error if it fails
                    val unpackedContent = unzipDatabaseBytes(content, context.cacheDir)
                    content = unpackedContent
                } catch (e: Exception) {
                    // Extract technical detail (e.g., "ZipException: Not a ZIP archive" or "NoSuchElementException: ...")
                    val errorDescription = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Unknown error"}"

                    // Log the detailed exception somewhere if needed
                    // Log.e("UnpackError", "Failed to extract database", e)

                    // Update your status or bubble up a comprehensive IOException
                    throw IOException("Failed to extract .db from zip file ($errorDescription)", e)
                }
            }

            saveDatabaseSource(context, url, content, oldUrl)
        } catch (e: Exception) {
            updateDatabaseStatus(url, DatabaseStatus.FAILED, e.message)
            throw e
        }
    }

    /**
     * Unpacks a ZIP archive provided as a byte array and extracts the first database (.db) file found.
     *
     * ### Android Compatibility Warning
     * This function specifically avoids using Android's native `java.util.zip.ZipInputStream`
     * sequential parser. When a ZIP file is generated programmatically via streams (e.g., automated
     * backend feeds or GitHub Actions), it often leaves the size and CRC metadata empty in the local
     * header and appends a **Data Descriptor** block *after* the compressed data payload.
     *
     * Android's legacy `ZipInputStream` implementation is notoriously brittle and frequently throws a
     * `ZipException` (e.g., CRC or malformed data errors) when it encounters these post-data descriptors
     * in-flight.
     *
     * To circumvent this, this implementation uses a random-access approach (via `ZipFile` or a
     * virtual `FileSystem`). By processing the Central Directory located at the end of the archive first,
     * it guarantees successful decompression regardless of how the ZIP header flags were generated.
     *
     * @param zipBytes The raw byte array representing the compressed ZIP archive.
     * @return A [ByteArray] containing the uncompressed `.db` file content if successfully found and
     *         extracted; `null` if no matching database file is found or if an extraction error occurs.
     */
    @Throws(IOException::class, NoSuchElementException::class)
    internal fun unzipDatabaseBytes(zipBytes: ByteArray, cacheDir: File): ByteArray {
        val tempFile = File.createTempFile("temp_db", ".zip", cacheDir)

        try {
            tempFile.writeBytes(zipBytes)

            ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".db")) {
                        zip.getInputStream(entry).use { inputStream ->
                            val out = ByteArrayOutputStream()
                            inputStream.copyTo(out)
                            return out.toByteArray()
                        }
                    }
                }
                // If the loop finishes without returning, the file structure is fine but missing the target
                throw NoSuchElementException("ZIP archive parsed successfully, but no file ending in '.db' was found inside.")
            }
        } finally {
            tempFile.delete()
        }
    }

    fun setActiveDatabase(url: String?) {
        updateConfig { it.copy(activeDatabase = url) }
    }
}
