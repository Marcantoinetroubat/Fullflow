package com.newoether.agora.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.data.BuiltInPrompts
import com.newoether.agora.data.modelAliasDisplayName
import com.newoether.agora.data.modelDisplayName
import com.newoether.agora.model.ModelId
import com.newoether.agora.ui.components.providerIcon
import com.newoether.agora.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsProactivePage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val enabled by viewModel.settings.proactiveIntelligenceEnabled.collectAsState()
    val selectedModel by viewModel.settings.proactiveIntelligenceModel.collectAsState()
    val customPrompt by viewModel.settings.proactiveIntelligencePrompt.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    val availableModels by viewModel.settings.availableModels.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val customProviders by viewModel.settings.customProviders.collectAsState()
    val modelProviderNames by viewModel.settings.modelProviderNames.collectAsState()

    val proactiveTasks by viewModel.proactiveIntelligence.tasks.collectAsState()
    val isGenerating by viewModel.proactiveIntelligence.isGenerating.collectAsState()

    var showModelDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

    // All synced models (user feedback: the separate provider step was redundant).
    val candidateModels = remember(availableModels) {
        availableModels.values.flatten().distinct()
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_proactive),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("proactive.md") }
    ) {
        SettingsGroupColumn {
            // Group 1: Activation & Modèle
            SettingsGroup(
                title = "Paramètres de recommandation",
                items = buildList {
                    add {
                        SettingsItem(
                            headlineContent = { Text("Activer l'intelligence proactive") },
                            supportingContent = {
                                Text("Propose 5 actions dynamiques sur l'écran d'accueil basées sur vos discussions et mémoires.")
                            },
                            leadingContent = {
                                Icon(Icons.Default.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = {
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { viewModel.settings.setProactiveIntelligenceEnabled(it) }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setProactiveIntelligenceEnabled(!enabled)
                            }
                        )
                    }

                    if (enabled) {
                        add {
                            SettingsItem(
                                headlineContent = { Text("Modèle d'analyse proactive") },
                                supportingContent = {
                                    val displayName = if (selectedModel.isNullOrBlank()) {
                                        "Modèle par défaut du chat"
                                    } else {
                                        modelDisplayName(
                                            selectedModel!!,
                                            modelAliases,
                                            customProviders,
                                            modelProviderNames[selectedModel] != false
                                        )
                                    }
                                    Text(displayName)
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable { showModelDialog = true }
                            )
                        }
                    }
                }
            )

            // Group 2: Prompt Système
            if (enabled) {
                SettingsGroup(
                    title = "Prompt système d'analyse",
                    items = listOf {
                        PromptSettingItem(
                            title = "Instructions d'analyse",
                            description = "Personnaliser la manière dont les 5 orientations (Reprendre, Approfondir, Synergies, Automatisation, Veille) sont formulées.",
                            prompt = customPrompt.ifBlank { BuiltInPrompts.PROACTIVE_INTELLIGENCE_SYSTEM },
                            onClick = { showPromptDialog = true }
                        )
                    }
                )

                // Group 3: Aperçu & Régénération
                SettingsGroup(
                    title = "Aperçu des recommandations actuelles",
                    items = listOf {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (proactiveTasks.isEmpty()) {
                                Text(
                                    text = "Aucune recommandation active pour le moment.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                proactiveTasks.forEach { task ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = task.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = task.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (isGenerating) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "Analyse en cours…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Dialog: Modèle
    if (showModelDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showModelDialog = false },
            title = { Text("Sélectionner le modèle", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SettingsItem(
                            headlineContent = { Text("Modèle par défaut du chat") },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedModel == null,
                                    onClick = {
                                        viewModel.settings.setProactiveIntelligenceModel(null)
                                        showModelDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setProactiveIntelligenceModel(null)
                                showModelDialog = false
                            }
                        )
                    }
                    items(candidateModels, key = { it }) { model ->
                        val isSelected = selectedModel == model
                        val displayName = modelDisplayName(
                            model,
                            modelAliases,
                            customProviders,
                            modelProviderNames[model] != false
                        )
                        SettingsItem(
                            headlineContent = { Text(displayName) },
                            leadingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.settings.setProactiveIntelligenceModel(model)
                                        showModelDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setProactiveIntelligenceModel(model)
                                showModelDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("Fermer") }
            }
        )
    }

    // Dialog: Prompt système
    if (showPromptDialog) {
        var editedPrompt by remember {
            mutableStateOf(customPrompt.ifBlank { BuiltInPrompts.PROACTIVE_INTELLIGENCE_SYSTEM })
        }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showPromptDialog = false },
            title = { Text("Prompt d'Intelligence Proactive", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = editedPrompt,
                        onValueChange = { editedPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 340.dp),
                        label = { Text("Instructions système") },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    editedPrompt = BuiltInPrompts.PROACTIVE_INTELLIGENCE_SYSTEM
                }) {
                    Text("Rétablir défaut")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showPromptDialog = false }) { Text("Annuler") }
                    Button(onClick = {
                        viewModel.settings.setProactiveIntelligencePrompt(editedPrompt)
                        showPromptDialog = false
                    }) {
                        Text("Enregistrer")
                    }
                }
            }
        )
    }
}
