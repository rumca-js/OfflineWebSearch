package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry

/**
 * Convenience wrapper for [DetailThumbnail] when displaying an [Entry].
 *
 * @param entry The entry to display thumbnail/video preview for.
 * @param isRestricted When true, disables video embedding and masks thumbnail content.
 * @param modifier Optional modifier applied to the preview container.
 * @param videoPreview Whether YouTube video preview is enabled.
 * @param onTap Callback invoked on single tap.
 * @param onLongPress Callback invoked on long press.
 */
@Composable
fun EntryThumbnailPreview(
    entry: Entry,
    isRestricted: Boolean,
    modifier: Modifier = Modifier,
    videoPreview: Boolean = true,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    DetailThumbnail(
        link = entry.link,
        thumbnailUrl = entry.thumbnail,
        isRestricted = isRestricted,
        modifier = modifier,
        videoPreview = videoPreview,
        onTap = onTap,
        onLongPress = onLongPress
    )
}
