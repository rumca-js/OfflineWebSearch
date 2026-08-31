package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.rumcajs.offlinewebsearch.data.ReadLaterRepository
import kotlinx.coroutines.launch
import io.github.rumcajs.offlinewebsearch.ui.components.EntryDetailTopBar
import io.github.rumcajs.offlinewebsearch.ui.components.EntryThumbnailPreview
import io.github.rumcajs.offlinewebsearch.ui.components.SocialDataPane
import io.github.rumcajs.offlinewebsearch.ui.components.isEmptyOrZero
import io.github.rumcajs.offlinewebsearch.webtoolkit.YouTubeVideoHandler

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
    onSelectSource: ((io.github.rumcajs.offlinewebsearch.data.Source) -> Unit)? = null,
    /** Called after a successful Read Later add (true) or remove (false). */
    onReadLaterChanged: ((Boolean) -> Unit)? = null,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.AppConfigManager.config.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isReadLater by remember { mutableStateOf(false) }

    val activeDbState = config.activeDatabaseState
    val isEditable = activeDbState != null && !activeDbState.isReadOnly && activeDbState.extension == ".db"

    LaunchedEffect(entry.id, activeDbState) {
        val entryId = entry.id
        if (entryId != null && activeDbState != null && activeDbState.extension == ".db") {
            isReadLater = ReadLaterRepository.isReadLater(context, activeDbState, entryId)
        } else {
            isReadLater = false
        }
    }

    LaunchedEffect(entry.id ?: entry.link) {
        onVisit?.invoke()
    }

    Scaffold(
        topBar = {
            EntryDetailTopBar(
                entry = entry,
                isEditable = isEditable,
                isReadLater = isReadLater,
                onToggleReadLater = {
                    val entryId = entry.id ?: return@EntryDetailTopBar
                    scope.launch {
                        if (isReadLater) {
                            val (success, err) = ReadLaterRepository.removeReadLaterByEntryId(context, activeDbState, entryId)
                            if (success) {
                                isReadLater = false
                                onReadLaterChanged?.invoke(false)
                                Toast.makeText(context, "Removed from Read Later", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, err ?: "Failed to update", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val (success, err) = ReadLaterRepository.addReadLater(context, activeDbState, entryId)
                            if (success) {
                                isReadLater = true
                                onReadLaterChanged?.invoke(true)
                                Toast.makeText(context, "Added to Read Later", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, err ?: "Failed to update", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onNavigateToEdit = onNavigateToEdit,
                onDeleteClick = { showDeleteDialog = true },
                onBack = onBack,
                context = context
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

            val hasVideoPreview = config.dbconfig.videoPreview && !isRestricted && entry.link?.let { YouTubeVideoHandler(it).isHandledBy() } == true
            val hasThumbnail = !entry.thumbnail.isNullOrBlank()
            if (hasVideoPreview || hasThumbnail) {
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.getDisplayTitle(entry, config.userAge),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                    color = if (entry.link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (entry.link != null) TextDecoration.Underline else TextDecoration.None,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
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
                        }
                )
                entry.page_rating_votes?.takeIf { it > 0 }?.let { votes ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Text(
                            text = "⭐ $votes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

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

            // Social data pane (if social data exists for entry)
            val socialData = entry.socialData
            val hasSocialData = socialData != null && !socialData.isEmptyOrZero()
            if (hasSocialData) {
                Spacer(modifier = Modifier.height(4.dp))
                SocialDataPane(
                    socialData = socialData
                )
            }

            var showTagsDialog by remember { mutableStateOf(false) }
            var tagsInput by remember(entry.tags) { mutableStateOf(entry.tags?.joinToString(", ") ?: "") }
            var isSavingTags by remember { mutableStateOf(false) }

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

            if (isEditable && entry.id != null) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        tagsInput = entry.tags?.joinToString(", ") ?: ""
                        showTagsDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Tags")
                }
            }

            if (showTagsDialog && entry.id != null) {
                AlertDialog(
                    onDismissRequest = { if (!isSavingTags) showTagsDialog = false },
                    title = { Text("Edit Tags") },
                    text = {
                        Column {
                            Text(
                                text = "Enter tags separated with ',' character:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = tagsInput,
                                onValueChange = { tagsInput = it },
                                label = { Text("Tags") },
                                placeholder = { Text("tag1, tag2, tag3") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                maxLines = 4
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val newTags = tagsInput.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                val entryId = entry.id
                                val dbState = activeDbState
                                if (entryId != null && dbState != null) {
                                    scope.launch {
                                        isSavingTags = true
                                        try {
                                            io.github.rumcajs.offlinewebsearch.data.EntryCompactedTagsRepository.deleteTagsForEntry(
                                                context,
                                                dbState,
                                                entryId
                                            )
                                            for (tag in newTags) {
                                                io.github.rumcajs.offlinewebsearch.data.EntryCompactedTagsRepository.insertTag(
                                                    context,
                                                    dbState,
                                                    tag,
                                                    entryId
                                                )
                                            }
                                            val updatedEntry = entry.copy(tags = newTags)
                                            onSelectEntry?.invoke(updatedEntry)
                                            Toast.makeText(context, "Tags updated", Toast.LENGTH_SHORT).show()
                                            showTagsDialog = false
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Failed to update tags", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSavingTags = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingTags
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showTagsDialog = false },
                            enabled = !isSavingTags
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Source pane (if source_id or source_url is set)
            if (entry.source_id != null || !entry.source_url.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                io.github.rumcajs.offlinewebsearch.ui.components.SourcePane(
                    entry = entry,
                    isRestricted = isRestricted,
                    onSelectSource = onSelectSource
                )
            }

            var showVoteDialog by remember { mutableStateOf(false) }
            var voteInput by remember(entry.page_rating_votes) { mutableStateOf((entry.page_rating_votes ?: 0).toString()) }
            var isSavingVote by remember { mutableStateOf(false) }
            var voteError by remember { mutableStateOf<String?>(null) }

            if (showVoteDialog && entry.id != null) {
                AlertDialog(
                    onDismissRequest = { if (!isSavingVote) showVoteDialog = false },
                    title = { Text("Add Vote") },
                    text = {
                        Column {
                            Text(
                                text = "Enter a vote value between -100 and 100:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = voteInput,
                                onValueChange = {
                                    voteInput = it
                                    voteError = null
                                },
                                label = { Text("Vote") },
                                placeholder = { Text("0") },
                                isError = voteError != null,
                                supportingText = voteError?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val parsed = voteInput.trim().toIntOrNull()
                                if (parsed == null || parsed < -100 || parsed > 100) {
                                    voteError = "Please enter an integer between -100 and 100"
                                    return@Button
                                }
                                val entryId = entry.id
                                val dbState = activeDbState
                                if (entryId != null && dbState != null) {
                                    scope.launch {
                                        isSavingVote = true
                                        try {
                                            val (success, newVotes) = io.github.rumcajs.offlinewebsearch.data.EntryRepository.setVote(
                                                context,
                                                dbState,
                                                entryId,
                                                parsed
                                            )
                                            if (success && newVotes != null) {
                                                val updatedEntry = entry.copy(page_rating_votes = newVotes)
                                                onSelectEntry?.invoke(updatedEntry)
                                                Toast.makeText(context, "Vote updated to $newVotes", Toast.LENGTH_SHORT).show()
                                                showVoteDialog = false
                                            } else {
                                                Toast.makeText(context, "Failed to update vote", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Failed to update vote", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSavingVote = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingVote
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showVoteDialog = false },
                            enabled = !isSavingVote
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (!config.networkConfig.disabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { entry.link?.let { onNavigateToLinkPreview(it) } },
                    enabled = !isRestricted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check status")
                }
            }

            if (isEditable && entry.id != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        voteInput = (entry.page_rating_votes ?: 0).toString()
                        voteError = null
                        showVoteDialog = true
                    },
                    enabled = !isRestricted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add vote")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            _root_ide_package_.io.github.rumcajs.offlinewebsearch.util.EntryUtils.getDisplayDescription(entry, config.userAge)?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
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
