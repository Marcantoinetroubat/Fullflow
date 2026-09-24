package com.newoether.agora.ui.chat.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity
import com.newoether.agora.fulllive.viewmodel.FullLiveViewModel

/**
 * Controller for the Live Voice feature: a single icon in the input bar
 * that opens the persona picker, then launches a full-screen voice session.
 */
object LiveVoiceController {
    var showPersonaPicker by mutableStateOf(false)
        private set

    fun open() { showPersonaPicker = true }
    fun close() { showPersonaPicker = false }
}

/**
 * Host: shows the persona picker, then the FullLive voice session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceHost() {
    if (!LiveVoiceController.showPersonaPicker) return

    val viewModel: FullLiveViewModel = viewModel()
    val personas by viewModel.personas.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()

    // When a persona is selected, FullLiveViewModel navigates to CurrentScreen.Conversation
    // We detect this and close the picker, then show the FullLive session
    val isConversationActive = currentScreen !is com.newoether.agora.fulllive.viewmodel.CurrentScreen.Home

    if (isConversationActive) {
        val context = LocalContext.current
        // Full-screen voice session — reuse FullLiveApp
        Dialog(
            onDismissRequest = {
                viewModel.navigateHome()
                LiveVoiceController.close()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                com.newoether.agora.fulllive.FullLiveApp(
                    viewModel = viewModel,
                    onShareText = { content, title ->
                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, content)
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, title))
                    },
                )
            }
        }
    } else {
        // Persona picker dialog
        AlertDialog(
            onDismissRequest = { LiveVoiceController.close() },
            title = { Text("Choisir un persona vocal", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (personas.isEmpty()) {
                        Text(
                            "Aucun persona. Créez-en un dans Paramètres → Personas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(personas) { persona ->
                                PersonaChip(persona = persona) {
                                    viewModel.startConversation(persona)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { LiveVoiceController.close() }) {
                    Text("Fermer")
                }
            },
        )
    }
}

@Composable
private fun PersonaChip(persona: PersonaEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    persona.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                if (persona.description.isNotBlank()) {
                    Text(
                        persona.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color(0xFF4FC3F7),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
