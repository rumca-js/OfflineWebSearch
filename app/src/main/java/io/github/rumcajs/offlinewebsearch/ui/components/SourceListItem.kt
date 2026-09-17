package io.github.rumcajs.offlinewebsearch.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.rumcajs.offlinewebsearch.data.AppConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalDataRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository

/** Default dead / disabled alpha if configuration is not accessible. */
private const val DEFAULT_DEAD_ALPHA = 0.6f

/**
 * Composable row item representing a [Source] in the source list.
 *
 * Displays source thumbnail/favicon, title, URL, error badges (if consecutive errors occurred),
 * and a refresh-needed badge if the source fetch is outdated.
 *
 * @param source The [Source] to display.
 * @param activeDbState Current active [DatabaseState].
 * @param isEditable Whether the active database is writable.
 * @param onClick Callback when the item is tapped.
 * @param onEditClick Callback when edit is requested.
 * @param onDeleteClick Callback when delete is requested.
 * @param config Current application configuration.
 * @param isRefreshing Whether a global source refresh is currently in progress.
 * @param modifier Optional modifier for styling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceListItem(
    source: Source,
    activeDbState: DatabaseState?,
    isEditable: Boolean = false,
    onClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    config: AppConfiguration? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val deadAlpha = config?.dbconfig?.entriesDeadAlpha ?: DEFAULT_DEAD_ALPHA
    val itemAlpha = if (!source.enabled) deadAlpha else 1f

    var isFetchRequired by remember(source.id, activeDbState) { mutableStateOf(false) }
    var consecutiveErrors by remember(source.id, activeDbState) { mutableStateOf(0) }

    LaunchedEffect(source, activeDbState, isRefreshing) {
        if (source.id != null) {
            val opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, activeDbState, source.id)
            consecutiveErrors = opData?.consecutive_errors ?: 0
            isFetchRequired = if (!source.enabled || source.url.isBlank()) {
                false
            } else {
                SourceOperationalDataRepository.isFetchOutdated(opData?.date_fetched)
            }
        } else {
            consecutiveErrors = 0
            isFetchRequired = SourceRepository.isFetchRequired(context, activeDbState, source)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(itemAlpha)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (source.url.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(source.url))
                        Toast.makeText(context, "Source URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail / favicon
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (source.favicon.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(source.favicon)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumbnail for ${source.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title & indicator badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = source.title.ifBlank { "Untitled Source" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (consecutiveErrors > 0) {
                            SourceErrorBadge(consecutiveErrors = consecutiveErrors)
                        }
                        if (isFetchRequired) {
                            SourceRefreshBadge()
                        }
                    }
                }

                // URL
                if (source.url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinkText(text = source.url)
                }
            }
        }
    }
}
