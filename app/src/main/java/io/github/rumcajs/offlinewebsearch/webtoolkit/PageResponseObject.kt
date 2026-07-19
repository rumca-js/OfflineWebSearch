package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object NetworkUtils {
    // TODO move to statusCodes.kt
    fun isStatusCodeValid(statusCode: Int): Boolean {
        return (statusCode >= 200 && statusCode < 400);
    }

    fun isStatusCodeInvalid(statusCode: Int): Boolean {
        if (statusCode == 0)
            return false;
        if (statusCode == 403)
            return false;
        if (statusCode == 429)
            return false;

        if (statusCode < 200)
            return true;
        if (statusCode >= 400)
            return true;
        return false;
    }

    val client: OkHttpClient by lazy {
        val config = AppConfigManager.config.value

        OkHttpClient.Builder()
            .connectTimeout(config.networkConfig.connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(config.networkConfig.readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun verifyUrl(urlString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val config = AppConfigManager.config.value
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = config.networkConfig.connectTimeout
            connection.readTimeout = config.networkConfig.readTimeout
            connection.setRequestProperty("User-Agent", config.networkConfig.userAgent)
            val responseCode = connection.responseCode
            isStatusCodeValid(responseCode)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getResponseFull(urlString: String): PageResponseObject =
        executeRequest(
            urlString,
            "application/rss+xml, application/atom+xml, text/xml, application/json, */*"
        )

    suspend fun getResponseHeaders(urlString: String): PageResponseObject =
        executeHeaderRequest(urlString)

    suspend fun executeHeaderRequest(
        urlString: String,
        acceptHeader: String? = null
    ): PageResponseObject = withContext(Dispatchers.IO) {
        try {
            val config = AppConfigManager.config.value

            val requestBuilder = Request.Builder()
                .url(urlString)
                .head()
                .header("User-Agent", config.networkConfig.userAgent)

            acceptHeader?.let {
                requestBuilder.header("Accept", it)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                PageResponseObject(
                    statusCode = response.code,
                    headers = response.headers.toMultimap(),
                    error = if (response.isSuccessful) null else "HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            PageResponseObject(
                statusCode = -1,
                headers = emptyMap(),
                error = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        }
    }

    private suspend fun executeRequest(
        urlString: String,
        acceptHeader: String? = null
    ): PageResponseObject = withContext(Dispatchers.IO) {
        try {
            val config = AppConfigManager.config.value

            val requestBuilder = Request.Builder()
                .url(urlString)
                .header("User-Agent", config.networkConfig.userAgent)

            acceptHeader?.let {
                requestBuilder.header("Accept", it)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val text = response.body?.string()

                PageResponseObject(
                    statusCode = response.code,
                    headers = response.headers.toMultimap(),
                    text = text,
                    error = if (response.isSuccessful) null else "HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            PageResponseObject(
                statusCode = -1,
                headers = emptyMap(),
                text = null,
                error = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        }
    }

    suspend fun executeRequestBinary(
        urlString: String,
        acceptHeader: String? = null
    ): PageResponseObject = withContext(Dispatchers.IO) {

        try {
            val config = AppConfigManager.config.value

            val requestBuilder = Request.Builder()
                .url(urlString)
                .header("User-Agent", config.networkConfig.userAgent)

            acceptHeader?.let {
                requestBuilder.header("Accept", it)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->

                val headers = response.headers.toMultimap()

                val bytes = response.body?.bytes()

                if (response.isSuccessful) {
                    PageResponseObject(
                        statusCode = response.code,
                        headers = headers,
                        bytes = bytes
                    )
                } else {
                    PageResponseObject(
                        statusCode = response.code,
                        headers = headers,
                        text = bytes?.decodeToString(),
                        error = "HTTP ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            PageResponseObject(
                statusCode = -1,
                headers = emptyMap(),
                bytes = null,
                text = null,
                error = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        }
    }
}

// TODO move to PageResponseObject.kt
data class PageResponseObject(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val text: String? = null,
    val bytes: ByteArray? = null, // Changed from String? to ByteArray?
    val error: String? = null
) {
    val contentType: String?
        get() = headers.entries.find { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()

    val length: Long?
        get() = headers.entries.find { it.key.equals("Content-Length", ignoreCase = true) }?.value?.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
            ?: bytes?.size?.toLong() // Check the binary size first
            ?: text?.toByteArray(Charsets.UTF_8)?.size?.toLong()

    val isValid: Boolean get() = NetworkUtils.isStatusCodeValid(statusCode)
    val isInvalid: Boolean get() = NetworkUtils.isStatusCodeInvalid(statusCode)

    // Overriding equals and hashCode is highly recommended when using ByteArrays in data classes
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageResponseObject

        if (statusCode != other.statusCode) return false
        if (headers != other.headers) return false
        if (text != other.text) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}
