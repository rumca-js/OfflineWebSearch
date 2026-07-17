package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
        var connection: HttpURLConnection? = null
        try {
            val config = AppConfigManager.config.value
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection

            // 1. Set the request method to HEAD
            connection.requestMethod = "HEAD"

            connection.connectTimeout = config.networkConfig.connectTimeout
            connection.readTimeout = config.networkConfig.readTimeout
            connection.setRequestProperty("User-Agent", config.networkConfig.userAgent)
            if (acceptHeader != null) {
                connection.setRequestProperty("Accept", acceptHeader)
            }

            val responseCode = connection.responseCode
            val headers = connection.headerFields.mapNotNull { (key, value) ->
                if (key != null) key to value else null
            }.toMap()

            // 2. Skip reading connection.inputStream. HEAD requests have no body.

            if (isStatusCodeValid(responseCode)) {
                PageResponseObject(
                    statusCode = responseCode,
                    headers = headers,
                    text = null // No body content to provide
                )
            } else {
                PageResponseObject(
                    statusCode = responseCode,
                    headers = headers,
                    text = null,
                    error = "HTTP $responseCode"
                )
            }
        } catch (e: Exception) {
            PageResponseObject(
                statusCode = -1,
                headers = emptyMap(),
                text = null,
                error = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun executeRequest(
        urlString: String,
        acceptHeader: String? = null
    ): PageResponseObject = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val config = AppConfigManager.config.value
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = config.networkConfig.connectTimeout
            connection.readTimeout = config.networkConfig.readTimeout
            connection.setRequestProperty("User-Agent", config.networkConfig.userAgent)
            if (acceptHeader != null) {
                connection.setRequestProperty("Accept", acceptHeader)
            }
            val responseCode = connection.responseCode
            val headers = connection.headerFields.mapNotNull { (key, value) ->
                if (key != null) key to value else null
            }.toMap()

            val stream = if (isStatusCodeValid(responseCode)) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.use { it.bufferedReader().readText() }

            if (isStatusCodeValid(responseCode)) {
                PageResponseObject(responseCode, headers, text=text)
            } else {
                PageResponseObject(responseCode, headers, text=text, error="HTTP $responseCode")
            }
        } catch (e: Exception) {
            PageResponseObject(
                statusCode = -1,
                headers = emptyMap(),
                text = null,
                error = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun executeRequestBinary(
        urlString: String,
        acceptHeader: String? = null
    ): PageResponseObject = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val config = AppConfigManager.config.value
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = config.networkConfig.connectTimeout
            connection.readTimeout = config.networkConfig.readTimeout
            connection.setRequestProperty("User-Agent", config.networkConfig.userAgent)
            if (acceptHeader != null) {
                connection.setRequestProperty("Accept", acceptHeader)
            }
            val responseCode = connection.responseCode
            val headers = connection.headerFields.mapNotNull { (key, value) ->
                if (key != null) key to value else null
            }.toMap()

            val stream = if (isStatusCodeValid(responseCode)) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            // 1. Read raw binary bytes instead of a String
            val bytes = stream?.use { it.readBytes() }

            if (isStatusCodeValid(responseCode)) {
                // 2. Pass bytes to your response object (Change this param match your constructor)
                PageResponseObject(responseCode, headers, bytes = bytes)
            } else {
                // For errors, it's usually fine to convert bytes to text so you can read the error message
                val errorText = bytes?.decodeToString()
                PageResponseObject(responseCode, headers, text = errorText, error = "HTTP $responseCode")
            }
        } catch (e: Exception) {
            PageResponseObject(
                statusCode = -1,
                headers = emptyMap(),
                text = null,
                bytes = null, // Ensure safety initialization on crash
                error = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        } finally {
            connection?.disconnect()
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
