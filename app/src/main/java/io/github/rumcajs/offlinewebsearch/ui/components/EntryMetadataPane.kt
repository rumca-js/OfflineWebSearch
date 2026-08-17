package io.github.rumcajs.offlinewebsearch.ui.components

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.ui.screens.DetailRow
import io.github.rumcajs.offlinewebsearch.ui.screens.LinkRow
import io.github.rumcajs.offlinewebsearch.util.EntryUtils
import io.github.rumcajs.offlinewebsearch.util.UrlServices
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
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Display channel
        entry.link?.let { link ->
            val handler = HandlerBuilder(link).build()
            val channel = handler?.getChannel() ?: ""
            val isChannel = handler is YouTubeChannelHandler || handler is RedditChannelHandler || handler is OdyseeChannelHandler
            if (channel.isNotEmpty() && !isChannel) {
                DetailRow(
                    label = "Channel",
                    value = channel
                )
            }
        }

        // Resolve and display feeds
        entry.link?.let { link ->
            val handler = HandlerBuilder(link).build()
            val feeds = handler?.getFeeds()?.filter { it != link } ?: emptyList()
            if (feeds.isNotEmpty()) {
                feeds.forEach { feedUrl ->
                    LinkRow(
                        label = "Feed Link",
                        url = feedUrl,
                        isRestricted = isRestricted,
                        toastMessage = "Feed link copied"
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Entry detail properties, metadata
        DetailRow(
            label = "Created",
            value = EntryUtils.getFormattedDate(entry.date_created)
        )
        DetailRow(
            label = "Dead",
            value = EntryUtils.getFormattedDate(entry.date_dead_since)
        )
        DetailRow(
            label = "Bookmarked",
            value = if (entry.bookmarked == true) "Yes" else "No"
        )

        DetailRow(
            label = "Author",
            value = entry.author ?: "NA"
        )
        DetailRow(
            label = "Album",
            value = entry.album ?: "NA"
        )
        DetailRow(
            label = "Language",
            value = entry.language ?: "NA"
        )

        DetailRow(
            label = "Rating",
            value = EntryUtils.getFormattedRating(entry)
        )
        DetailRow(
            label = "Votes",
            value = EntryUtils.getFormattedVotes(entry)
        )
        DetailRow(
            label = "Visits",
            value = EntryUtils.getFormattedVisits(entry)
        )

        DetailRow(
            label = "Status Code",
            value = (entry.status_code ?: 0).toString()
        )
        DetailRow(
            label = "Manual Status Code",
            value = (entry.manual_status_code ?: 0).toString()
        )

        entry.thumbnail?.let { thumbUrl ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .pointerInput(thumbUrl) {
                        detectTapGestures(
                            onLongPress = {
                                clipboardManager.setText(AnnotatedString(thumbUrl))
                                Toast.makeText(context, "Thumbnail link copied", Toast.LENGTH_SHORT).show()
                            },
                            onTap = {
                                uriHandler.openUri(thumbUrl)
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Thumbnail", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                Text(
                    text = "Link (Long press to copy)",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    fontSize = 14.sp
                )
            }
        }

        // Resolve and display UrlServices links
        entry.link?.let { link ->
            val urlServices = UrlServices()
            val serviceLinks = urlServices.getServiceLinks(link)
            if (serviceLinks.isNotEmpty()) {
                serviceLinks.forEach { (serviceName, serviceUrl) ->
                    LinkRow(
                        label = serviceName,
                        url = serviceUrl,
                        isRestricted = isRestricted,
                        toastMessage = "$serviceName link copied"
                    )
                }
            }
        }
    }
}
