package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.util.EntryUtils

/**
 * Renders an entry in Search Engine view style with emphasis on title,
 * leading icon/thumbnail, and clickable URL link below the title.
 *
 * @param entry The database entry to display.
 * @param onClick Callback when the entry is tapped.
 * @param modifier Optional modifier for styling.
 */
@Composable
fun EntryListSearchEngineItem(
    entry: Entry,
    onClick: (Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    val config by AppConfigManager.config.collectAsState()
    val isDead = EntryUtils.isDead(entry)
    val isRestricted = EntryUtils.isRestricted(entry, config.userAge)

    EntryItemCard(
        entry = entry,
        onClick = onClick,
        modifier = modifier
    ) {
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
                    EntryLeadingIcon(
                        entry = entry,
                        showIcons = config.dbconfig.showIcons,
                        userAge = config.userAge
                    )
                    Text(
                        text = EntryUtils.getDisplayTitle(entry, config.userAge),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                EntryBadges(
                    entry = entry,
                    isDead = isDead,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            entry.link?.let { linkUrl ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isRestricted) "xXx" else linkUrl,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
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
                SocialDataPane(socialData = socialData)
            }

            EntryTagsPane(
                tags = entry.tags,
                isRestricted = isRestricted
            )
        }
    }
}
