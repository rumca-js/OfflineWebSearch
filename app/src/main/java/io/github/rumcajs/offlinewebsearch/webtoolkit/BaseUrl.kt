package io.github.rumcajs.offlinewebsearch.webtoolkit

/**
 * Class representing a base URL endpoint.
 * Fetches responses, retains PageResponseObject, and provides Page objects and PageHandlers.
 */
class BaseUrl(val url: String) {
    private var response: PageResponseObject? = null

    /**
     * Executes HTTP network request for the URL and captures/caches the resulting PageResponseObject.
     */
    suspend fun getResponse(
        acceptHeader: String? = "application/rss+xml, application/atom+xml, text/xml, application/json, */*"
    ): PageResponseObject {
        val resp = NetworkUtils.executeRequest(url, acceptHeader)
        this.response = resp
        return resp
    }

    /**
     * Gets the currently captured PageResponseObject if available without making a network request.
     */
    fun getCachedResponse(): PageResponseObject? = response

    /**
     * Returns a Page instance (HtmlPage or RssPage depending on content) built from the response.
     * If getResponse has not been called yet, it fetches it automatically.
     */
    suspend fun getPage(): Page {
        val resp = response ?: getResponse()
        val textContent = resp.text ?: ""
        val contentType = resp.contentType ?: ""
        return PageBuilder.build(url, textContent, contentType)
    }

    /**
     * Returns the title of the page parsed from the fetched contents.
     */
    suspend fun getTitle(): String? {
        return getPage().getTitle()
    }

    /**
     * Returns the description of the page parsed from the fetched contents.
     */
    suspend fun getDescription(): String? {
        return getPage().getDescription()
    }

    /**
     * Returns the publication date of the page parsed from the fetched contents.
     * Should be in format Year-Month-Day Hour:Minutes
     */
    suspend fun getDatePublished(): java.util.Date? {
        return getPage().getDatePublished()
    }

    /**
     * Returns the list of entries extracted from the page.
     */
    suspend fun getEntries(): List<io.github.rumcajs.offlinewebsearch.data.Entry> {
        return getPage().getEntries()
    }

    /**
     * Returns the list of thumbnail URLs extracted from the page.
     */
    suspend fun getThumbnails(): List<String> {
        return getPage().getThumbnails()
    }

    /**
     * Returns a PageHandler matching this URL if one is available from HandlerBuilder.
     */
    fun getPageHandler(): PageHandler? {
        return HandlerBuilder(url).build()
    }
}
