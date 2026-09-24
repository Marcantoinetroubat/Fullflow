package com.newoether.agora.fulllive.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity

val MODEL_OPTIONS = listOf(
    Pair("", "Modèle par défaut (Configuration)"),
    Pair("gemini-3.8-live", "Gemini 3.8 Live (Google)"),
    Pair("gemini-3.8-live-extended-thinking", "Gemini 3.8 Extended Thinking (Raisonnement)"),
    Pair("gpt-live-1", "GPT Live 1 (OpenAI Full-Duplex)"),
    Pair("gpt-realtime-2.1", "gpt-realtime-2.1 (OpenAI Expressif)"),
    Pair("gpt-realtime-2.1-mini", "gpt-realtime-2.1-mini (OpenAI Économique)"),
    Pair("grok-voice-think-fast-2.0", "Grok Voice Think Fast 2.0 (xAI)"),
    Pair("openai-custom", "OpenAI API Custom (OpenRouter, ZenMux, etc.)")
)

val GEMINI_VOICES = listOf("Puck", "Kore", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
val OPENAI_VOICES = listOf("alloy", "ash", "ballad", "cedar", "coral", "echo", "marin", "sage", "shimmer", "verse")
val GROK_VOICES = listOf("eve", "ara", "rex", "sal", "leo", "aurora", "luna", "atlas", "orion", "carina")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditorDialog(
    persona: PersonaEntity?,
    onDismiss: () -> Unit,
    onSave: (PersonaEntity) -> Unit,
    onExportJson: ((PersonaEntity) -> Unit)? = null
) {
    var name by remember { mutableStateOf(persona?.name ?: "") }
    var description by remember { mutableStateOf(persona?.description ?: "") }
    var model by remember { mutableStateOf(persona?.model ?: "") }
    var voice by remember { mutableStateOf(persona?.voice ?: "Puck") }
    var reactivity by remember { mutableStateOf(persona?.reactivity ?: "balanced") }
    var creativity by remember { mutableStateOf(persona?.creativity ?: "balanced") }
    var greeting by remember { mutableStateOf(persona?.greeting ?: "persona") }
    var personaInfo by remember { mutableStateOf(persona?.personaInfo ?: "") }
    var prompt by remember {
        mutableStateOf(
            persona?.prompt ?: """# Identité
Tu es un assistant vocal bienveillant, clair et direct.

# Règles pour l'oral
- Sois concis : 2-3 phrases maximum par intervention.
- Pas de formatage markdown, parle avec des intonations naturelles.""".trimIndent()
        )
    }

    var modelExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var reactivityExpanded by remember { mutableStateOf(false) }
    var creativityExpanded by remember { mutableStateOf(false) }
    var greetingExpanded by remember { mutableStateOf(false) }

    val availableVoices = remember(model) {
        when {
            model.startsWith("grok") -> GROK_VOICES
            model.startsWith("gpt") || model == "openai-custom" -> OPENAI_VOICES
            else -> GEMINI_VOICES
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (persona != null) "Modifier le persona" else "Nouveau persona",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    placeholder = { Text("Ex: Gérard, Ada, Atlas...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description courte") },
                    placeholder = { Text("Ex: Mentor philosophique, ingénieur tech...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Model selector
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    OutlinedTextField(
                        value = MODEL_OPTIONS.find { it.first == model }?.second ?: "Modèle par défaut",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Moteur & Modèle d'IA") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        MODEL_OPTIONS.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.second) },
                                onClick = {
                                    model = opt.first
                                    modelExpanded = false
                                    // ensure voice compatibility
                                    val newVoices = when {
                                        model.startsWith("grok") -> GROK_VOICES
                                        model.startsWith("gpt") -> OPENAI_VOICES
                                        else -> GEMINI_VOICES
                                    }
                                    if (voice !in newVoices) {
                                        voice = newVoices.first()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Voice selector
                ExposedDropdownMenuBox(
                    expanded = voiceExpanded,
                    onExpandedChange = { voiceExpanded = !voiceExpanded }
                ) {
                    OutlinedTextField(
                        value = voice,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Voix") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = voiceExpanded,
                        onDismissRequest = { voiceExpanded = false }
                    ) {
                        availableVoices.forEach { vName ->
                            DropdownMenuItem(
                                text = { Text(vName) },
                                onClick = {
                                    voice = vName
                                    voiceExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reactivity (VAD)
                    ExposedDropdownMenuBox(
                        expanded = reactivityExpanded,
                        onExpandedChange = { reactivityExpanded = !reactivityExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = when (reactivity) {
                                "reactive" -> "Réactif"
                                "patient" -> "Patient"
                                "very-patient" -> "Très patient"
                                else -> "Équilibré"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Réactivité") },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = reactivityExpanded,
                            onDismissRequest = { reactivityExpanded = false }
                        ) {
                            listOf(
                                "reactive" to "Réactif (coupe vite)",
                                "balanced" to "Équilibré",
                                "patient" to "Patient (attend)",
                                "very-patient" to "Très patient"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        reactivity = key
                                        reactivityExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Creativity
                    ExposedDropdownMenuBox(
                        expanded = creativityExpanded,
                        onExpandedChange = { creativityExpanded = !creativityExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = when (creativity) {
                                "precise" -> "Précis"
                                "creative" -> "Créatif"
                                "wild" -> "Fantaisiste"
                                else -> "Équilibré"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Créativité") },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = creativityExpanded,
                            onDismissRequest = { creativityExpanded = false }
                        ) {
                            listOf(
                                "precise" to "Précis (factuel)",
                                "balanced" to "Équilibré",
                                "creative" to "Créatif",
                                "wild" to "Fantaisiste"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        creativity = key
                                        creativityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Greeting
                ExposedDropdownMenuBox(
                    expanded = greetingExpanded,
                    onExpandedChange = { greetingExpanded = !greetingExpanded }
                ) {
                    OutlinedTextField(
                        value = if (greeting == "user") "J'engage la conversation" else "Le persona parle en premier",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Début d'échange") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = greetingExpanded,
                        onDismissRequest = { greetingExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Le persona parle en premier") },
                            onClick = {
                                greeting = "persona"
                                greetingExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("J'engage la conversation") },
                            onClick = {
                                greeting = "user"
                                greetingExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = personaInfo,
                    onValueChange = { personaInfo = it },
                    label = { Text("Infos spécifiques à ce persona") },
                    placeholder = { Text("Détails connus uniquement de ce copilote...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("System Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 6
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            prompt = """# Identité
Tu es $name, $description.

# Directives Orales
- Ton naturel, direct et chaleureux
- Réponses concises (2 à 3 phrases maximum)
- Pas de markdown dans tes réponses orales""".trimIndent()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.padding(2.dp))
                        Text("Générer prompt")
                    }

                    if (persona != null && onExportJson != null) {
                        OutlinedButton(
                            onClick = { onExportJson(persona) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.padding(2.dp))
                            Text("Exporter JSON")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val entity = PersonaEntity(
                                    id = persona?.id ?: ("p_" + System.currentTimeMillis()),
                                    name = name.trim(),
                                    description = description.trim(),
                                    model = model,
                                    voice = voice,
                                    reactivity = reactivity,
                                    creativity = creativity,
                                    greeting = greeting,
                                    personaInfo = personaInfo.trim(),
                                    prompt = prompt.trim(),
                                    imageUri = persona?.imageUri,
                                    gender = persona?.gender ?: "male",
                                    isSystem = persona?.isSystem ?: false,
                                    sortOrder = persona?.sortOrder ?: 10
                                )
                                onSave(entity)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sauvegarder")
                    }
                }
            }
        }
    }
}
