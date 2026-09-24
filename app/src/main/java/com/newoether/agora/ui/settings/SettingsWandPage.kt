package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.modelAliasDisplayName
import com.newoether.agora.data.providerDisplayName
import com.newoether.agora.model.ModelId
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.wand.PromptStructure
import com.newoether.agora.wand.WandPrompts
import com.newoether.agora.wand.WandSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * « Voix et baguette » settings — restyled on the standard settings components
 * (CollapsingSettingsScaffold / SettingsGroup / SettingsItem) for visual coherence
 * with every other settings page. The wand model is picked from the user's synced
 * models (single « Provider:modelId » selection — the compiler auto-resolves the
 * provider). Zapier/MCP connections no longer appear here per user feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsWandPage(
    viewModel: ChatViewModel? = null,
    onBack: () -> Unit,
    onOpenMcp: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WandSettingsStore.getInstance(context) }

    val structures by store.structures.collectAsState(initial = emptyList())
    val wandModel by store.wandModel.collectAsState(initial = "")
    val magicPrompt by store.magicPrompt.collectAsState(initial = WandPrompts.DEFAULT_MAGIC_PROMPT)
    val persona by store.personaBrief.collectAsState(
        initial = com.newoether.agora.wand.PersonaBrief("", 0, 0L),
    )

    val availableModels by (viewModel?.settings?.availableModels ?: MutableStateFlow(emptyMap())).collectAsState()
    val modelAliases by (viewModel?.settings?.modelAliases ?: MutableStateFlow(emptyMap())).collectAsState()
    val customProviders by (viewModel?.settings?.customProviders ?: MutableStateFlow(emptyList())).collectAsState()
    val showDocFab by (viewModel?.settings?.showDocumentationFab ?: MutableStateFlow(false)).collectAsState()

    var editingStructure by remember { mutableStateOf<PromptStructure?>(null) }
    var showPersonaEditor by remember { mutableStateOf(false) }
    var showMagicPromptEditor by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    val allModels = remember(availableModels) {
        availableModels.values.flatten().distinct().sorted()
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_wand),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("wand.md") }
    ) {
        SettingsGroupColumn {
            // ── Modèle de la baguette : sélection parmi les modèles synchronisés ──
            SettingsGroup(
                title = "Modèle de la baguette magique",
                items = listOf {
                    val displayName = if (wandModel.isBlank()) {
                        "Automatique (modèle par défaut du chat)"
                    } else {
                        modelAliasDisplayName(wandModel, modelAliases, customProviders)
                    }
                    SettingsItem(
                        headlineContent = { Text("Modèle de compilation") },
                        supportingContent = { Text(displayName) },
                        leadingContent = {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable { showModelDialog = true }
                    )
                }
            )

            // ── Prompt de magie ───────────────────────────────────────────
            SettingsGroup(
                title = "Prompt de magie",
                items = listOf {
                    PromptSettingItem(
                        title = "Prompt de magie (System Prompt)",
                        description = "Instructions qui transforment la saisie brute en un prompt enrichi, clair et sans ambiguïté.",
                        prompt = magicPrompt,
                        onClick = { showMagicPromptEditor = true }
                    )
                }
            )

            // ── Structures de prompts par type ────────────────────────────
            SettingsGroup(
                title = "Structures de prompts",
                items = buildList {
                    structures.forEach { structure ->
                        add {
                            SettingsItem(
                                headlineContent = { Text(structure.type.label) },
                                supportingContent = {
                                    Text(
                                        structure.template.take(110) + if (structure.template.length > 110) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    IconButton(onClick = { editingStructure = structure }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Modifier la structure",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { editingStructure = structure }
                            )
                        }
                    }
                    add {
                        SettingsItem(
                            headlineContent = { Text("Réinitialiser les structures") },
                            supportingContent = { Text("Revenir aux modèles par défaut") },
                            leadingContent = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable {
                                scope.launch { store.resetStructuresToDefaults() }
                            }
                        )
                    }
                }
            )

            // ── Brief persona ─────────────────────────────────────────────
            SettingsGroup(
                title = "Brief persona",
                items = listOf {
                    SettingsItem(
                        headlineContent = { Text("Votre persona") },
                        supportingContent = {
                            Text(
                                if (persona.content.isBlank()) {
                                    "Aucun brief persona défini — la baguette utilisera un ton neutre."
                                } else {
                                    persona.content.take(120) + if (persona.content.length > 120) "…" else ""
                                }
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            IconButton(onClick = { showPersonaEditor = true }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Modifier le persona",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.clickable { showPersonaEditor = true }
                    )
                }
            )
        }
    }

    // ── Dialog : modèle de la baguette (tous les modèles synchronisés) ────
    if (showModelDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showModelDialog = false },
            title = { Text("Sélectionner le modèle", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        val isSelected = wandModel.isBlank()
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    "Automatique (modèle par défaut)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            store.setWandModel("")
                                            store.setWandProvider("")
                                        }
                                        showModelDialog = false
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    store.setWandModel("")
                                    store.setWandProvider("")
                                }
                                showModelDialog = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(allModels, key = { it }) { modelId ->
                        val isSelected = wandModel.equals(modelId, ignoreCase = true)
                        val parsed = if (modelId.contains(":")) ModelId.parse(modelId) else null
                        val providerName = parsed?.let { providerDisplayName(it.providerName, customProviders) }
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    modelAliasDisplayName(modelId, modelAliases, customProviders),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            supportingContent = providerName?.let {
                                { Text(it, style = MaterialTheme.typography.bodySmall) }
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        // Single synced id « Provider:modelId » — the compiler
                                        // auto-resolves the provider from the prefix.
                                        scope.launch {
                                            store.setWandModel(modelId)
                                            store.setWandProvider("")
                                        }
                                        showModelDialog = false
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    store.setWandModel(modelId)
                                    store.setWandProvider("")
                                }
                                showModelDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ── Dialog : prompt de magie ──────────────────────────────────────────
    if (showMagicPromptEditor) {
        PromptEditDialog(
            title = "Prompt de magie",
            initialPrompt = magicPrompt,
            onDismiss = { showMagicPromptEditor = false },
            onSave = { newPrompt ->
                scope.launch { store.setMagicPrompt(newPrompt) }
                showMagicPromptEditor = false
            },
        )
    }

    // ── Dialog : édition de structure ─────────────────────────────────────
    editingStructure?.let { structure ->
        WandStructureEditorDialog(
            structure = structure,
            onDismiss = { editingStructure = null },
            onSave = { updated ->
                scope.launch { store.upsertStructure(updated) }
                editingStructure = null
            },
        )
    }

    // ── Dialog : persona ──────────────────────────────────────────────────
    if (showPersonaEditor) {
        PromptEditDialog(
            title = "Brief persona",
            initialPrompt = persona.content,
            onDismiss = { showPersonaEditor = false },
            onSave = { content ->
                scope.launch { store.savePersonaBrief(content) }
                showPersonaEditor = false
            },
        )
    }
}

/** Standard-styled structure editor: name + template (Zapier connections removed from UI). */
@Composable
private fun WandStructureEditorDialog(
    structure: PromptStructure,
    onDismiss: () -> Unit,
    onSave: (PromptStructure) -> Unit,
) {
    var name by remember { mutableStateOf(structure.name) }
    var template by remember { mutableStateOf(structure.template) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(structure.type.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la structure") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Template de prompt") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    structure.copy(
                        name = name.ifBlank { structure.name },
                        template = template,
                    ),
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
