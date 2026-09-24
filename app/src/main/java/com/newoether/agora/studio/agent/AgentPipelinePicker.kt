package com.newoether.agora.studio.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.studio.agent.db.AgentEntity
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.studio.agent.db.PipelineEntity
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Controller for showing/hiding the Agent Pipeline picker dialog.
 */
object AgentPipelineController {
    var visible by mutableStateOf(false)
        private set
    var pendingQuestion by mutableStateOf<String?>(null)
        private set

    fun open(question: String) {
        pendingQuestion = question
        visible = true
    }

    fun close() {
        visible = false
        pendingQuestion = null
    }
}

/**
 * Dialog for selecting and running an agent pipeline on a question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPipelinePickerHost() {
    if (!AgentPipelineController.visible) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as? com.newoether.agora.AgoraApplication
    val container = remember(app) { app?.requireContainer() }
    val agentDao = remember(container) { container?.database?.agentDao() }
    val scope = rememberCoroutineScope()

    var selectedPipeline by remember { mutableStateOf<PipelineEntity?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PipelineResult?>(null) }

    val pipelines by agentDao?.observeActivePipelines()?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    Dialog(
        onDismissRequest = { AgentPipelineController.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F1216),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pipeline d'Agents",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Chainage d'experts sur votre question",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Question preview
                val question = AgentPipelineController.pendingQuestion ?: ""
                if (question.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = AgoraAlpha.Subtle),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Text(
                            text = question,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 3,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Pipeline list
                if (result != null) {
                    // Show result
                    PipelineResultView(result = result!!)
                } else if (isRunning) {
                    // Show progress
                    PipelineProgressView(progressMessage = progressMessage)
                } else {
                    // Show pipeline picker
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                    ) {
                        items(pipelines) { pipeline ->
                            PipelineOptionCard(
                                pipeline = pipeline,
                                isSelected = selectedPipeline?.id == pipeline.id,
                                onClick = { selectedPipeline = pipeline }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { AgentPipelineController.close() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Annuler", color = Color.White.copy(alpha = AgoraAlpha.Hint))
                        }

                        Button(
                            onClick = {
                                selectedPipeline?.let { pipeline ->
                                    isRunning = true
                                    progressMessage = "Initialisation du pipeline..."
                                    scope.launch {
                                        try {
                                            val executor = AgentPipelineExecutor(context)
                                            val res = executor.execute(
                                                question = question,
                                                pipeline = pipeline,
                                                onProgress = { progress ->
                                                    progressMessage = "${progress.agentName} (${progress.stepIndex}/${progress.totalSteps})"
                                                }
                                            )
                                            result = res
                                            isRunning = false
                                        } catch (e: Exception) {
                                            progressMessage = "Erreur : ${e.localizedMessage}"
                                            isRunning = false
                                        }
                                    }
                                }
                            },
                            enabled = selectedPipeline != null && question.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("Lancer", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineOptionCard(
    pipeline: PipelineEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val agentIds = remember(pipeline.agentIdsJson) {
        try {
            Json.parseToJsonElement(pipeline.agentIdsJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) { emptyList() }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Subtle) else Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Handle) else Color.White.copy(alpha = AgoraAlpha.Subtle)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Divider))
            ) {
                Icon(Icons.Default.AccountTree, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pipeline.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${agentIds.size} agents · ${pipeline.loopCount} boucle${if (pipeline.loopCount > 1) "s" else ""}",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }

            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PipelineProgressView(progressMessage: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = progressMessage,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PipelineResultView(result: PipelineResult) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Agent contributions (collapsible)
        if (result.agentContributions.isNotEmpty()) {
            Text(
                text = "Contributions des agents",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            result.agentContributions.forEach { (name, output) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = AgoraAlpha.Subtle),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        Text(
                            text = output.take(150) + if (output.length > 150) "..." else "",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = AgoraAlpha.Hint)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Final synthesis
        Text(
            text = "Synthèse Finale",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF34D399),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF34D399).copy(alpha = AgoraAlpha.Subtle),
            border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = result.finalSynthesis,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 19.sp,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { AgentPipelineController.close() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Fermer", color = Color.White.copy(alpha = AgoraAlpha.Hint))
            }
        }
    }
}
