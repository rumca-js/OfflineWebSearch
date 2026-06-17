package com.example.index.ui.screens

import com.example.index.data.Entry
import com.example.index.util.EntryUtils
import com.example.index.data.AppConfigManager
import com.example.index.ui.components.RemoteImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(entry: Entry, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val config by AppConfigManager.config.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val isRestricted = EntryUtils.isRestricted(entry, config.userAge)
            val copyLink = {
                entry.link?.let { link ->
                    clipboardManager.setText(AnnotatedString(link))
                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }

            if (entry.thumbnail != null) {
                RemoteImage(
                    url = entry.thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp)
                        .padding(bottom = 16.dp)
                        .pointerInput(entry.link) {
                            detectTapGestures(
                                onTap = {
                                    entry.link?.let { uriHandler.openUri(it) }
                                },
                                onLongPress = {
                                    if (!isRestricted) {
                                        copyLink()
                                    }
                                }
                            )
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    isRestricted = isRestricted
                )
            }

            Text(
                text = EntryUtils.getDisplayTitle(entry, config.userAge),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
                color = if (entry.link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (entry.link != null) TextDecoration.Underline else TextDecoration.None,
                modifier = Modifier.pointerInput(entry.link) {
                    detectTapGestures(
                        onTap = {
                            entry.link?.let { uriHandler.openUri(it) }
                        },
                        onLongPress = {
                            if (!isRestricted) {
                                copyLink()
                            }
                        }
                    )
                }
            )

            entry.link?.let { link ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isRestricted) "xXx" else link,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(link) {
                            if (!isRestricted) {
                                detectTapGestures(
                                    onTap = { uriHandler.openUri(link) },
                                    onLongPress = { copyLink() }
                                )
                            }
                        }
                )
            }

            entry.date_published?.let { date ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Published: ${if (isRestricted) "xXx" else EntryUtils.getFormattedDate(date)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.tags?.let { tags ->
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            Text(
                                text = if (isRestricted) "#xXx" else "#$tag",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            EntryUtils.getDisplayDescription(entry, config.userAge)?.let {
                Text(
                    text = it,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Entry detail properties
            // Metadata

            DetailRow(label = "Created", value = EntryUtils.getFormattedDate(entry.date_created))
            DetailRow(label = "Dead", value = EntryUtils.getFormattedDate(entry.date_dead_since))

            DetailRow(label = "Author", value = entry.author ?: "NA")
            DetailRow(label = "Album", value = entry.album ?: "NA")
            DetailRow(label = "Language", value = entry.language ?: "NA")

            DetailRow(label = "Rating", value = EntryUtils.getFormattedRating(entry))
            DetailRow(label = "Votes", value = EntryUtils.getFormattedVotes(entry))

            DetailRow(label = "Status Code", value = (entry.status_code ?: 0).toString())
            DetailRow(label = "Manual Status Code", value = (entry.manual_status_code ?: 0).toString())

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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
        Text(text = value)
    }
}
