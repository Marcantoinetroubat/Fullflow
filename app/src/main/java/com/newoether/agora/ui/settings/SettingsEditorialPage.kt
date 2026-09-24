package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEditorialPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val editorialEnabled by viewModel.settings.editorialEnabled.collectAsState()
    val editorialImageModel by viewModel.settings.editorialImageModel.collectAsState()
    val editorialImagePrompt by viewModel.settings.editorialImagePrompt.collectAsState()
    val editorialImageFrequency by viewModel.settings.editorialImageFrequency.collectAsState()
    val editorialSerifEnabled by viewModel.settings.editorialSerifEnabled.collectAsState()

    val availableModels by viewModel.settings.availableModels.collectAsState()
    var showModelDialog by remember { mutableStateOf(false) }

    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    // Flatten all models across providers for the picker
    val imageModelsList = remember(availableModels) {
        availableModels.values.flatten().filter { 
            it.contains("image", ignoreCase = true) || 
            it.contains("dall-e", ignoreCase = true) || 
            it.contains("flux", ignoreCase = true) ||
            it.contains("midjourney", ignoreCase = true) ||
            it.contains("stable-diffusion", ignoreCase = true)
        }.ifEmpty { 
            listOf("openai:dall-e-3", "openai:dall-e-2", "gemini:imagen-3") 
        }
    }

    CollapsingSettingsScaffold(
        title = "Rendu Éditorial Magazine",
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("editorial.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = "Présentation de l'Article", items = buildList {
                add {
                    SettingsItem(
                        headlineContent = { Text("Activer le Rendu Éditorial") },
                        supportingContent = { Text("Transforme chaque réponse structurée en un magnifique article de presse premium.") },
                        leadingContent = { Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = editorialEnabled,
                                onCheckedChange = { viewModel.settings.setEditorialEnabled(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.settings.setEditorialEnabled(!editorialEnabled) }
                    )
                }

                if (editorialEnabled) {
                    add {
                        SettingsItem(
                            headlineContent = { Text("Police d'écriture Serif") },
                            supportingContent = { Text("Utilise une police à empattements littéraire de style livre ou journal haut de gamme.") },
                            leadingContent = { Icon(Icons.Default.FontDownload, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Switch(
                                    checked = editorialSerifEnabled,
                                    onCheckedChange = { viewModel.settings.setEditorialSerifEnabled(it) }
                                )
                            },
                            modifier = Modifier.clickable { viewModel.settings.setEditorialSerifEnabled(!editorialSerifEnabled) }
                        )
                    }
                }
            })

            if (editorialEnabled) {
                SettingsGroup(title = "Illustrations de Section Automatiques", items = buildList {
                    add {
                        SettingsItem(
                            headlineContent = { Text("Fréquence des illustrations") },
                            supportingContent = {
                                Text(
                                    when (editorialImageFrequency) {
                                        0 -> "Désactivé (pas d'images de section)"
                                        1 -> "Toutes les sections"
                                        else -> "Toutes les $editorialImageFrequency sections"
                                    }
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                val nextFreq = when (editorialImageFrequency) {
                                    0 -> 1
                                    1 -> 2
                                    2 -> 3
                                    3 -> 0
                                    else -> 2
                                }
                                viewModel.settings.setEditorialImageFrequency(nextFreq)
                            }
                        )
                    }

                    if (editorialImageFrequency > 0) {
                        add {
                            SettingsItem(
                                headlineContent = { Text("Modèle de génération d'images") },
                                supportingContent = { Text(editorialImageModel ?: "openai:dall-e-3") },
                                leadingContent = { Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { showModelDialog = true }
                            )
                        }

                        add {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "Prompt de structure d'illustrations",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = editorialImagePrompt,
                                    onValueChange = { viewModel.settings.setEditorialImagePrompt(it) },
                                    placeholder = { Text("Ex: Style aquarelle, couleurs chaudes...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { 
                                        viewModel.settings.setEditorialImagePrompt("Illustration éditoriale de style magazine premium, artistique, détaillée, sans texte.") 
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Réinitialiser par défaut", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    // Image Model Picker Dialog
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Sélectionner le modèle d'illustrations", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(imageModelsList) { m ->
                        val isSelected = m == (editorialImageModel ?: "openai:dall-e-3")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.settings.setEditorialImageModel(m)
                                    showModelDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.settings.setEditorialImageModel(m)
                                    showModelDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = m,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("Fermer")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
