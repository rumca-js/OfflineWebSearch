package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.HandlerBuilder
import io.github.rumcajs.offlinewebsearch.webtoolkit.OdyseeChannelHandler
import io.github.rumcajs.offlinewebsearch.webtoolkit.Page
import io.github.rumcajs.offlinewebsearch.webtoolkit.RedditChannelHandler
import io.github.rumcajs.offlinewebsearch.webtoolkit.YouTubeChannelHandler

/**
 * Component that displays page preview properties, metadata, channel info, and feed links
 * in the same style as [EntryMetadataPane].
 *
 * @param page The parsed web or feed page.
 * @param url The page URL.
 * @param isRestricted When true, sensitive metadata values are masked.
 * @param modifier Optional modifier.
 */
@Composable
fun PageMetadataPane(
    page: Page,
    url: String,
    isRestricted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Resolve channel and feed links from handler if applicable
        val handler = HandlerBuilder(url).build()
        val channel = handler?.getChannel() ?: ""
        val isChannel = handler is YouTubeChannelHandler || handler is RedditChannelHandler || handler is OdyseeChannelHandler

        if (channel.isNotEmpty() && !isChannel) {
            PropertiesPane(
                properties = listOf(
                    PropertyItem(label = "Channel", value = channel)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Combine feed links from both handler and page
        val allFeeds = (handler?.getFeeds().orEmpty() + page.getFeeds())
            .distinct()
            .filter { it.isNotBlank() && it != url }

        if (allFeeds.isNotEmpty()) {
            PropertiesPane(
                properties = allFeeds.map { feedUrl ->
                    PropertyItem(
                        label = "Feed Link",
                        value = feedUrl,
                        type = PropertyType.LINK,
                        isRestricted = isRestricted,
                        toastMessage = "Feed link copied"
                    )
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Page properties
        val datePublishedStr = page.getDatePublished()?.let { DateUtils.toIsoString(it) }
        val entries = page.getEntries()
        val thumbnails = page.getThumbnails()

        PropertiesPane(
            properties = buildList {
                add(PropertyItem(label = "Language", value = page.getLanguage() ?: "NA"))
                if (!datePublishedStr.isNullOrBlank()) {
                    add(PropertyItem(label = "Date Published", value = datePublishedStr))
                }
                if (entries.isNotEmpty()) {
                    add(PropertyItem(label = "Feed Entries", value = entries.size.toString()))
                }
                if (thumbnails.isNotEmpty()) {
                    add(PropertyItem(label = "Thumbnails", value = thumbnails.size.toString()))
                }
                page.getHash()?.let { hash ->
                    add(PropertyItem(label = "Content Hash", value = hash.joinToString("") { "%02x".format(it) }))
                }
                page.getMetaHash()?.let { metaHash ->
                    add(PropertyItem(label = "Meta Hash", value = metaHash.joinToString("") { "%02x".format(it) }))
                }
                page.getBodyHash()?.let { bodyHash ->
                    add(PropertyItem(label = "Body Hash", value = bodyHash.joinToString("") { "%02x".format(it) }))
                }
            }
        )
    }
}
