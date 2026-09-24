package com.newoether.agora.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraTabs
import com.newoether.agora.studio.agent.AgentDefaults
import com.newoether.agora.studio.agent.db.AgentEntity
import com.newoether.agora.studio.agent.db.PipelineEntity
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAgentsPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as? com.newoether.agora.AgoraApplication }
    val container = remember(app) { app?.requireContainer() }
    val db = container?.database
    val agentDao = remember { db?.agentDao() }
    val scope = rememberCoroutineScope()

    if (agentDao == null) {
        // Database not ready yet
        CollapsingSettingsScaffold(title = "Agents & Pipelines", onBack = onBack) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    val agentsFlow = remember { agentDao.observeAllAgents() }
    val pipelinesFlow = remember { agentDao.observeAllPipelines() }
    val agents by agentsFlow.collectAsState(initial = emptyList())
    val pipelines by pipelinesFlow.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Agents, 1: Pipelines
    var showAgentEditor by remember { mutableStateOf<AgentEntity?>(null) }
    var showPipelineEditor by remember { mutableStateOf<PipelineEntity?>(null) }
    var showNewAgentDialog by remember { mutableStateOf(false) }
    var showNewPipelineDialog by remember { mutableStateOf(false) }

    CollapsingSettingsScaffold(
        title = "Agents & Pipelines",
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab bar canonique (onglets soulignés M3, 48dp min)
            AgoraTabs(
                tabs = listOf("Agents (${agents.size})", "Pipelines (${pipelines.size})"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )

            Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

            when (selectedTab) {
                0 -> AgentsList(
                    agents = agents,
                    onEdit = { showAgentEditor = it },
                    onDelete = { scope.launch { agentDao.deleteAgent(it) } },
                    onToggle = { agent, active -> scope.launch { agentDao.setAgentActive(agent.id, active) } },
                    onNew = { showNewAgentDialog = true },
                )
                1 -> PipelinesList(
                    pipelines = pipelines,
                    agents = agents,
                    onEdit = { showPipelineEditor = it },
                    onDelete = { scope.launch { agentDao.deletePipeline(it) } },
                    onToggle = { pipeline, active -> scope.launch { agentDao.setPipelineActive(pipeline.id, active) } },
                    onNew = { showNewPipelineDialog = true },
                )
            }
        }
    }

    // Agent Editor Dialog
    showAgentEditor?.let { agent ->
        AgentEditorDialog(
            agent = agent,
            onDismiss = { showAgentEditor = null },
            onSave = { updated ->
                scope.launch {
                    agentDao.upsertAgent(updated)
                    showAgentEditor = null
                }
            }
        )
    }

    // Pipeline Editor Dialog
    showPipelineEditor?.let { pipeline ->
        PipelineEditorDialog(
            pipeline = pipeline,
            agents = agents,
            onDismiss = { showPipelineEditor = null },
            onSave = { updated ->
                scope.launch {
                    agentDao.upsertPipeline(updated)
                    showPipelineEditor = null
                }
            }
        )
    }

    // New Agent Dialog
    if (showNewAgentDialog) {
        NewAgentDialog(
            onDismiss = { showNewAgentDialog = false },
            onCreate = { name, specialty, systemPrompt, modelId ->
                scope.launch {
                    val agent = AgentEntity(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        specialty = specialty,
                        systemPrompt = systemPrompt,
                        modelId = modelId,
                        isActive = true,
                        isSystem = false,
                        sortOrder = agents.size + 1,
                    )
                    agentDao.upsertAgent(agent)
                    showNewAgentDialog = false
                }
            }
        )
    }

    // New Pipeline Dialog
    if (showNewPipelineDialog) {
        NewPipelineDialog(
            agents = agents.filter { it.isActive },
            onDismiss = { showNewPipelineDialog = false },
            onCreate = { name, description, agentIds, loopCount, synthesizerId ->
                scope.launch {
                    val pipeline = PipelineEntity(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        description = description,
                        agentIdsJson = buildJsonArray { agentIds.forEach { add(it) } }.toString(),
                        loopCount = loopCount,
                        synthesizerAgentId = synthesizerId,
                        isActive = true,
                        isSystem = false,
                        sortOrder = pipelines.size + 1,
                    )
                    agentDao.upsertPipeline(pipeline)
                    showNewPipelineDialog = false
                }
            }
        )
    }
}

@Composable
private fun AgentsList(
    agents: List<AgentEntity>,
    onEdit: (AgentEntity) -> Unit,
    onDelete: (AgentEntity) -> Unit,
    onToggle: (AgentEntity, Boolean) -> Unit,
    onNew: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedButton(
                onClick = onNew,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nouvel Agent", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(agents) { agent ->
            AgentRowCard(
                agent = agent,
                onEdit = { onEdit(agent) },
                onDelete = { onDelete(agent) },
                onToggle = { onToggle(agent, it) },
            )
        }
    }
}

@Composable
private fun AgentRowCard(
    agent: AgentEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(agent.color).copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(1.dp, Color(agent.color).copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(agent.color).copy(alpha = AgoraAlpha.Divider))
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(agent.color),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = agent.specialty,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                    maxLines = 1
                )
                Text(
                    text = agent.systemPrompt.take(80) + "...",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                    maxLines = 2
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Edit, "Modifier", tint = Color.White.copy(alpha = AgoraAlpha.Hint), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                }
                Switch(
                    checked = agent.isActive,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PipelinesList(
    pipelines: List<PipelineEntity>,
    agents: List<AgentEntity>,
    onEdit: (PipelineEntity) -> Unit,
    onDelete: (PipelineEntity) -> Unit,
    onToggle: (PipelineEntity, Boolean) -> Unit,
    onNew: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedButton(
                onClick = onNew,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nouveau Pipeline", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(pipelines) { pipeline ->
            PipelineRowCard(
                pipeline = pipeline,
                agents = agents,
                onEdit = { onEdit(pipeline) },
                onDelete = { onDelete(pipeline) },
                onToggle = { onToggle(pipeline, it) },
            )
        }
    }
}

@Composable
private fun PipelineRowCard(
    pipeline: PipelineEntity,
    agents: List<AgentEntity>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val agentIds = remember(pipeline.agentIdsJson) {
        try {
            Json.parseToJsonElement(pipeline.agentIdsJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) { emptyList() }
    }
    val agentNames = agentIds.mapNotNull { id -> agents.find { it.id == id }?.name }.joinToString(" → ")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AccountTree, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pipeline.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (pipeline.description.isNotBlank()) {
                        Text(
                            text = pipeline.description,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Edit, "Modifier", tint = Color.White.copy(alpha = AgoraAlpha.Hint), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, "Supprimer", tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                    }
                    Switch(
                        checked = pipeline.isActive,
                        onCheckedChange = onToggle,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (agentNames.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Chaîne : $agentNames",
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8),
                    maxLines = 2
                )
                Text(
                    text = "Boucles : ${pipeline.loopCount} · Synthétiseur : ${agents.find { it.id == pipeline.synthesizerAgentId }?.name ?: "défaut"}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint)
                )
            }
        }
    }
}

@Composable
private fun AgentEditorDialog(
    agent: AgentEntity,
    onDismiss: () -> Unit,
    onSave: (AgentEntity) -> Unit,
) {
    var name by remember { mutableStateOf(agent.name) }
    var specialty by remember { mutableStateOf(agent.specialty) }
    var systemPrompt by remember { mutableStateOf(agent.systemPrompt) }
    var modelId by remember { mutableStateOf(agent.modelId) }

    com.newoether.agora.ui.ds.AgoraDialog(
        title = "Modifier ${agent.name}",
        onDismissRequest = onDismiss,
        confirmText = "Sauvegarder",
        onConfirm = { onSave(agent.copy(name = name, specialty = specialty, systemPrompt = systemPrompt, modelId = modelId)) },
        dismissText = "Annuler",
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.newoether.agora.ui.ds.AgoraTextField(value = name, onValueChange = { name = it }, label = "Nom")
                com.newoether.agora.ui.ds.AgoraTextField(value = specialty, onValueChange = { specialty = it }, label = "Spécialité")
                com.newoether.agora.ui.ds.AgoraTextField(value = modelId, onValueChange = { modelId = it }, label = "Modèle (Provider:modelId)")
                com.newoether.agora.ui.ds.AgoraTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = "Prompt système",
                    modifier = Modifier.height(150.dp),
                    singleLine = false,
                )
            }
        },
    )
}

@Composable
private fun PipelineEditorDialog(
    pipeline: PipelineEntity,
    agents: List<AgentEntity>,
    onDismiss: () -> Unit,
    onSave: (PipelineEntity) -> Unit,
) {
    var name by remember { mutableStateOf(pipeline.name) }
    var description by remember { mutableStateOf(pipeline.description) }
    var loopCount by remember { mutableIntStateOf(pipeline.loopCount) }

    com.newoether.agora.ui.ds.AgoraDialog(
        title = "Modifier ${pipeline.name}",
        onDismissRequest = onDismiss,
        confirmText = "Sauvegarder",
        onConfirm = { onSave(pipeline.copy(name = name, description = description, loopCount = loopCount)) },
        dismissText = "Annuler",
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.newoether.agora.ui.ds.AgoraTextField(value = name, onValueChange = { name = it }, label = "Nom")
                com.newoether.agora.ui.ds.AgoraTextField(value = description, onValueChange = { description = it }, label = "Description")

                // Loop count slider
                com.newoether.agora.ui.ds.AgoraSliderRow(
                    title = "Nombre de boucles",
                    value = loopCount.toFloat(),
                    onValueChange = { loopCount = it.toInt() },
                    valueLabel = "$loopCount",
                    valueRange = 1f..5f,
                    steps = 3,
                )
            }
        },
    )
}

@Composable
private fun NewAgentDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }

    com.newoether.agora.ui.ds.AgoraDialog(
        title = "Nouvel Agent",
        onDismissRequest = onDismiss,
        confirmText = "Créer",
        onConfirm = { if (name.isNotBlank() && systemPrompt.isNotBlank()) onCreate(name, specialty, systemPrompt, modelId) },
        dismissText = "Annuler",
        confirmEnabled = name.isNotBlank() && systemPrompt.isNotBlank(),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.newoether.agora.ui.ds.AgoraTextField(value = name, onValueChange = { name = it }, label = "Nom de l'agent")
                com.newoether.agora.ui.ds.AgoraTextField(value = specialty, onValueChange = { specialty = it }, label = "Spécialité")
                com.newoether.agora.ui.ds.AgoraTextField(value = modelId, onValueChange = { modelId = it }, label = "Modèle (Provider:modelId, vide = par défaut)")
                com.newoether.agora.ui.ds.AgoraTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = "Prompt système",
                    modifier = Modifier.height(120.dp),
                    singleLine = false,
                )
            }
        },
    )
}

@Composable
private fun NewPipelineDialog(
    agents: List<AgentEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>, Int, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loopCount by remember { mutableIntStateOf(1) }
    var selectedAgentIds by remember { mutableStateOf(setOf<String>()) }
    var synthesizerId by remember { mutableStateOf("") }

    com.newoether.agora.ui.ds.AgoraDialog(
        title = "Nouveau Pipeline",
        onDismissRequest = onDismiss,
        confirmText = "Créer",
        onConfirm = {
            if (name.isNotBlank() && selectedAgentIds.isNotEmpty()) {
                onCreate(name, description, selectedAgentIds.toList(), loopCount, synthesizerId)
            }
        },
        dismissText = "Annuler",
        confirmEnabled = name.isNotBlank() && selectedAgentIds.isNotEmpty(),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                com.newoether.agora.ui.ds.AgoraTextField(value = name, onValueChange = { name = it }, label = "Nom du pipeline")
                com.newoether.agora.ui.ds.AgoraTextField(value = description, onValueChange = { description = it }, label = "Description")

                Text("Agents (sélectionnez dans l'ordre)", style = MaterialTheme.typography.labelMedium)
                agents.forEach { agent ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAgentIds = if (selectedAgentIds.contains(agent.id)) {
                                    selectedAgentIds - agent.id
                                } else {
                                    selectedAgentIds + agent.id
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selectedAgentIds.contains(agent.id),
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(agent.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(agent.specialty, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                com.newoether.agora.ui.ds.AgoraSliderRow(
                    title = "Nombre de boucles",
                    value = loopCount.toFloat(),
                    onValueChange = { loopCount = it.toInt() },
                    valueLabel = "$loopCount",
                    valueRange = 1f..5f,
                    steps = 3,
                )
            }
        },
    )
}
