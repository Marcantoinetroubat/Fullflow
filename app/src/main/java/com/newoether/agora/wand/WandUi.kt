package com.newoether.agora.wand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.newoether.agora.ui.ds.AgoraAlpha

/** Accent ponté sur le Design System (même valeur, source unique). */
private val WandAccent = com.newoether.agora.ui.ds.FullLiveBridge.Purple
private val WandSurface = Color(0xFF101724)
private val WandBorder = Color(0xFF1E283A)
private val WandTextMuted = Color(0xFF94A3B8)

/**
 * Owns every wand dialog shown from the input bar: the 10-type picker and the
 * full-screen preview. Kept in the wand package so `ChatBottomBar.kt` stays
 * under the repository source-size policy.
 */
@Composable
fun WandPickerHost(
    textFieldState: TextFieldState,
    showPicker: Boolean,
    onDismissPicker: () -> Unit,
) {
    if (showPicker) {
        WandTypePickerDialog(
            onValidate = { WandController.compile(textFieldState.text.toString()) },
            onDismiss = onDismissPicker,
        )
    }
}

/**
 * Post-response entry point: shows the type picker for a text coming from an
 * existing chat message (see [WandMessageTrigger]). Compilation, preview and
 * composer delivery then flow through the regular wand pipeline.
 */
@Composable
fun WandMessagePickerHost(
    sourceText: String?,
    onDismiss: () -> Unit,
) {
    if (sourceText != null) {
        WandTypePickerDialog(
            onValidate = { WandController.compile(sourceText) },
            onDismiss = onDismiss,
        )
    }
}

/** Full-screen preview: original vs optimized, declared connections & skills, explicit choice. */
@Composable
fun WandPreviewDialog(
    preview: WandController.WandPreview,
    wiringStatus: Map<ConnectionId, Boolean> = emptyMap(),
    onSendOptimized: (String) -> Unit,
    onKeepOriginal: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D12),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = WandAccent, modifier = Modifier.size(20.dp))
                        Text(
                            "Aperçu baguette",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, "Fermer", tint = WandTextMuted, modifier = Modifier.size(20.dp))
                    }
                }

                Text(
                    "${preview.structureName} · ${preview.modelUsed} · ≈ ${"%.4f".format(preview.costUsd)} $",
                    fontSize = 11.sp,
                    color = WandTextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WandSection(title = "Texte original") {
                        Text(preview.rawText, fontSize = 13.sp, color = Color.White)
                    }
                    WandSection(title = "Prompt optimisé", highlight = true) {
                        Text(preview.optimized, fontSize = 13.sp, color = Color.White)
                    }
                    if (preview.ambiguities.isNotEmpty()) {
                        WandSection(title = "Ambiguïtés signalées") {
                            preview.ambiguities.forEach {
                                Text("• $it", fontSize = 12.sp, color = Color(0xFFFBBF24))
                            }
                        }
                    }
                    WandSection(title = "Connexions interrogées") {
                        if (preview.connections.isEmpty()) {
                            Text("Aucune", fontSize = 12.sp, color = WandTextMuted)
                        } else {
                            preview.connections.forEach { connection ->
                                val wired = wiringStatus[connection]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        when (wired) {
                                            true -> "✓"
                                            false -> "✗"
                                            null -> "•"
                                        },
                                        fontSize = 12.sp,
                                        color = when (wired) {
                                            true -> Color(0xFF34D399)
                                            false -> Color(0xFFEF4444)
                                            null -> Color.White
                                        },
                                    )
                                    Text(
                                        connection.label + when (wired) {
                                            true -> ""
                                            false -> " (non câblée via MCP)"
                                            null -> ""
                                        },
                                        fontSize = 12.sp,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    WandSection(title = "Compétences appliquées") {
                        if (preview.skills.isEmpty()) {
                            Text("Aucune", fontSize = 12.sp, color = WandTextMuted)
                        } else {
                            preview.skills.forEach {
                                Text("• ${it.removeSuffix(".md")}", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onKeepOriginal,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WandTextMuted),
                        border = BorderStroke(1.dp, WandTextMuted.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Garder l'original", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onSendOptimized(preview.optimized) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WandAccent,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Envoyer l'optimisé", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WandSection(
    title: String,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) WandAccent.copy(alpha = AgoraAlpha.Subtle) else WandSurface,
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (highlight) WandAccent.copy(alpha = 0.4f) else WandBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) WandAccent else WandTextMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            content()
        }
    }
}

/** Menu of the ten canonical request types + dynamic skill picker. */
@Composable
private fun WandTypePickerDialog(onValidate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WandSurface,
        title = {
            Text("Type de requête", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                RequestType.entries.forEach { type ->
                    val selected = type == WandController.selectedType
                    Surface(
                        onClick = { WandController.selectedType = type },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) WandAccent.copy(alpha = 0.18f) else Color(0xFF1B2330),
                        border = BorderStroke(
                            1.dp,
                            if (selected) WandAccent else Color.White.copy(alpha = AgoraAlpha.Subtle),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            type.label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }

                val skills = WandController.availableSkills()
                if (skills.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Compétences à mobiliser",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WandTextMuted,
                    )
                    skills.forEach { skill ->
                        val selected = skill.name in WandController.selectedSkillNames
                        Surface(
                            onClick = { WandController.toggleSkill(skill.name) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) WandAccent.copy(alpha = 0.18f) else Color(0xFF1B2330),
                            border = BorderStroke(
                                1.dp,
                                if (selected) WandAccent else Color.White.copy(alpha = AgoraAlpha.Subtle),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    skill.name.removeSuffix(".md"),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White,
                                )
                                if (skill.description.isNotBlank()) {
                                    Text(
                                        skill.description.take(90),
                                        fontSize = 10.sp,
                                        color = WandTextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onValidate()
            }) { Text("Valider", color = WandAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = WandTextMuted) }
        },
    )
}
