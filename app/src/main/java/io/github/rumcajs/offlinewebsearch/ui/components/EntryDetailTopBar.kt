package io.github.rumcajs.offlinewebsearch.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry

/**
 * Top bar component for EntryDetailScreen with actions for bookmarking (Read Later), sharing, editing, and deleting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailTopBar(
    entry: Entry,
    isEditable: Boolean,
    isReadLater: Boolean,
    onToggleReadLater: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onDeleteClick: () -> Unit,
    onBack: () -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text("") }, // not text
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (isEditable && entry.id != null) {
                IconButton(onClick = onToggleReadLater) {
                    Icon(
                        imageVector = if (isReadLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isReadLater) "Remove from Read Later" else "Check later"
                    )
                }
            }
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
            if (isEditable) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        },
        modifier = modifier
    )
}
