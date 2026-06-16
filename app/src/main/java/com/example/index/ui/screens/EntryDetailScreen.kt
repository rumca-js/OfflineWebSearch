package com.example.index.ui.screens

import com.example.index.data.Place
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
import androidx.compose.ui.Alignment
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
fun EntryDetailScreen(place: Place, onBack: () -> Unit) {
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
            if (place.thumbnail != null) {
                RemoteImage(
                    url = place.thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp)
                        .padding(bottom = 16.dp)
                        .pointerInput(place.link) {
                            detectTapGestures(
                                onTap = {
                                    place.link?.let { uriHandler.openUri(it) }
                                }
                            )
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    isRestricted = EntryUtils.isRestricted(place, config.userAge)
                )
            }

            Text(
                text = EntryUtils.getDisplayTitle(place, config.userAge),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
                color = if (place.link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (place.link != null) TextDecoration.Underline else TextDecoration.None,
                modifier = if (place.link != null) {
                    Modifier.clickable { uriHandler.openUri(place.link) }
                } else {
                    Modifier
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            EntryUtils.getDisplayDescription(place, config.userAge)?.let {
                Text(
                    text = it,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Entry detail properties
            // Metadata
            DetailRow(label = "Rating", value = EntryUtils.getFormattedRating(place))
            DetailRow(label = "Votes", value = EntryUtils.getFormattedVotes(place))
            DetailRow(label = "Created", value = EntryUtils.getFormattedDate(place.date_created))
            DetailRow(label = "Published", value = EntryUtils.getFormattedDate(place.date_published))
            place.thumbnail?.let { thumbUrl ->
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
            place.tags?.let { tags ->
                if (tags.isNotEmpty()) {
                    Text(
                        text = "Tags:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            SuggestionChip(
                                onClick = { /* Search for tag? */ },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
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
