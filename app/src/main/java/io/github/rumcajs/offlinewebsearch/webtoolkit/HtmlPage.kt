package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.Entry

class HtmlPage(val url: String, val contents: String) : Page {
    private var title: String? = null
    private var description: String? = null
    private val thumbnails = mutableListOf<String>()
    private var datePublished: String? = null

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
                        "og:image", "og:image:url", "og:image:secure_url" -> {
                            thumbnails.add(unescapedContent)
                        }
                        "og:article:published_time", "article:published_time", "og:pubdate", "og:publish_date" -> {
                            if (datePublished == null) datePublished = unescapedContent
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

    // TODO write test for it
    // TODO make it getFeeds, returns list, consistent with API
    fun getFeed(): String? {
        if (contents.isBlank()) return null

        // Regex to match any <link ...> tag in a case-insensitive manner
        val linkTagRegex = """<link\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)

        linkTagRegex.findAll(contents).forEach { matchResult ->
            val tag = matchResult.value
            val type = getAttrValue(tag, "type")
            val rel = getAttrValue(tag, "rel")

            // RSS links have type="application/rss+xml" (or sometimes type="application/atom+xml")
            // and usually rel="alternate"
            if (type?.lowercase() == "application/rss+xml" || type?.lowercase() == "application/atom+xml") {
                val href = getAttrValue(tag, "href")
                if (href != null) {
                    return resolveUrl(unescapeHtml(href))
                }
            }
        }
        return null
    }

    override fun getTitle(): String? = title
    override fun getDescription(): String? = description
    override fun getThumbnails(): List<String> = thumbnails
    override fun getDatePublished(): String? = datePublished
    override fun getEntries(): List<Entry> = emptyList()
}

