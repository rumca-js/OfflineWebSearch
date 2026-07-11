package io.github.rumcajs.offlinewebsearch.handler

import io.github.rumcajs.offlinewebsearch.util.UrlLocation
import java.net.URI

/**
 * Interface representing a page handler that checks if a link can be handled.
 */
interface PageHandler {
    fun isHandledBy(): Boolean
    fun getFeeds(): List<String> = emptyList()
    fun getUrl(): String
    fun getChannel(): String = ""
}

/**
 * Handles YouTube videos.
 */
class YouTubeVideoHandler(private val link: String) : PageHandler {
    override fun getUrl(): String = link
    override fun getChannel(): String = ""
    override fun isHandledBy(): Boolean = getVideoId() != null

    fun getVideoId(): String? {
        val domain = UrlLocation.getDomain(link)
        if (domain == "youtu.be") {
            val path = getPath(link)
            val segments = path.split("/").filter { it.isNotEmpty() }
            return segments.firstOrNull()
        }
        if (domain == "youtube.com" || domain == "www.youtube.com" || domain == "m.youtube.com") {
            val path = getPath(link)
            if (path.startsWith("/watch")) {
                return getQueryParameter(link, "v")
            }
            if (path.startsWith("/embed/")) {
                val segments = path.split("/").filter { it.isNotEmpty() }
                return segments.getOrNull(1)
            }
            if (path.startsWith("/shorts/")) {
                val segments = path.split("/").filter { it.isNotEmpty() }
                return segments.getOrNull(1)
            }
        }
        return null
    }


    private fun getPath(link: String): String {
        return try {
            val adjusted = if (!link.contains("://")) "http://$link" else link
            URI(adjusted).path ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getQueryParameter(link: String, key: String): String? {
        return try {
            val adjusted = if (!link.contains("://")) "http://$link" else link
            val query = URI(adjusted).query ?: return null
            query.split("&")
                .map { it.split("=") }
                .firstOrNull { it.size == 2 && it[0] == key }
                ?.get(1)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Handles YouTube channels.
 */
class YouTubeChannelHandler(private val link: String) : PageHandler {
    val channelUid: String? = linkToUid(link)

    override fun getUrl(): String = link

    fun getChannelUrl(): String? {
        return channelUid?.let { "https://www.youtube.com/channel/$it" }
    }

    override fun getChannel(): String = getChannelUrl() ?: ""

    override fun isHandledBy(): Boolean {
        val domain = UrlLocation.getDomain(link)
        if (domain == "youtube.com" || domain == "www.youtube.com" || domain == "m.youtube.com") {
            val path = getPath(link)
            return path.startsWith("/channel/") ||
                    path.startsWith("/c/") ||
                    path.startsWith("/user/") ||
                    path.startsWith("/@")
        }
        return false
    }

    override fun getFeeds(): List<String> {
        return if (channelUid != null) {
            listOf("https://www.youtube.com/feeds/videos.xml?channel_id=$channelUid")
        } else {
            emptyList()
        }
    }

    private fun getPath(link: String): String {
        return try {
            val adjusted = if (!link.contains("://")) "http://$link" else link
            URI(adjusted).path ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        @JvmStatic
        fun getUidFromChannelLink(link: String): String? {
            val path = try {
                val adjusted = if (!link.contains("://")) "http://$link" else link
                URI(adjusted).path ?: ""
            } catch (e: Exception) {
                ""
            }
            if (path.startsWith("/channel/")) {
                val uid = path.substringAfter("/channel/").split("/").firstOrNull { it.isNotEmpty() }
                if (!uid.isNullOrEmpty()) {
                    return uid
                }
            }
            return null
        }

        @JvmStatic
        fun getUidFromRssLink(link: String): String? {
            return try {
                val adjusted = if (!link.contains("://")) "http://$link" else link
                val uri = URI(adjusted)
                val query = uri.query ?: return null
                query.split("&")
                    .map { it.split("=") }
                    .firstOrNull { it.size == 2 && it[0] == "channel_id" }
                    ?.get(1)
            } catch (e: Exception) {
                null
            }
        }

        @JvmStatic
        fun linkToUid(link: String): String? {
            return getUidFromChannelLink(link) ?: getUidFromRssLink(link)
        }
    }
}

/**
 * Handles GitHub repositories.
 */
class GitHubRepositoryHandler(private val link: String) : PageHandler {
    override fun getUrl(): String = link

    override fun getChannel(): String {
        val path = getPath(link)
        val segments = path.split("/").filter { it.isNotEmpty() }
        return if (segments.isNotEmpty()) segments[0] else ""
    }

    override fun isHandledBy(): Boolean {
        val domain = UrlLocation.getDomain(link)
        if (domain == "github.com" || domain == "www.github.com") {
            val path = getPath(link)
            val segments = path.split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2) {
                val owner = segments[0]
                val reservedNames = setOf(
                    "features", "pricing", "security", "customer-stories",
                    "resources", "about", "blog", "readme", "support", "contact",
                    "login", "signup", "join", "search", "trending", "explore",
                    "marketplace", "notifications", "settings", "issues", "pulls"
                )
                return owner.lowercase() !in reservedNames
            }
        }
        return false;
    }

    override fun getFeeds(): List<String> {
        val domain = UrlLocation.getDomain(link)
        val path = getPath(link)
        val segments = path.split("/").filter { it.isNotEmpty() }
        if (segments.size >= 2) {
            val owner = segments[0]
            val repo = segments[1]
            val host = if (domain.isNotEmpty()) domain else "github.com"
            return listOf(
                "https://$host/$owner/$repo/commits.atom",
                "https://$host/$owner/$repo/releases.atom"
            )
        }
        return emptyList()
    }

    private fun getPath(link: String): String {
        return try {
            val adjusted = if (!link.contains("://")) "http://$link" else link
            URI(adjusted).path ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Handles Reddit subreddits and user feeds.
 */
class RedditChannelHandler(private val link: String) : PageHandler {
    override fun getUrl(): String = link

    override fun getChannel(): String {
        val path = getPath(link)
        return when {
            path.startsWith("/r/") -> path.substringAfter("/r/").split("/").firstOrNull { it.isNotEmpty() } ?: ""
            path.startsWith("/user/") -> path.substringAfter("/user/").split("/").firstOrNull { it.isNotEmpty() } ?: ""
            path.startsWith("/u/") -> path.substringAfter("/u/").split("/").firstOrNull { it.isNotEmpty() } ?: ""
            else -> ""
        }
    }

    override fun isHandledBy(): Boolean {
        val domain = UrlLocation.getDomain(link)
        if (domain == "reddit.com" || domain == "www.reddit.com" || domain == "old.reddit.com") {
            val path = getPath(link)
            val isChannelPath = path.startsWith("/r/") || path.startsWith("/user/") || path.startsWith("/u/")
            val isPost = path.contains("/comments/")
            return isChannelPath && !isPost
        }
        return false
    }

    override fun getFeeds(): List<String> {
        val domain = UrlLocation.getDomain(link)
        val path = getPath(link)
        val cleanPath = path.removeSuffix("/")
        if (cleanPath.startsWith("/r/") || cleanPath.startsWith("/user/") || cleanPath.startsWith("/u/")) {
            val host = if (domain.isNotEmpty()) domain else "www.reddit.com"
            return listOf("https://$host$cleanPath/.rss")
        }
        return emptyList()
    }

    private fun getPath(link: String): String {
        return try {
            val adjusted = if (!link.contains("://")) "http://$link" else link
            URI(adjusted).path ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Builder class that registers handlers and matches the first handler that handles the link.
 */
class HandlerBuilder(private val link: String) {
    private val handlers = mutableListOf<PageHandler>()

    fun registerHandler(handler: PageHandler): HandlerBuilder {
        handlers.add(handler)
        return this
    }

    fun build(): PageHandler? {
        val activeHandlers = if (handlers.isEmpty()) {
            listOf(
                YouTubeVideoHandler(link),
                YouTubeChannelHandler(link),
                GitHubRepositoryHandler(link),
                RedditChannelHandler(link)
            )
        } else {
            handlers
        }
        return activeHandlers.firstOrNull { it.isHandledBy() }
    }
}
