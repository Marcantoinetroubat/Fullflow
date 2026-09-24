package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
fun SettingsPodcastDistributionPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    // Read podcast generation settings (reuse existing ones)
    val podcastModel by viewModel.settings.podcastGenModel.collectAsState()
    val podcastTtsEngine by viewModel.settings.podcastGenTtsEngine.collectAsState()
    val podcastVoice by viewModel.settings.podcastGenVoice.collectAsState()

    CollapsingSettingsScaffold(
        title = "Distribution Podcast",
        onBack = onBack,
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = "Diffusion RSS", items = buildList {
                add {
                    SettingsItem(
                        headlineContent = { Text("Flux RSS du podcast") },
                        supportingContent = { Text("Génère un flux RSS 2.0 compatible Spotify, Apple Podcasts et Deezer pour vos épisodes.") },
                        leadingContent = { Icon(Icons.Default.RssFeed, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(checked = true, onCheckedChange = { /* Always on for now */ })
                        },
                    )
                }
            })

            SettingsGroup(title = "Email & Newsletter", items = buildList {
                add {
                    SettingsItem(
                        headlineContent = { Text("Envoi par email") },
                        supportingContent = { Text("Envoyez chaque épisode généré à votre liste de contacts par email.") },
                        leadingContent = { Icon(Icons.Default.Mail, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(checked = false, onCheckedChange = { /* TODO: implement email sending */ })
                        },
                    )
                }
            })

            SettingsGroup(title = "Configuration du modèle", items = buildList {
                add {
                    SettingsItem(
                        headlineContent = { Text("Modèle de script") },
                        supportingContent = { Text(podcastModel ?: "Modèle par défaut") },
                        leadingContent = { Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { /* TODO: model picker */ }
                    )
                }
                add {
                    SettingsItem(
                        headlineContent = { Text("Moteur vocal TTS") },
                        supportingContent = { Text("$podcastTtsEngine · Voix : $podcastVoice") },
                        leadingContent = { Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            })

            SettingsGroup(title = "Programmation", items = buildList {
                add {
                    SettingsItem(
                        headlineContent = { Text("Épisodes récurrents") },
                        supportingContent = { Text("Programmez la génération automatique d'épisodes (ex: chaque lundi à 7h).") },
                        leadingContent = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { /* TODO: link to automation */ }
                    )
                }
            })
        }
    }
}
