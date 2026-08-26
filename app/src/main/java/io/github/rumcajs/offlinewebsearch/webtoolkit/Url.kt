package io.github.rumcajs.offlinewebsearch.webtoolkit

import io.github.rumcajs.offlinewebsearch.data.Entry

/**
 * Open class representing a URL endpoint, delegating core functionality to [BaseUrl].
 * Designed to allow extension by specialized classes such as RemoteUrl in the future.
 *
 * @property url The string representation of the target URL.
 */
open class Url(val url: String) {
    protected val baseUrl: BaseUrl = BaseUrl(url)

    /**
     * Executes HTTP network request for the URL and captures/caches the resulting PageResponseObject.
     */
    open suspend fun getResponse(
        acceptHeader: String? = "application/rss+xml, application/atom+xml, text/xml, application/json, */*"
    ): PageResponseObject {
        return baseUrl.getResponse(acceptHeader)
    }

    /**
     * Gets the currently captured PageResponseObject if available without making a network request.
     */
    open fun getCachedResponse(): PageResponseObject? = baseUrl.getCachedResponse()

    /**
     * Returns a Page instance (HtmlPage or RssPage depending on content) built from the response.
     * If getResponse has not been called yet, it fetches it automatically.
     */
    open suspend fun getPage(): Page {
        return baseUrl.getPage()
    }

    /**
     * Returns the title of the page parsed from the fetched contents.
     */
    open suspend fun getTitle(): String? {
        return baseUrl.getTitle()
    }

    /**
     * Returns the description of the page parsed from the fetched contents.
     */
    open suspend fun getDescription(): String? {
        return baseUrl.getDescription()
    }

    /**
     * Returns the publication date of the page parsed from the fetched contents.
     * Should be in format Year-Month-Day Hour:Minutes
     */
    open suspend fun getDatePublished(): java.util.Date? {
        return baseUrl.getDatePublished()
    }

    /**
     * Returns the list of entries extracted from the page.
     */
    open suspend fun getEntries(): List<Entry> {
        return baseUrl.getEntries()
    }

    /**
     * Returns the list of thumbnail URLs extracted from the page.
     */
    open suspend fun getThumbnails(): List<String> {
        return baseUrl.getThumbnails()
    }

    /**
     * Returns a PageHandler matching this URL if one is available from HandlerBuilder.
     */
    open fun getPageHandler(): PageHandler? {
        return baseUrl.getPageHandler()
    }
}
