package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.data.EntryTransitionHistory
import io.github.rumcajs.offlinewebsearch.data.EntryTransitionHistoryRepository

/**
 * Component that displays a list/panel of transition links to other entries
 * originated from a given entry using [EntryTransitionHistoryRepository].
 */
@Composable
fun EntryTransitionsPanel(
    fromEntryId: Long?,
    onSelectEntry: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    maxEntries: Int = EntryTransitionHistoryRepository.MAX_NUMBER_OF_DISPLAYED_ENTRIES
) {
    if (fromEntryId == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by AppConfigManager.config.collectAsState()
    val activeDbState = config.activeDatabaseState
    var transitions by remember(fromEntryId, activeDbState) {
        mutableStateOf<List<Pair<EntryTransitionHistory, Entry>>>(emptyList())
    }
    var loadError by remember(fromEntryId, activeDbState) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(fromEntryId, activeDbState, maxEntries) {
        if (activeDbState != null) {
            val res = EntryTransitionHistoryRepository.getTransitionedEntriesFrom(
                context = context,
                activeDatabaseState = activeDbState,
                fromEntryId = fromEntryId,
                limit = maxEntries
            )
            transitions = res.entries
            loadError = res.error
        } else {
            transitions = emptyList()
            loadError = null
        }
    }

    if (transitions.isEmpty() && loadError == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Visited From This Entry",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (loadError != null) {
            Text(
                text = "Error loading entry transitions: $loadError",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            transitions.forEach { (transition, targetEntry) ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    transition.counter?.let { visits ->
                        Text(
                            text = "$visits visits",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    EntryItem(
                        entry = targetEntry,
                        onClick = { selectedEntry ->
                            if (activeDbState != null && config.dbconfig.trackUserNavigation) {
                                selectedEntry.id?.let { targetId ->
                                    coroutineScope.launch {
                                        EntryTransitionHistoryRepository.insertTransition(
                                            context,
                                            activeDbState,
                                            fromEntryId,
                                            targetId
                                        )
                                    }
                                }
                            }
                            onSelectEntry(selectedEntry)
                        }
                    )
                }
            }
        }
    }
}
