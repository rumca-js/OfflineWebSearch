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

    /**
     * Fetches [urlString] and parses it as either:
     *  - The app's native JSON format (list of Entry or single Entry)
     *  - RSS 2.0 / Atom XML feed
     * Returns a pair of (entries, errorMessage). On success errorMessage is null.
     */
    suspend fun fetchRssEntries(urlString: String): Pair<List<Entry>, String?> =
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
                    return@withContext Pair(emptyList(), "HTTP ${connection.responseCode}")
                }

                val body = connection.inputStream.bufferedReader().readText()
                val trimmed = body.trimStart()

                // Try native JSON first
                if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                    return@withContext tryParseJson(body)
                }

                // Fall back to XML (RSS 2.0 / Atom)
                return@withContext tryParseXml(body)
            } catch (e: Exception) {
                Pair(emptyList(), e.localizedMessage ?: e.javaClass.simpleName)
            } finally {
                connection?.disconnect()
            }
        }

    // ── JSON ──────────────────────────────────────────────────────────────────

    private fun tryParseJson(body: String): Pair<List<Entry>, String?> {
        return try {
            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            val entries: List<Entry> = try {
                json.decodeFromString(body)
            } catch (_: Exception) {
                listOf(json.decodeFromString<Entry>(body))
            }
            Pair(entries, null)
        } catch (e: Exception) {
            Pair(emptyList(), "JSON parse error: ${e.localizedMessage}")
        }
    }

    // ── XML (RSS 2.0 / Atom) ──────────────────────────────────────────────────

    private fun tryParseXml(body: String): Pair<List<Entry>, String?> {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(body.reader())

            val entries = mutableListOf<Entry>()
            var isAtom = false
            var inEntry = false
            var title: String? = null
            var link: String? = null
            var description: String? = null
            var author: String? = null
            var pubDate: String? = null
            var thumbnail: String? = null
            var currentText = StringBuilder()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name ?: ""
                        if (tag == "feed") isAtom = true

                        val isItemTag = tag == "item" || (isAtom && tag == "entry")
                        if (isItemTag) {
                            inEntry = true
                            title = null; link = null; description = null
                            author = null; pubDate = null; thumbnail = null
                        }

                        // Atom: link is an attribute
                        if (isAtom && tag == "link" && inEntry) {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null && link == null) link = href
                        }

                        // media:thumbnail or enclosure url attribute
                        if ((tag == "thumbnail" || tag == "enclosure") && inEntry) {
                            val url = parser.getAttributeValue(null, "url")
                            if (url != null && thumbnail == null) thumbnail = url
                        }

                        currentText = StringBuilder()
                    }

                    XmlPullParser.TEXT -> currentText.append(parser.text ?: "")

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name ?: ""
                        val text = currentText.toString().trim()

                        if (inEntry) {
                            when (tag) {
                                "title" ->
                                    if (title == null) title = text.ifEmpty { null }
                                "link" ->
                                    if (!isAtom && link == null) link = text.ifEmpty { null }
                                "description", "summary", "content", "content:encoded" ->
                                    if (description == null) description = text.ifEmpty { null }
                                "author", "dc:creator", "name" ->
                                    if (author == null) author = text.ifEmpty { null }
                                "pubDate", "published", "updated", "dc:date" ->
                                    if (pubDate == null) pubDate = text.ifEmpty { null }
                                "url" ->
                                    if (thumbnail == null) thumbnail = text.ifEmpty { null }
                            }

                            val isEndItemTag = tag == "item" || (isAtom && tag == "entry")
                            if (isEndItemTag) {
                                entries.add(
                                    Entry(
                                        link = link,
                                        title = title,
                                        description = description,
                                        author = author,
                                        thumbnail = thumbnail,
                                        date_published = pubDate,
                                        date_created = pubDate,
                                    )
                                )
                                inEntry = false
                            }
                        }

                        currentText = StringBuilder()
                    }
                }
                eventType = parser.next()
            }
            Pair(entries, null)
        } catch (e: Exception) {
            Pair(emptyList(), "XML parse error: ${e.localizedMessage}")
        }
    }
}

data class LinkPreviewResult(
    val statusCode: Int,
    val length: Long,
    val contentType: String? = null,
    val error: String? = null
)
