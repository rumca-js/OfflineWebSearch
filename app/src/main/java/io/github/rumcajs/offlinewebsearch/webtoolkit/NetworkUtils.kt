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

    suspend fun executeRequest(
        urlString: String,
        acceptHeader: String? = "application/rss+xml, application/atom+xml, text/xml, application/json, */*"
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
                .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache, no-store")
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
