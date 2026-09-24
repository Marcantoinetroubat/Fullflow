package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity
import com.newoether.agora.fulllive.viewmodel.FullLiveViewModel
import com.newoether.agora.ui.ds.AgoraDialog
import com.newoether.agora.R

/**
 * « Personas » settings page: full CRUD for voice assistant personas.
 * Personas use the providers/models/TTS already configured in FullFlow —
 * no separate API keys or engines to manage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPersonasPage(viewModel: com.newoether.agora.viewmodel.ChatViewModel, onBack: () -> Unit) {
    val fullLiveViewModel: FullLiveViewModel = viewModel()
    val personas by fullLiveViewModel.personas.collectAsState()
    var editingPersona by remember { mutableStateOf<PersonaEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personas vocaux") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { editingPersona = null; showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau persona")
                    }
                },
            )
        },
    ) { padding ->
        if (personas.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Aucun persona vocal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Créez un persona pour démarrer une conversation vocale live avec l'icône micro dans la barre de saisie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        onEdit = { editingPersona = persona; showEditor = true },
                        onDuplicate = { fullLiveViewModel.duplicatePersona(persona) },
                        onDelete = { fullLiveViewModel.deletePersona(persona.id) },
                    )
                }
            }
        }
    }

    if (showEditor) {
        PersonaEditorDialog(
            persona = editingPersona,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                fullLiveViewModel.savePersona(updated)
                showEditor = false
            },
        )
    }
}

@Composable
private fun PersonaCard(
    persona: PersonaEntity,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
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
                            maxLines = 2,
                        )
                    }
                    Text(
                        "Voix : ${persona.voice} · Modèle : ${persona.model.ifBlank { "par défaut" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Dupliquer", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AgoraDialog(
            title = "Supprimer « ${persona.name} » ?",
            onDismissRequest = { showDeleteConfirm = false },
            confirmText = "Supprimer",
            onConfirm = { showDeleteConfirm = false; onDelete() },
            dismissText = "Annuler",
            text = { Text("Cette action est irréversible.") },
            destructive = true,
        )
    }
}

@Composable
private fun PersonaEditorDialog(
    persona: PersonaEntity?,
    onDismiss: () -> Unit,
    onSave: (PersonaEntity) -> Unit,
) {
    var name by remember { mutableStateOf(persona?.name ?: "") }
    var description by remember { mutableStateOf(persona?.description ?: "") }
    var prompt by remember { mutableStateOf(persona?.prompt ?: "") }
    var voice by remember { mutableStateOf(persona?.voice ?: "Puck") }
    var reactivity by remember { mutableStateOf(persona?.reactivity ?: "balanced") }
    var creativity by remember { mutableStateOf(persona?.creativity ?: "balanced") }
    var greeting by remember { mutableStateOf(persona?.greeting ?: "persona") }
    var personaInfo by remember { mutableStateOf(persona?.personaInfo ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (persona == null) "Nouveau persona" else "Modifier ${persona.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = voice,
                    onValueChange = { voice = it },
                    label = { Text("Voix (Puck, Kore, Charon, Aoede...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Réactivité", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("reactive", "balanced", "patient").forEach { r ->
                        FilterChip(
                            selected = reactivity == r,
                            onClick = { reactivity = r },
                            label = { Text(when (r) { "reactive" -> "Réactif"; "balanced" -> "Équilibré"; else -> "Patient" }, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("Créativité", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("precise", "balanced", "creative").forEach { c ->
                        FilterChip(
                            selected = creativity == c,
                            onClick = { creativity = c },
                            label = { Text(when (c) { "precise" -> "Précis"; "balanced" -> "Équilibré"; else -> "Créatif" }, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("Ouverture", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = greeting == "persona",
                        onClick = { greeting = "persona" },
                        label = { Text("Le persona parle en premier", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = greeting == "user",
                        onClick = { greeting = "user" },
                        label = { Text("J'engage", fontSize = 11.sp) },
                    )
                }
                OutlinedTextField(
                    value = personaInfo,
                    onValueChange = { personaInfo = it },
                    label = { Text("Infos spécifiques (optionnel)") },
                    placeholder = { Text("Ce que ce persona doit savoir sur vous...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("System Prompt") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && prompt.isNotBlank()) {
                        onSave(
                            PersonaEntity(
                                id = persona?.id ?: "p_${System.currentTimeMillis()}",
                                name = name.trim(),
                                description = description.trim(),
                                model = persona?.model ?: "",
                                voice = voice.trim(),
                                reactivity = reactivity,
                                creativity = creativity,
                                greeting = greeting,
                                personaInfo = personaInfo.trim(),
                                prompt = prompt.trim(),
                                imageUri = persona?.imageUri,
                                gender = persona?.gender,
                                isSystem = persona?.isSystem ?: false,
                                sortOrder = persona?.sortOrder ?: 0,
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
