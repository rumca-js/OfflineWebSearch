package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.data.Source
import io.github.rumcajs.offlinewebsearch.data.SourceRepository

/**
 * Component that displays source information (thumbnail or text) matching entry.source_id or entry.source_url.
 */
@Composable
fun SourcePane(
    entry: Entry,
    isRestricted: Boolean,
    modifier: Modifier = Modifier,
    onSelectSource: ((Source) -> Unit)? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val config by AppConfigManager.config.collectAsState()

    val sourceId = entry.source_id
    val sourceUrl = entry.source_url

    if (sourceId == null && (sourceUrl.isNullOrBlank())) {
        return
    }

    var matchingSource by remember(sourceId, sourceUrl, config.activeDatabaseState) {
        mutableStateOf<Source?>(null)
    }

    LaunchedEffect(sourceId, sourceUrl, config.activeDatabaseState) {
        matchingSource = when {
            sourceId != null && sourceId != 0L -> {
                SourceRepository.getSourceById(context, config.activeDatabaseState, sourceId)
            }
            !sourceUrl.isNullOrBlank() -> {
                SourceRepository.getSourceByUrl(context, config.activeDatabaseState, sourceUrl)
            }
            else -> null
        }
    }

    val sourceToDisplay = matchingSource
    val fallbackUrl = sourceUrl?.takeIf { it.isNotBlank() } ?: sourceToDisplay?.url

    if (sourceToDisplay == null && fallbackUrl == null) {
        return
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Source",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val title = sourceToDisplay?.title?.takeIf { it.isNotBlank() } ?: fallbackUrl ?: "Source #${sourceId}"
        val favicon = sourceToDisplay?.favicon?.takeIf { it.isNotBlank() }
        val targetUrl = sourceToDisplay?.url?.takeIf { it.isNotBlank() } ?: fallbackUrl

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!isRestricted) {
                        Modifier.clickable {
                            if (sourceToDisplay != null && onSelectSource != null) {
                                onSelectSource.invoke(sourceToDisplay)
                            } else if (targetUrl != null) {
                                uriHandler.openUri(targetUrl)
                            }
                        }
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (favicon != null && !isRestricted) {
                RemoteImage(
                    url = favicon,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit,
                    showErrorText = false,
                    isRestricted = isRestricted
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isRestricted) "xXx" else title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (targetUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (targetUrl != null && title != targetUrl && !isRestricted) {
                    Text(
                        text = targetUrl,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
