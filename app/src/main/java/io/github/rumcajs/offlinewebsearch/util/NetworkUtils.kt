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

    suspend fun downloadFile(urlString: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val config = io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.value
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = config.connectTimeout
            connection.readTimeout = config.readTimeout
            connection.setRequestProperty("User-Agent", config.userAgent)
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getLinkPreview(urlString: String): LinkPreviewResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val config = io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.value
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = config.connectTimeout
            connection.readTimeout = config.readTimeout
            connection.setRequestProperty("User-Agent", config.userAgent)

            val responseCode = connection.responseCode

            var length = connection.contentLengthLong
            if (length < 0) {
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val bytes = stream?.readBytes()
                length = bytes?.size?.toLong() ?: 0L
            }

            LinkPreviewResult(statusCode = responseCode, length = length, contentType = connection.contentType)
        } catch (e: Exception) {
            LinkPreviewResult(
                statusCode = -1,
                length = 0L,
                error = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchRawContent(urlString: String): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val config = io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.value
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = config.connectTimeout
                connection.readTimeout = config.readTimeout
                connection.setRequestProperty("User-Agent", config.userAgent)
                connection.setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/atom+xml, text/xml, application/json, */*"
                )

                if (connection.responseCode !in 200..299) {
                    return@withContext Pair(null, "HTTP ${connection.responseCode}")
                }

                val body = connection.inputStream.bufferedReader().readText()
                Pair(body, null)
            } catch (e: Exception) {
                Pair(null, e.localizedMessage ?: e.javaClass.simpleName)
            } finally {
                connection?.disconnect()
            }
        }
}

data class LinkPreviewResult(
    val statusCode: Int,
    val length: Long,
    val contentType: String? = null,
    val error: String? = null
)
