package com.newoether.agora.mesh.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.mesh.*
import com.newoether.agora.ui.ds.AgoraRadii
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.theme.OutfitFamily
import java.text.SimpleDateFormat
import java.util.*

enum class MeshStudioTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ROOMS("Salons", Icons.Default.Forum),
    TEAM("Équipe", Icons.Default.Groups),
    LOCKS("Verrous", Icons.Default.Lock),
    LEDGER("Empreintes", Icons.Default.Fingerprint),
    MISSIONS("Missions", Icons.Default.Bolt)
}

@Composable
fun MeshStudioDialog(
    onDismissRequest: () -> Unit
) {
    val broker = MeshController.broker
    var selectedTab by remember { mutableStateOf(MeshStudioTab.ROOMS) }

    val agents by broker.agents.collectAsState()
    val rooms by broker.rooms.collectAsState()
    val activeRoomId by broker.activeRoomId.collectAsState()
    val roomMessagesMap by broker.roomMessages.collectAsState()
    val fileLocks by broker.fileLocks.collectAsState()
    val ledger by broker.ledger.collectAsState()
    val missionSummaries by broker.missionSummaries.collectAsState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0D12))
                .systemBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF10141C))
                        .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Atelier Pi-Mesh",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                Box(
                                    modifier = Modifier
                                        .clip(AgoraRadii.Xs)
                                        .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                                        .padding(horizontal = AgoraSpacing.Xs, vertical = AgoraSpacing.Xxs)
                                ) {
                                    Text(
                                        text = MeshProtocol.PROTOCOL_VERSION,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                            Text(
                                text = "Coordination d'agents locale 100% sur l'appareil",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = AgoraAlpha.Hint)
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Tab Bar
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color(0xFF141A24),
                    contentColor = Color(0xFF38BDF8),
                    divider = { HorizontalDivider(color = Color.White.copy(alpha = AgoraAlpha.Subtle)) }
                ) {
                    MeshStudioTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontFamily = OutfitFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(AgoraSpacing.Lg)
                                )
                            }
                        )
                    }
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        MeshStudioTab.ROOMS -> RoomsTabContent(
                            rooms = rooms,
                            activeRoomId = activeRoomId,
                            messages = roomMessagesMap[activeRoomId].orEmpty(),
                            onSelectRoom = { broker.selectRoom(it) },
                            onSendMessage = { text -> broker.sendUserPrompt(activeRoomId, text) }
                        )
                        MeshStudioTab.TEAM -> TeamTabContent(
                            agents = agents,
                            onRevive = { broker.reviveAgent(it) },
                            onDirectMessage = { agentId ->
                                broker.selectRoom(MeshProtocol.ROOM_GENERAL)
                                selectedTab = MeshStudioTab.ROOMS
                            }
                        )
                        MeshStudioTab.LOCKS -> LocksTabContent(
                            locks = fileLocks,
                            onRelease = { path, holder -> broker.releaseFileLock(path, holder) },
                            onAcquire = { path, purpose ->
                                broker.acquireFileLock(path, "agent-writer", "Rédacteur", purpose)
                            }
                        )
                        MeshStudioTab.LEDGER -> LedgerTabContent(
                            entries = ledger
                        )
                        MeshStudioTab.MISSIONS -> MissionsTabContent(
                            summaries = missionSummaries,
                            onLaunchDebate = { topic ->
                                broker.launchDebate(topic)
                                selectedTab = MeshStudioTab.ROOMS
                            },
                            onLaunchDrafting = { subject ->
                                broker.launchChainedDrafting(subject)
                                selectedTab = MeshStudioTab.ROOMS
                            },
                            onLaunchSynthesis = { topic ->
                                broker.launchParallelSynthesis(topic)
                                selectedTab = MeshStudioTab.ROOMS
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomsTabContent(
    rooms: List<MeshRoom>,
    activeRoomId: String,
    messages: List<MeshChatMessage>,
    onSelectRoom: (String) -> Unit,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val activeRoom = rooms.firstOrNull { it.id == activeRoomId }

    Column(modifier = Modifier.fillMaxSize()) {
        // Room selector chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F131A))
                .padding(vertical = AgoraSpacing.Md),
            contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
        ) {
            items(rooms) { room ->
                val isSelected = room.id == activeRoomId
                Surface(
                    onClick = { onSelectRoom(room.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E242E),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = AgoraAlpha.Divider)
                    )
                ) {
                    Text(
                        text = room.name,
                        fontFamily = OutfitFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm)
                    )
                }
            }
        }

        // Room description
        activeRoom?.let { room ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131822))
                    .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm)
            ) {
                Text(
                    text = room.description,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = AgoraSpacing.Lg),
            contentPadding = PaddingValues(vertical = AgoraSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
        ) {
            items(messages) { msg ->
                MessageItemCard(msg)
            }
        }

        // Message input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10141C))
                .padding(AgoraSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "Injecter une directive au groupe...",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0xFF171E28),
                    unfocusedContainerColor = Color(0xFF171E28),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank()) Color(0xFF0284C7) else Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Envoyer",
                    tint = Color.White,
                    modifier = Modifier.size(AgoraSpacing.Xl)
                )
            }
        }
    }
}

@Composable
private fun MessageItemCard(msg: MeshChatMessage) {
    val isUser = msg.fromAgentId == "user-conductor"
    val isSystem = msg.fromAgentId == "switchboard"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isUser -> Color(0xFF1E293B)
                isSystem -> Color(0xFF0F172A)
                else -> Color(0xFF161E29)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isUser) Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Handle) else Color.White.copy(alpha = AgoraAlpha.Subtle)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.Md)) {
            // Header: Name + Honest Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(AgoraSpacing.Xl)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isUser -> Color(0xFF38BDF8)
                                    isSystem -> Color(0xFF64748B)
                                    else -> Color(0xFF818CF8)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = msg.fromAgentName.take(1),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                    Text(
                        text = msg.fromAgentName,
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                // Honest Status Badge (Délivré, Lu, Répondu)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Divider))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${msg.status.badge} ${msg.status.label}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when (msg.status) {
                                MeshMessageStatus.ANSWERED -> Color(0xFF34D399)
                                MeshMessageStatus.READ -> Color(0xFF38BDF8)
                                MeshMessageStatus.DELIVERED -> Color(0xFFFBBF24)
                                MeshMessageStatus.SENT -> Color(0xFF94A3B8)
                                MeshMessageStatus.EXPIRED -> Color(0xFFEF4444)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

            // Body
            Text(
                text = msg.content,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

            // Footer: SHA-256 fingerprint preview (Zero-plaintext proof)
            Text(
                text = "SHA-256: ${msg.fingerprint.take(16)}...",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun TeamTabContent(
    agents: List<MeshAgent>,
    onRevive: (String) -> Unit,
    onDirectMessage: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(AgoraSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
    ) {
        item {
            Text(
                text = "Équipe d'assistants connectés (${agents.size})",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                text = "Chaque cartouche dispose de son canal local direct avec statuts honnêtes.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
        }

        items(agents) { agent ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AgoraSpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(agent.colorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = agent.name.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(AgoraSpacing.Md))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = agent.name,
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                            Text(
                                text = "${agent.state.symbol} ${agent.state.label}",
                                fontSize = 11.sp,
                                color = when (agent.state) {
                                    MeshAgentState.WORKING -> Color(0xFFF87171)
                                    MeshAgentState.IDLE -> Color(0xFF94A3B8)
                                    MeshAgentState.STUCK -> Color(0xFFEF4444)
                                }
                            )
                        }

                        Text(
                            text = agent.role,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = AgoraAlpha.Hint)
                        )

                        agent.currentTask?.let { task ->
                            Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
                            Text(
                                text = "Tâche : $task",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
                        Text(
                            text = "${agent.completedTasksCount} missions achevées",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    if (agent.state == MeshAgentState.STUCK) {
                        Button(
                            onClick = { onRevive(agent.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs)
                        ) {
                            Text("Débloquer", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocksTabContent(
    locks: List<MeshFileLock>,
    onRelease: (String, String) -> Unit,
    onAcquire: (String, String) -> Unit
) {
    var newFilePath by remember { mutableStateOf("") }
    var newPurpose by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AgoraSpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Réservations de Fichiers (Anti-Collision)",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "Baux temporaires évitant qu'un agent n'écrase le travail d'un autre.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Button(
                onClick = { showDialog = true },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                contentPadding = PaddingValues(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AgoraSpacing.Lg))
                Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                Text("Réserver", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        if (locks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                    Text(
                        text = "Aucun fichier actuellement verrouillé.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Tous les documents du projet sont libres d'accès.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
            ) {
                items(locks) { lock ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141A24)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = AgoraAlpha.Handle)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AgoraSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(AgoraSpacing.Xxl)
                            )
                            Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lock.filePath,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Détenteur : ${lock.holderAgentName} · ${lock.purpose}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Expiration du bail dans : ${lock.remainingMinutes} min",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                            Button(
                                onClick = { onRelease(lock.filePath, lock.holderAgentId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs)
                            ) {
                                Text("Libérer", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Poser un verrou de fichier", color = Color.White) },
            containerColor = Color(0xFF141923),
            text = {
                Column {
                    OutlinedTextField(
                        value = newFilePath,
                        onValueChange = { newFilePath = it },
                        label = { Text("Chemin du fichier (ex: draft.md)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                    OutlinedTextField(
                        value = newPurpose,
                        onValueChange = { newPurpose = it },
                        label = { Text("Motif de réservation") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFilePath.isNotBlank()) {
                            onAcquire(newFilePath.trim(), newPurpose.trim().ifEmpty { "Édition exclusive" })
                            showDialog = false
                            newFilePath = ""
                            newPurpose = ""
                        }
                    }
                ) {
                    Text("Confirmer", color = Color(0xFF38BDF8))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Annuler", color = Color.White.copy(alpha = AgoraAlpha.Hint))
                }
            }
        )
    }
}

@Composable
private fun LedgerTabContent(entries: List<MeshLedgerEntry>) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AgoraSpacing.Lg)
    ) {
        Text(
            text = "Registre à Empreintes Uniquement (Audit Trail)",
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        Text(
            text = "Confidentialité absolue : Aucun texte n'est stocké, uniquement des signatures SHA-256 de traçabilité.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
        ) {
            items(entries) { entry ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10151E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(AgoraSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dateFormat.format(Date(entry.timestamp)),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                Text(
                                    text = "${entry.fromAgentId} ➔ ${entry.toTarget}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
                            Text(
                                text = "SHA-256: ${entry.sha256Fingerprint}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8).copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${entry.finalStatus.badge} ${entry.finalStatus.label}",
                                fontSize = 10.sp,
                                color = Color(0xFF34D399)
                            )
                            Text(
                                text = "${entry.transitDurationMs} ms",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionsTabContent(
    summaries: List<MeshMissionSummary>,
    onLaunchDebate: (String) -> Unit,
    onLaunchDrafting: (String) -> Unit,
    onLaunchSynthesis: (String) -> Unit
) {
    var debateTopic by remember { mutableStateOf("Faut-il migrer le moteur vers le cloud ou rester 100% local ?") }
    var draftingSubject by remember { mutableStateOf("Guide de survie de la vie privée sur mobile") }
    var synthesisSubject by remember { mutableStateOf("Rapport de prospective 2026 sur les agents autonomes") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(AgoraSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Lg)
    ) {
        item {
            Text(
                text = "Missions Multi-Agents (« Lance et continue »)",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                text = "Déclenchez des scénarios d'orchestration collective en un clic.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Mission 1: Débat Contradictoire
        item {
            MissionCard(
                title = "1. Débat Contradictoire (Avocat A vs Avocat B)",
                description = "Met en scène deux cartouches aux opinions opposées avec un arbitre pour forger une décision éclairée.",
                icon = Icons.Default.Gavel,
                accentColor = Color(0xFFF59E0B),
                actionLabel = "Lancer le débat",
                onLaunch = { onLaunchDebate(debateTopic) }
            )
        }

        // Mission 2: Rédaction Multi-étapes
        item {
            MissionCard(
                title = "2. Rédaction Multi-étapes en Chaîne",
                description = "Recherche documentaire (Bibliothécaire) ➔ Premier jet avec verrou (Rédacteur) ➔ Audit qualité (Auditeur).",
                icon = Icons.Default.EditNote,
                accentColor = Color(0xFF8B5CF6),
                actionLabel = "Lancer la chaîne",
                onLaunch = { onLaunchDrafting(draftingSubject) }
            )
        }

        // Mission 3: Synthèse Multi-sources
        item {
            MissionCard(
                title = "3. Synthèse Parallèle Multi-Sources",
                description = "Dispatch parallèle simultané vers 3 cartouches et bilan synthétique avec durée et honest status.",
                icon = Icons.Default.ScatterPlot,
                accentColor = Color(0xFF06B6D4),
                actionLabel = "Lancer le dispatch",
                onLaunch = { onLaunchSynthesis(synthesisSubject) }
            )
        }

        if (summaries.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(AgoraSpacing.Md))
                Text(
                    text = "Derniers Bilans Synthétiques de Groupe",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            items(summaries) { summary ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131822)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(AgoraSpacing.Md)) {
                        Text(
                            text = summary.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = summary.synthesisNotes,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    actionLabel: String,
    onLaunch: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131822)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = AgoraAlpha.Handle)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(AgoraSpacing.Xxxl)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(AgoraSpacing.Xl))
                }
                Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                Text(
                    text = title,
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(AgoraSpacing.Md))

            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm)
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
        }
    }
}
