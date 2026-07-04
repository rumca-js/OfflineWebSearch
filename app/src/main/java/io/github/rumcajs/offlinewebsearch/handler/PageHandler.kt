package io.github.rumcajs.offlinewebsearch.handler

import io.github.rumcajs.offlinewebsearch.util.UrlLocation
import java.net.URI

/**
 * Interface representing a page handler that checks if a link can be handled.
 */
interface PageHandler {
    fun isHandledBy(link: String): Boolean
    fun getFeeds(link: String): List<String> = emptyList()
}

/**
 * Handles YouTube videos.
 */
class YouTubeVideoHandler : PageHandler {
    override fun isHandledBy(link: String): Boolean {
        val domain = UrlLocation.getDomain(link)
        if (domain == "youtu.be") {
            val path = getPath(link)
            return path.isNotEmpty() && path != "/"
        }
        if (domain == "youtube.com" || domain == "www.youtube.com" || domain == "m.youtube.com") {
            val path = getPath(link)
            if (path.startsWith("/watch")) {
                return getQueryParameter(link, "v") != null
            }
            if (path.startsWith("/embed/")) {
                return true
            }
            if (path.startsWith("/shorts/")) {
                return true
            }
        }
        return false
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
class YouTubeChannelHandler : PageHandler {
    override fun isHandledBy(link: String): Boolean {
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

    override fun getFeeds(link: String): List<String> {
        val path = getPath(link)
        if (path.startsWith("/channel/")) {
            val channelId = path.substringAfter("/channel/").split("/").firstOrNull { it.isNotEmpty() }
            if (!channelId.isNullOrEmpty()) {
                return listOf("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            }
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
 * Handles GitHub repositories.
 */
class GitHubRepositoryHandler : PageHandler {
    override fun isHandledBy(link: String): Boolean {
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
        return false
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
                YouTubeVideoHandler(),
                YouTubeChannelHandler(),
                GitHubRepositoryHandler()
            )
        } else {
            handlers
        }
        return activeHandlers.firstOrNull { it.isHandledBy(link) }
    }
}
