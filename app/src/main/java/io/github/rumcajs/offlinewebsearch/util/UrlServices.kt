package io.github.rumcajs.offlinewebsearch.util

// A common contract for all future URL services
interface LinkProvider {
    val serviceName: String
    fun generateLink(inputLink: String): String
}

// The Web Archive implementation
class WebArchiveProvider : LinkProvider {
    override val serviceName: String = "Web Archive"

    override fun generateLink(inputLink: String): String {
        // Encodes the URL to ensure it's safe for query parameters
        val encodedUrl = java.net.URLEncoder.encode(inputLink, "UTF-8")
        return "https://web.archive.org/web/$encodedUrl"
    }
}

class UrlServices {
    // Easily add more providers to this list in the future
    private val providers: List<LinkProvider> = listOf(
        WebArchiveProvider()
        // Future services go here, e.g., GoogleCacheProvider()
    )

    /**
     * Accepts a link and returns a list of Pairs containing (ServiceName, ServiceUrl)
     */
    fun getServiceLinks(inputLink: String): List<Pair<String, String>> {
        return providers.map { provider ->
            Pair(provider.serviceName, provider.generateLink(inputLink))
        }
    }
}
