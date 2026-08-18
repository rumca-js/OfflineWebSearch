package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Intent
import android.widget.Toast
import io.github.rumcajs.offlinewebsearch.ui.components.EntryThumbnailPreview
import io.github.rumcajs.offlinewebsearch.webtoolkit.HandlerBuilder
import io.github.rumcajs.offlinewebsearch.webtoolkit.OdyseeChannelHandler
import io.github.rumcajs.offlinewebsearch.webtoolkit.YouTubeChannelHandler
import io.github.rumcajs.offlinewebsearch.ui.components.SocialDataPane
import io.github.rumcajs.offlinewebsearch.ui.components.UrlServicesPane
import io.github.rumcajs.offlinewebsearch.ui.components.isEmptyOrZero
import io.github.rumcajs.offlinewebsearch.webtoolkit.RedditChannelHandler

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    entry: io.github.rumcajs.offlinewebsearch.data.Entry,
    onNavigateToLinkPreview: (String) -> Unit,
    onNavigateToEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onVisit: (() -> Unit)? = null,
    onSelectEntry: ((io.github.rumcajs.offlinewebsearch.data.Entry) -> Unit)? = null,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val config by _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id ?: entry.link) {
        onVisit?.invoke()
    }

    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly && activeDbState.extension == ".db"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    entry.link?.let { url ->
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share link"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    if (isEditable && onDelete != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        if (showDeleteDialog && onDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Remove entry") },
                text = { Text("Are you sure you want to permanently remove this entry?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val isRestricted = _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.isRestricted(entry, config.userAge)
            val copyLink = {
                entry.link?.let { link ->
                    clipboardManager.setText(AnnotatedString(link))
                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }

            EntryThumbnailPreview(
                entry = entry,
                isRestricted = isRestricted,
                videoPreview = config.dbconfig.videoPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                onTap = { entry.link?.let { uriHandler.openUri(it) } },
                onLongPress = { if (!isRestricted) { copyLink() } }
            )

            Text(
                text = _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.getDisplayTitle(entry, config.userAge),
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
                    text = "Published: ${if (isRestricted) "xXx" else _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.getFormattedDate(date)}",
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
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable(enabled = !isRestricted && onTagClick != null) {
                                    onTagClick?.invoke(tag)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!config.networkConfig.disabled) {
                Button(
                    onClick = { entry.link?.let { onNavigateToLinkPreview(it) } },
                    enabled = !isRestricted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check status")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.getDisplayDescription(entry, config.userAge)?.let {
                Text(
                    text = it,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Social data pane (if social data exists for entry)
            val socialData = entry.socialData
            val hasSocialData = socialData != null && !socialData.isEmptyOrZero()
            if (hasSocialData) {
                SocialDataPane(
                    socialData = socialData
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Entry detail properties and metadata pane
            io.github.rumcajs.offlinewebsearch.ui.components.EntryMetadataPane(
                entry = entry,
                isRestricted = isRestricted
            )

            // UrlServices link services pane (divided by horizontal bar)
            val hasServiceLinks = entry.link?.let { io.github.rumcajs.offlinewebsearch.util.UrlServices().getServiceLinks(it).isNotEmpty() } == true
            if (hasServiceLinks) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                io.github.rumcajs.offlinewebsearch.ui.components.UrlServicesPane(
                    entry = entry,
                    isRestricted = isRestricted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Entry transition history links panel
            io.github.rumcajs.offlinewebsearch.ui.components.EntryTransitionsPanel(
                fromEntryId = entry.id,
                onSelectEntry = { targetEntry ->
                    onSelectEntry?.invoke(targetEntry)
                }
            )

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

@Composable
fun LinkRow(
    label: String,
    url: String,
    isRestricted: Boolean,
    toastMessage: String = "Link copied"
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val displayUrl = if (isRestricted) "xXx" else url

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(url) {
                if (!isRestricted) {
                    detectTapGestures(
                        onTap = { uriHandler.openUri(url) },
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(url))
                            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = displayUrl,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 16.dp)
        )
    }
}
