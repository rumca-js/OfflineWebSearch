package io.github.rumcajs.offlinewebsearch.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.data.EntryTransitionHistory
import io.github.rumcajs.offlinewebsearch.data.EntryTransitionHistoryRepository
import io.github.rumcajs.offlinewebsearch.util.EntryUtils

/**
 * Component that displays a list/panel of transition links to other entries
 * originated from a given entry using [EntryTransitionHistoryRepository].
 */
@Composable
fun EntryTransitionsPanel(
    fromEntryId: Long?,
    onSelectEntry: (Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (fromEntryId == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    var transitions by remember(fromEntryId, activeDbState) {
        mutableStateOf<List<Pair<EntryTransitionHistory, Entry>>>(emptyList())
    }

    LaunchedEffect(fromEntryId, activeDbState) {
        if (activeDbState != null) {
            transitions = EntryTransitionHistoryRepository.loadTransitionedEntriesFrom(
                context = context,
                activeDatabaseState = activeDbState,
                fromEntryId = fromEntryId
            )
        }
    }

    if (transitions.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Visited From This Entry",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            transitions.forEach { (transition, targetEntry) ->
                val title = EntryUtils.getDisplayTitle(targetEntry, config.userAge)
                val counterText = transition.counter?.let { " ($it visits)" } ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (activeDbState != null) {
                                targetEntry.id?.let { targetId ->
                                    coroutineScope.launch {
                                        EntryTransitionHistoryRepository.recordTransition(
                                            context,
                                            activeDbState,
                                            fromEntryId,
                                            targetId
                                        )
                                    }
                                }
                            }
                            onSelectEntry(targetEntry)
                        }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "• $title$counterText",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
