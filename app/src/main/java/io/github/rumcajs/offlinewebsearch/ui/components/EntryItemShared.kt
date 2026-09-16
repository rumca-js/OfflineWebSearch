package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.util.EntryUtils

/**
 * Common card wrapper for entry list items handling alpha calculation,
 * direct link opening, and item elevation.
 *
 * @param entry The database entry being displayed.
 * @param onClick Callback triggered when item is tapped in non-direct link mode.
 * @param modifier Optional modifier for styling.
 * @param content Composable column content of the card.
 */
@Composable
fun EntryItemCard(
    entry: Entry,
    onClick: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val config by AppConfigManager.config.collectAsState()
    val isDead = EntryUtils.isDead(entry)
    val isVisited = (entry.page_rating_visits ?: 0) > 0
    val isBookmarked = entry.bookmarked == true

    val itemAlpha = when {
        isBookmarked -> 1f
        (entry.page_rating_votes ?: 0) > 0 -> 1f
        isDead && isVisited -> config.dbconfig.entriesDeadAlpha * config.dbconfig.entriesVisitAlpha
        isDead -> config.dbconfig.entriesDeadAlpha
        isVisited -> config.dbconfig.entriesVisitAlpha
        else -> 1f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(itemAlpha)
            .clickable(enabled = entry.link != null || !config.dbconfig.directLinks) {
                if (config.dbconfig.directLinks) {
                    entry.link?.let { uriHandler.openUri(it) }
                } else {
                    onClick(entry)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

/**
 * Displays status badges for an entry (bookmark icon, dead indicator, age restriction, votes).
 *
 * @param entry The entry to render badges for.
 * @param isDead Whether the entry is marked as dead.
 * @param modifier Optional modifier.
 */
@Composable
fun EntryBadges(
    entry: Entry,
    isDead: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.bookmarked == true) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Bookmarked",
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (isDead) {
            Text(
                text = "💀",
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        if ((entry.age ?: 0) > 0) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "A",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
        entry.page_rating_votes?.takeIf { it > 0 }?.let { votes ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = "⭐ $votes",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Renders small thumbnail image or fallback link icon on the left side of entry items.
 *
 * @param entry The entry providing the thumbnail or link.
 * @param showIcons Whether icon display is enabled in settings.
 * @param userAge The user age setting for content filtering.
 * @param modifier Optional modifier.
 */
@Composable
fun EntryLeadingIcon(
    entry: Entry,
    showIcons: Boolean,
    userAge: Int,
    modifier: Modifier = Modifier
) {
    if (!showIcons) return

    if (!entry.thumbnail.isNullOrBlank()) {
        RemoteImage(
            url = entry.thumbnail,
            modifier = modifier
                .size(48.dp)
                .padding(end = 8.dp),
            showErrorText = false,
            isRestricted = EntryUtils.isRestricted(entry, userAge)
        )
    } else if (entry.link != null) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = modifier
                .size(48.dp)
                .padding(end = 8.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Displays published date and author in a space-between row.
 *
 * @param datePublished Raw date string of publication.
 * @param displayAuthor Computed display author string.
 * @param isRestricted Whether age restriction masks the text with "xXx".
 * @param modifier Optional modifier.
 */
@Composable
fun EntryMetaRow(
    datePublished: String?,
    displayAuthor: String?,
    isRestricted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        datePublished?.let {
            Text(
                text = if (isRestricted) "xXx" else EntryUtils.getFormattedDate(it),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        displayAuthor?.let { authorText ->
            Text(
                text = if (isRestricted) "xXx" else authorText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Displays tag chips in a wrapping FlowRow.
 *
 * @param tags List of tags to display.
 * @param isRestricted Whether age restriction masks tag text with "xXx".
 * @param modifier Optional modifier.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryTagsPane(
    tags: List<String>?,
    isRestricted: Boolean,
    modifier: Modifier = Modifier
) {
    if (tags.isNullOrEmpty()) return

    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.forEach { tag ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (isRestricted) "xXx" else tag,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
