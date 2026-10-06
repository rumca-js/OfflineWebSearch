package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Prominent thumbnail banner rendered at the top of an entry in Gallery view style.
 *
 * Displays either:
 * - A centered predefined Material icon in a styled banner box when [thumbnailUrl] matches a predefined icon identifier.
 * - A full-width remote image thumbnail via [RemoteImage] when [thumbnailUrl] is a remote image URL.
 * - Nothing when [showIcons] is false or [thumbnailUrl] is null/blank.
 *
 * @param thumbnailUrl The effective thumbnail URL or predefined icon name.
 * @param isRestricted Whether age restriction is applied.
 * @param showIcons Whether icon display is enabled in configuration.
 * @param modifier Optional layout modifier.
 */
@Composable
fun EntryGalleryThumbnail(
    thumbnailUrl: String?,
    isRestricted: Boolean = false,
    showIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showIcons || thumbnailUrl.isNullOrBlank()) return

    val predefinedVector = if (!isRestricted) sourceIconImageVector(thumbnailUrl) else null
    if (predefinedVector != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = predefinedVector,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        RemoteImage(
            url = thumbnailUrl,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.Crop,
            showErrorText = false,
            isRestricted = isRestricted
        )
    }
}
