package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRulesRepository
import kotlinx.coroutines.launch

/**
 * Screen displaying the defined entry rules from the `entryrules` table.
 *
 * Each item displays:
 * - `enabled`: whether the rule is currently enabled
 * - `rule_name`: the display name of the rule
 * - `trigger_rule_url`: the URL pattern that triggers the rule
 * - `block`: whether matches for this rule should be blocked
 *
 * @param onBack Callback invoked when navigating back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryRulesScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()

    var rules by remember { mutableStateOf<List<EntryRule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRule by remember { mutableStateOf<EntryRule?>(null) }

    fun loadRules() {
        scope.launch {
            isLoading = true
            rules = EntryRulesRepository.getRules(context, config.activeDatabaseState)
            isLoading = false
        }
    }

    LaunchedEffect(config.activeDatabaseState) {
        loadRules()
    }

    if (selectedRule != null) {
        RuleDetailDialog(
            rule = selectedRule!!,
            onDismiss = { selectedRule = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                rules.isEmpty() -> {
                    Text(
                        text = "No entry rules defined.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rules, key = { it.id ?: it.hashCode() }) { rule ->
                            EntryRuleCard(
                                rule = rule,
                                isWritable = config.activeDatabaseState?.isSQLite == true && config.activeDatabaseState?.isReadOnly != true,
                                onToggleEnabled = { newEnabled ->
                                    scope.launch {
                                        val updated = rule.copy(enabled = newEnabled)
                                        val (success, _) = EntryRulesRepository.updateRule(context, config.activeDatabaseState, updated)
                                        if (success) {
                                            rules = rules.map { if (it.id == rule.id) updated else it }
                                        }
                                    }
                                },
                                onClick = { selectedRule = rule }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card composable representing a single [EntryRule].
 *
 * Displays enabled toggle, rule name, trigger URL, and block status.
 *
 * @param rule The entry rule to display.
 * @param isWritable True if the active database is writable.
 * @param onToggleEnabled Callback when the user toggles the enabled switch.
 * @param onClick Callback when the user clicks the card to view full details.
 */
@Composable
private fun EntryRuleCard(
    rule: EntryRule,
    isWritable: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.rule_name.ifEmpty { "Unnamed Rule" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (rule.block) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Block", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }

                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = onToggleEnabled,
                        enabled = isWritable
                    )
                }
            }

            if (rule.trigger_rule_url.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trigger URL: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = rule.trigger_rule_url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (rule.priority != 0 || rule.trigger_text_hits > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (rule.priority != 0) {
                        Text(
                            text = "Priority: ${rule.priority}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (rule.trigger_text_hits > 0) {
                        Text(
                            text = "Hits: ${rule.trigger_text_hits}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog displaying full information for a selected [EntryRule].
 *
 * @param rule The rule to show.
 * @param onDismiss Callback to dismiss the dialog.
 */
@Composable
private fun RuleDetailDialog(
    rule: EntryRule,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = rule.rule_name.ifEmpty { "Entry Rule Details" },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleDetailRow("ID", rule.id?.toString() ?: "—")
                    RuleDetailRow("Enabled", if (rule.enabled) "Yes" else "No")
                    RuleDetailRow("Priority", rule.priority.toString())
                    RuleDetailRow("Block", if (rule.block) "Yes" else "No")
                    RuleDetailRow("Trust", if (rule.trust) "Yes" else "No")

                    if (rule.trigger_rule_name.isNotEmpty()) {
                        RuleDetailRow("Trigger Rule Name", rule.trigger_rule_name)
                    }
                    if (rule.trigger_rule_url.isNotEmpty()) {
                        RuleDetailRow("Trigger URL", rule.trigger_rule_url)
                    }
                    if (rule.trigger_text.isNotEmpty()) {
                        RuleDetailRow("Trigger Text", rule.trigger_text)
                    }
                    if (rule.trigger_text_fields.isNotEmpty()) {
                        RuleDetailRow("Trigger Text Fields", rule.trigger_text_fields)
                    }
                    RuleDetailRow("Trigger Hits", rule.trigger_text_hits.toString())

                    if (rule.auto_tag.isNotEmpty()) {
                        RuleDetailRow("Auto Tag", rule.auto_tag)
                    }
                    if (rule.apply_age_limit > 0) {
                        RuleDetailRow("Age Limit", rule.apply_age_limit.toString())
                    }
                    if (rule.browser_id > 0) {
                        RuleDetailRow("Browser ID", rule.browser_id.toString())
                    }
                    if (rule.script.isNotEmpty()) {
                        RuleDetailRow("Script", rule.script)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Helper row displaying a label and value in the rule detail dialog.
 */
@Composable
private fun RuleDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
