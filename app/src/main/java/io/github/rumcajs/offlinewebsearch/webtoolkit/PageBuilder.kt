package io.github.rumcajs.offlinewebsearch.webtoolkit

object PageBuilder {
    fun build(url: String, contents: String, inputType: String): Page {
        return when (inputType.lowercase()) {
            "html", "text/html" -> HtmlPage(url, contents)
            "rss", "xml", "application/rss+xml", "application/atom+xml", "atom" -> RssPage(url, contents)
            else -> {
                val isHtml = url.endsWith(".html") || url.endsWith(".htm") ||
                        contents.trim().startsWith("<html", ignoreCase = true) ||
                        contents.trim().contains("<!doctype html", ignoreCase = true)
                if (isHtml) {
                    HtmlPage(url, contents)
                } else {
                    RssPage(url, contents)
                }
            }
        }
    }
}
