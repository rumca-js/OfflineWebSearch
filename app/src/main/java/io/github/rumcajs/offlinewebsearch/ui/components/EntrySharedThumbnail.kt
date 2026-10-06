package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.util.EntryUtils

/**
 * Leading thumbnail component rendered on the left of entry items in Standard and Search Engine views.
 *
 * Displays:
 * - A predefined vector icon when [sourceFavicon] or [entry.thumbnail] is a predefined icon identifier.
 * - A remote thumbnail image via [RemoteImage] when a URL is present.
 * - A fallback link icon ([Icons.Default.Link]) when no thumbnail/favicon is available but [entry.link] is non-null.
 * - Nothing when [showIcons] is false.
 *
 * @param entry The entry providing thumbnail and link information.
 * @param showIcons Whether icon display is enabled in configuration.
 * @param userAge The user age setting for content restriction.
 * @param sourceFavicon Optional source favicon to fallback to when entry thumbnail is missing.
 * @param modifier Optional layout modifier.
 */
@Composable
fun EntrySharedThumbnail(
    entry: Entry,
    showIcons: Boolean,
    userAge: Int,
    sourceFavicon: String? = null,
    modifier: Modifier = Modifier
) {
    if (!showIcons) return

    val thumbnailToUse = entry.thumbnail?.takeIf { it.isNotBlank() } ?: sourceFavicon?.takeIf { it.isNotBlank() }
    val isRestricted = EntryUtils.isRestricted(entry, userAge)

    if (!thumbnailToUse.isNullOrBlank()) {
        val predefinedVector = if (!isRestricted) sourceIconImageVector(thumbnailToUse) else null
        if (predefinedVector != null) {
            Box(
                modifier = modifier
                    .size(48.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = predefinedVector,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            RemoteImage(
                url = thumbnailToUse,
                modifier = modifier
                    .size(48.dp)
                    .padding(end = 8.dp),
                showErrorText = false,
                isRestricted = isRestricted
            )
        }
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
 * Backward compatibility alias for [EntrySharedThumbnail].
 */
@Composable
fun EntryLeadingIcon(
    entry: Entry,
    showIcons: Boolean,
    userAge: Int,
    sourceFavicon: String? = null,
    modifier: Modifier = Modifier
) {
    EntrySharedThumbnail(
        entry = entry,
        showIcons = showIcons,
        userAge = userAge,
        sourceFavicon = sourceFavicon,
        modifier = modifier
    )
}
