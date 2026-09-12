package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import java.security.MessageDigest
import java.util.Date

class HtmlPage(val url: String, val contents: String) : Page {
    private var title: String? = null
    private var description: String? = null
    private var language: String? = null
    private val thumbnails = mutableListOf<String>()
    private var datePublished: Date? = null

    init {
        if (contents.isNotBlank()) {
            val metaTagRegex = """<meta\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)
            metaTagRegex.findAll(contents).forEach { matchResult ->
                val tag = matchResult.value
                val property = getAttrValue(tag, "property") ?: getAttrValue(tag, "name")
                val content = getAttrValue(tag, "content")
                if (property != null && content != null) {
                    val unescapedContent = unescapeHtml(content)
                    when (property.lowercase()) {
                        "og:title" -> {
                            if (title == null) title = unescapedContent
                        }
                        "og:description" -> {
                            if (description == null) description = unescapedContent
                        }
                        "og:locale" -> {
                            if (language == null) language = unescapedContent
                        }
                        "og:image", "og:image:url", "og:image:secure_url" -> {
                            thumbnails.add(unescapedContent)
                        }
                        "og:article:published_time", "article:published_time", "og:pubdate", "og:publish_date" -> {
                            if (datePublished == null) datePublished = DateUtils.parseDateString(unescapedContent)
                        }
                    }
                }
            }
        }
    }

    private fun getAttrValue(tag: String, attrName: String): String? {
        val regex = """\b$attrName\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(tag) ?: return null
        return match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    /**
     * Resolves relative URLs against the base site URL if necessary.
     */
    private fun resolveUrl(path: String): String {
        return try {
            val baseUri = java.net.URI(url)
            baseUri.resolve(path).toString()
        } catch (e: Exception) {
            path // Fallback to raw string if URI resolution fails
        }
    }

    /**
     * Finds and returns all RSS/Atom feed links declared in `<link>` tags.
     * Resolves relative URLs to absolute URLs against the page URL.
     */
    override fun getFeeds(): List<String> {
        if (contents.isBlank()) return emptyList()

        val feeds = mutableListOf<String>()
        // Regex to match any <link ...> tag in a case-insensitive manner
        val linkTagRegex = """<link\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)

        linkTagRegex.findAll(contents).forEach { matchResult ->
            val tag = matchResult.value
            val type = getAttrValue(tag, "type")?.lowercase()
            val rel = getAttrValue(tag, "rel")?.lowercase()

            // RSS links have type="application/rss+xml", "application/atom+xml", etc.
            val isFeedType = type == "application/rss+xml" ||
                    type == "application/atom+xml" ||
                    type == "application/rdf+xml" ||
                    type == "application/feed+json" ||
                    (type == "application/json" && rel == "alternate") ||
                    (type == "text/xml" && (rel == "alternate" || tag.contains("rss", ignoreCase = true)))

            if (isFeedType) {
                val href = getAttrValue(tag, "href")
                if (!href.isNullOrBlank()) {
                    val resolved = resolveUrl(unescapeHtml(href))
                    if (!feeds.contains(resolved)) {
                        feeds.add(resolved)
                    }
                }
            }
        }
        return feeds
    }

    override fun getTitle(): String? = title
    override fun getDescription(): String? = description
    override fun getLanguage(): String? = language
    override fun getThumbnails(): List<String> = thumbnails
    override fun getDatePublished(): Date? = datePublished
    override fun getEntries(): List<Entry> = emptyList()

    /**
     * Returns a SHA-256 hash of the entire response text (full [contents]).
     */
    override fun getHash(): ByteArray? {
        if (contents.isBlank()) return null
        return sha256(contents)
    }

    /**
     * Returns a SHA-256 hash of the `<body>` section of the page,
     * or null if no body section is found or content is blank.
     */
    override fun getBodyHash(): ByteArray? {
        val body = extractBody() ?: return null
        return sha256(body)
    }

    /**
     * Returns a SHA-256 hash of all concatenated `<meta>` tags in the page,
     * or null if no meta tags are found or content is blank.
     */
    override fun getMetaHash(): ByteArray? {
        val meta = extractMeta() ?: return null
        return sha256(meta)
    }

    /**
     * Extracts the raw text between `<body>` and `</body>` tags (inclusive),
     * returning null if no body section is present.
     */
    private fun extractBody(): String? {
        if (contents.isBlank()) return null
        val bodyRegex = """(?is)<body[\s>].*?</body>""".toRegex()
        return bodyRegex.find(contents)?.value
    }

    /**
     * Concatenates all `<meta ...>` tag strings found in the document,
     * returning null if there are none.
     */
    private fun extractMeta(): String? {
        if (contents.isBlank()) return null
        val metaTagRegex = """<meta\s+[^>]+>""".toRegex(RegexOption.IGNORE_CASE)
        val tags = metaTagRegex.findAll(contents).map { it.value }.toList()
        return if (tags.isEmpty()) null else tags.joinToString(separator = "")
    }

    /**
     * Computes and returns a SHA-256 digest of the given [text].
     */
    private fun sha256(text: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
}

