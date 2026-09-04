package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.util.EntryUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.HandlerBuilder
import io.github.rumcajs.offlinewebsearch.webtoolkit.OdyseeChannelHandler
import io.github.rumcajs.offlinewebsearch.webtoolkit.RedditChannelHandler
import io.github.rumcajs.offlinewebsearch.webtoolkit.YouTubeChannelHandler

/**
 * Component that displays entry detail properties, metadata, channel info, feed links, and UrlServices links.
 */
@Composable
fun EntryMetadataPane(
    entry: Entry,
    isRestricted: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by AppConfigManager.config.collectAsState()

    val displayAuthor by produceState<String?>(initialValue = entry.author?.takeIf { it.isNotBlank() }, key1 = entry, key2 = config.activeDatabaseState) {
        value = EntryUtils.getDisplayAuthor(entry, context, config.activeDatabaseState)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Resolve channel and feed links from handler, rendered as PropertiesPane sections
        entry.link?.let { link ->
            val handler = HandlerBuilder(link).build()

            // Display channel if available and entry is not itself a channel page
            val channel = handler?.getChannel() ?: ""
            val isChannel = handler is YouTubeChannelHandler || handler is RedditChannelHandler || handler is OdyseeChannelHandler
            if (channel.isNotEmpty() && !isChannel) {
                PropertiesPane(
                    properties = listOf(
                        PropertyItem(label = "Channel", value = channel)
                    )
                )
            }

            // Display feed links (excluding the entry link itself)
            val feeds = handler?.getFeeds()?.filter { it != link } ?: emptyList()
            if (feeds.isNotEmpty()) {
                PropertiesPane(
                    properties = feeds.map { feedUrl ->
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
        }

        // Entry detail properties and metadata
        PropertiesPane(
            properties = buildList {
                add(PropertyItem(label = "Created", value = EntryUtils.getFormattedDate(entry.date_created)))
                add(PropertyItem(label = "Dead", value = EntryUtils.getFormattedDate(entry.date_dead_since)))
                add(PropertyItem(label = "Bookmarked", value = if (entry.bookmarked == true) "Yes" else "No"))
                add(PropertyItem(label = "Author", value = displayAuthor ?: "NA"))
                add(PropertyItem(label = "Album", value = entry.album ?: "NA"))
                add(PropertyItem(label = "Language", value = entry.language ?: "NA"))
                add(PropertyItem(label = "Rating", value = EntryUtils.getFormattedRating(entry)))
                add(PropertyItem(label = "Votes", value = EntryUtils.getFormattedVotes(entry)))
                add(PropertyItem(label = "Visits", value = EntryUtils.getFormattedVisits(entry)))
                add(PropertyItem(label = "Status Code", value = (entry.status_code ?: 0).toString()))
                add(PropertyItem(label = "Manual Status Code", value = (entry.manual_status_code ?: 0).toString()))
                entry.thumbnail?.let { thumbUrl ->
                    add(
                        PropertyItem(
                            label = "Thumbnail",
                            value = thumbUrl,
                            type = PropertyType.LINK,
                            isRestricted = isRestricted,
                            toastMessage = "Thumbnail link copied"
                        )
                    )
                }
            }
        )
    }
}
