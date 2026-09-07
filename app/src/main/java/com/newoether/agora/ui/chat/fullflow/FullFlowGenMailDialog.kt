package com.newoether.agora.ui.chat.fullflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.newoether.agora.ui.theme.OutfitFamily

@Composable
fun FullFlowGenMailDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onGenerateMail: (prompt: String) -> Unit,
) {
    if (!visible) return

    var subject by remember { mutableStateOf("") }
    var recipientType by remember { mutableStateOf("Client") }
    var tone by remember { mutableStateOf("Professionnel") }
    var keyPoints by remember { mutableStateOf("") }

    val recipients = listOf("Client", "Collègue", "Direction", "Partenaire", "Recruteur")
    val tones = listOf("Professionnel", "Chaleureux", "Direct", "Diplomatique", "Exécutif")

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF11151A),
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF1E293B)))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF90CAF9),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GenMail",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Rédacteur d'emails sur mesure propulsé par FullFlow AI",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                // Subject input
                Text(
                    text = "Objet / Objectif de l'email",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    placeholder = { Text("Ex: Relance proposition commerciale ou invitation", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF90CAF9),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF1E242B),
                        unfocusedContainerColor = Color(0xFF161A20),
                    ),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Destinataire chips
                Text(
                    text = "Destinataire",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recipients.take(3).forEach { item ->
                        FilterChip(
                            selected = recipientType == item,
                            onClick = { recipientType = item },
                            label = { Text(item, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E3A5F),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1A1F26),
                                labelColor = Color.White.copy(alpha = 0.7f),
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tone chips
                Text(
                    text = "Ton",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tones.take(3).forEach { item ->
                        FilterChip(
                            selected = tone == item,
                            onClick = { tone = item },
                            label = { Text(item, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E3A5F),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1A1F26),
                                labelColor = Color.White.copy(alpha = 0.7f),
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Key points input
                Text(
                    text = "Détails & points clés",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = keyPoints,
                    onValueChange = { keyPoints = it },
                    placeholder = { Text("Indiquez les détails importants, dates, délais...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF90CAF9),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF1E242B),
                        unfocusedContainerColor = Color(0xFF161A20),
                    ),
                    maxLines = 4,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit button
                Button(
                    onClick = {
                        val prompt = buildString {
                            append("Rédige un email professionnel et impeccable avec les spécifications suivantes :\n")
                            if (subject.isNotBlank()) append("- Objet/Thème : $subject\n")
                            append("- Destinataire : $recipientType\n")
                            append("- Ton adopté : $tone\n")
                            if (keyPoints.isNotBlank()) append("- Points à inclure : $keyPoints\n")
                            append("\nFournis d'abord l'Objet suggéré, puis le corps de l'email complet, prêt à l'envoi.")
                        }
                        onGenerateMail(prompt)
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White,
                    ),
                    enabled = subject.isNotBlank() || keyPoints.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Générer l'email avec FullFlow", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}
