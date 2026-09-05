package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.ViewStyle
import io.github.rumcajs.offlinewebsearch.util.EntryUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryItem(entry: Entry, onClick: (Entry) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val config by AppConfigManager.config.collectAsState()
    val isDead = !entry.date_dead_since.isNullOrBlank()

    val displayAuthor by produceState<String?>(initialValue = entry.author?.takeIf { it.isNotBlank() }, key1 = entry, key2 = config.activeDatabaseState) {
        value = EntryUtils.getDisplayAuthor(entry, context, config.activeDatabaseState)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (isDead) 0.5f else 1f)
            .clickable(enabled = entry.link != null || !config.dbconfig.directLinks) {
                if (config.dbconfig.directLinks) {
                    entry.link?.let { uriHandler.openUri(it) }
                } else {
                    onClick(entry)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (config.dbconfig.viewStyle == ViewStyle.GALLERY && config.dbconfig.showIcons && !entry.thumbnail.isNullOrBlank()) {
                RemoteImage(
                    url = entry.thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop,
                    showErrorText = false,
                    isRestricted = EntryUtils.isRestricted(
                        entry,
                        config.userAge
                    )
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config.dbconfig.showIcons && config.dbconfig.viewStyle != ViewStyle.GALLERY) {
                            if (!entry.thumbnail.isNullOrBlank()) {
                                RemoteImage(
                                    url = entry.thumbnail,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(end = 8.dp),
                                    showErrorText = false,
                                    isRestricted = EntryUtils.isRestricted(
                                        entry,
                                        config.userAge
                                    )
                                )
                            } else if (entry.link != null) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = EntryUtils.getDisplayTitle(entry, config.userAge),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.bookmarked == true) {
                            Text(
                                text = "📌",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        if (isDead) {
                            Text(
                                text = "💀",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        entry.page_rating_votes?.takeIf { it > 0 }?.let { votes ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
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

                val isRestricted = EntryUtils.isRestricted(entry, config.userAge)

                if (config.dbconfig.viewStyle == ViewStyle.SEARCH_ENGINE) {
                    entry.link?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRestricted) "xXx" else it,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (config.dbconfig.viewStyle == ViewStyle.STANDARD || config.dbconfig.viewStyle == ViewStyle.GALLERY) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        entry.date_published?.let {
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

                entry.language?.let { lang ->
                    if (lang.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRestricted) "xXx" else lang,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val socialData = entry.socialData
                if (socialData != null && !socialData.isEmptyOrZero()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SocialDataPane(
                        socialData = socialData
                    )
                }

                entry.tags?.let { tags ->
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
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
                }
            }
        }
    }
}
