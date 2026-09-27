package io.github.rumcajs.offlinewebsearch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
 * Supports adding, viewing, editing, and deleting entry rules.
 *
 * @param onBack Callback invoked when navigating back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryRulesScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val config by AppConfigManager.config.collectAsState()
    val scope = rememberCoroutineScope()
    val isWritable = config.activeDatabaseState?.isSQLite == true && config.activeDatabaseState?.isReadOnly != true

    var rules by remember { mutableStateOf<List<EntryRule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRule by remember { mutableStateOf<EntryRule?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<EntryRule?>(null) }

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

    if (showAddDialog) {
        RuleFormDialog(
            initialRule = null,
            onDismiss = { showAddDialog = false },
            onSaveRule = { newRule ->
                scope.launch {
                    val (rowId, err) = EntryRulesRepository.insertRule(context, config.activeDatabaseState, newRule)
                    if (rowId != null) {
                        Toast.makeText(context, "Entry rule added", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        loadRules()
                    } else {
                        Toast.makeText(context, err ?: "Failed to add rule", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (editingRule != null) {
        RuleFormDialog(
            initialRule = editingRule,
            onDismiss = { editingRule = null },
            onSaveRule = { updatedRule ->
                scope.launch {
                    val (success, err) = EntryRulesRepository.updateRule(context, config.activeDatabaseState, updatedRule)
                    if (success) {
                        Toast.makeText(context, "Entry rule updated", Toast.LENGTH_SHORT).show()
                        editingRule = null
                        loadRules()
                    } else {
                        Toast.makeText(context, err ?: "Failed to update rule", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (selectedRule != null) {
        RuleDetailDialog(
            rule = selectedRule!!,
            isWritable = isWritable,
            onDismiss = { selectedRule = null },
            onEdit = { ruleToEdit ->
                val rule = ruleToEdit
                selectedRule = null
                editingRule = rule
            },
            onDelete = { ruleToDelete ->
                ruleToDelete.id?.let { ruleId ->
                    scope.launch {
                        val (success, err) = EntryRulesRepository.deleteById(context, config.activeDatabaseState, ruleId)
                        if (success) {
                            Toast.makeText(context, "Rule deleted", Toast.LENGTH_SHORT).show()
                            selectedRule = null
                            loadRules()
                        } else {
                            Toast.makeText(context, err ?: "Failed to delete rule", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
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
                },
                actions = {
                    if (isWritable) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Entry Rule")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isWritable) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Entry Rule")
                }
            }
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
                        text = if (isWritable) "No entry rules defined. Tap + to add one." else "No entry rules defined.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rules, key = { it.id ?: it.hashCode() }) { rule ->
                            EntryRuleCard(
                                rule = rule,
                                isWritable = isWritable,
                                onToggleEnabled = { newEnabled ->
                                    scope.launch {
                                        val updated = rule.copy(enabled = newEnabled)
                                        val (success, _) = EntryRulesRepository.updateRule(context, config.activeDatabaseState, updated)
                                        if (success) {
                                            rules = rules.map { if (it.id == rule.id) updated else it }
                                        }
                                    }
                                },
                                onEdit = { editingRule = rule },
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
 * @param onEdit Callback when the user clicks the edit button.
 * @param onClick Callback when the user clicks the card to view full details.
 */
@Composable
private fun EntryRuleCard(
    rule: EntryRule,
    isWritable: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
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

                    if (isWritable) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit Rule",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
 * Dialog for adding or editing an [EntryRule].
 *
 * @param initialRule Rule to edit, or null when adding a new rule.
 * @param onDismiss Callback to dismiss the dialog.
 * @param onSaveRule Callback with the new or updated rule.
 */
@Composable
private fun RuleFormDialog(
    initialRule: EntryRule?,
    onDismiss: () -> Unit,
    onSaveRule: (EntryRule) -> Unit
) {
    val isEditing = initialRule != null

    var ruleName by remember { mutableStateOf(initialRule?.rule_name ?: "") }
    var triggerRuleUrl by remember { mutableStateOf(initialRule?.trigger_rule_url ?: "") }
    var triggerText by remember { mutableStateOf(initialRule?.trigger_text ?: "") }
    var triggerTextFields by remember { mutableStateOf(initialRule?.trigger_text_fields ?: "") }
    var triggerRuleName by remember { mutableStateOf(initialRule?.trigger_rule_name ?: "") }
    var autoTag by remember { mutableStateOf(initialRule?.auto_tag ?: "") }
    var priorityStr by remember { mutableStateOf(initialRule?.priority?.toString() ?: "0") }
    var applyAgeLimitStr by remember { mutableStateOf(initialRule?.apply_age_limit?.toString() ?: "0") }
    var browserIdStr by remember { mutableStateOf(initialRule?.browser_id?.toString() ?: "0") }
    var script by remember { mutableStateOf(initialRule?.script ?: "") }
    var block by remember { mutableStateOf(initialRule?.block ?: false) }
    var trust by remember { mutableStateOf(initialRule?.trust ?: false) }
    var enabled by remember { mutableStateOf(initialRule?.enabled ?: true) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Entry Rule" else "Add Entry Rule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Rule Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = triggerRuleUrl,
                    onValueChange = { triggerRuleUrl = it },
                    label = { Text("Trigger Rule URL") },
                    placeholder = { Text("e.g. https://example.com/*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = triggerText,
                    onValueChange = { triggerText = it },
                    label = { Text("Trigger Text") },
                    placeholder = { Text("Text keyword to match") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = triggerTextFields,
                    onValueChange = { triggerTextFields = it },
                    label = { Text("Trigger Text Fields") },
                    placeholder = { Text("title, description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = triggerRuleName,
                    onValueChange = { triggerRuleName = it },
                    label = { Text("Trigger Rule Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = autoTag,
                    onValueChange = { autoTag = it },
                    label = { Text("Auto Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = priorityStr,
                        onValueChange = { priorityStr = it },
                        label = { Text("Priority") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = applyAgeLimitStr,
                        onValueChange = { applyAgeLimitStr = it },
                        label = { Text("Age Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = browserIdStr,
                        onValueChange = { browserIdStr = it },
                        label = { Text("Browser ID") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it },
                    label = { Text("Script") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = block, onCheckedChange = { block = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Block matching entries")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = trust, onCheckedChange = { trust = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trust matching entries")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enabled")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalRuleName = ruleName.trim()
                    if (finalRuleName.isEmpty() && triggerRuleUrl.trim().isEmpty() && triggerText.trim().isEmpty()) {
                        return@Button
                    }
                    val ruleToSave = (initialRule ?: EntryRule()).copy(
                        enabled = enabled,
                        priority = priorityStr.toIntOrNull() ?: 0,
                        rule_name = finalRuleName.ifEmpty { "Rule" },
                        trigger_rule_name = triggerRuleName.trim(),
                        trigger_rule_url = triggerRuleUrl.trim(),
                        trigger_text = triggerText.trim(),
                        trigger_text_fields = triggerTextFields.trim(),
                        block = block,
                        trust = trust,
                        auto_tag = autoTag.trim(),
                        apply_age_limit = applyAgeLimitStr.toIntOrNull() ?: 0,
                        browser_id = browserIdStr.toIntOrNull() ?: 0,
                        script = script.trim()
                    )
                    onSaveRule(ruleToSave)
                }
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog displaying full information for a selected [EntryRule].
 *
 * @param rule The rule to show.
 * @param isWritable True if the active database is writable.
 * @param onDismiss Callback to dismiss the dialog.
 * @param onEdit Callback when the user decides to edit the rule.
 * @param onDelete Callback when the user decides to delete the rule.
 */
@Composable
private fun RuleDetailDialog(
    rule: EntryRule,
    isWritable: Boolean,
    onDismiss: () -> Unit,
    onEdit: (EntryRule) -> Unit,
    onDelete: (EntryRule) -> Unit
) {
    val scrollState = rememberScrollState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Rule") },
            text = { Text("Are you sure you want to delete rule '${rule.rule_name.ifEmpty { "Unnamed" }}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(rule)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isWritable && rule.id != null) {
                    TextButton(onClick = { onEdit(rule) }) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
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
