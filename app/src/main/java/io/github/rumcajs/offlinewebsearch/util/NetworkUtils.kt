package io.github.rumcajs.offlinewebsearch.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import io.github.rumcajs.offlinewebsearch.data.Entry

object NetworkUtils {
    suspend fun verifyUrl(urlString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val config = io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.value
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = config.connectTimeout
            connection.readTimeout = config.readTimeout
            connection.setRequestProperty("User-Agent", config.userAgent)
            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadAll(urlString: String): PageResponseObject =
        executeRequest(
            urlString,
            "application/rss+xml, application/atom+xml, text/xml, application/json, */*"
        )

    suspend fun getLinkPreview(urlString: String): PageResponseObject =
        executeRequest(urlString)

    private suspend fun executeRequest(
        urlString: String,
        acceptHeader: String? = null
    ): PageResponseObject = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val config = io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.value
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = config.connectTimeout
            connection.readTimeout = config.readTimeout
            connection.setRequestProperty("User-Agent", config.userAgent)
            if (acceptHeader != null) {
                connection.setRequestProperty("Accept", acceptHeader)
            }
            val responseCode = connection.responseCode
            val headers = connection.headerFields.mapNotNull { (key, value) ->
                if (key != null) key to value else null
            }.toMap()

            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.use { it.bufferedReader().readText() }

            if (responseCode in 200..299) {
                PageResponseObject(responseCode, headers, text)
            } else {
                PageResponseObject(responseCode, headers, text, "HTTP $responseCode")
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
}

data class PageResponseObject(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val text: String?,
    val error: String? = null
) {
    val contentType: String?
        get() = headers.entries.find { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()

    val length: Long
        get() = headers.entries.find { it.key.equals("Content-Length", ignoreCase = true) }?.value?.firstOrNull()?.toLongOrNull()
            ?: text?.toByteArray(Charsets.UTF_8)?.size?.toLong()
            ?: 0L
}
