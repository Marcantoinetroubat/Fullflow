package com.newoether.agora.ui.chat.live

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.AgoraApplication
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.data.local.BrainItemEntity
import com.newoether.agora.data.local.NoteEntity
import com.newoether.agora.data.local.ObsidianTaskEntity
import com.newoether.agora.data.notes.NoteRepository
import com.newoether.agora.data.notes.VaultImporter
import com.newoether.agora.service.BrainEmbeddingService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controller for showing/hiding the Second Brain screen.
 */
object SecondBrainController {
    var visible by mutableStateOf(false)
        private set

    fun open() { visible = true }
    fun close() { visible = false }
}

/**
 * Full-screen dialog showing the Second Brain vault (notes + brain items + tasks).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondBrainHost() {
    if (!SecondBrainController.visible) return

    val context = LocalContext.current

    Dialog(
        onDismissRequest = { SecondBrainController.close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F1216),
        ) {
            SecondBrainScreen(onBack = { SecondBrainController.close() }, context = context)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondBrainScreen(
    onBack: () -> Unit,
    context: Context,
) {
    val container = remember {
        runCatching {
            (context.applicationContext as? AgoraApplication)?.requireContainer()
        }.getOrNull()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Notes, 1: Tâches, 2: Ingérés
    var searchQuery by remember { mutableStateOf("") }

    var notes by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<ObsidianTaskEntity>>(emptyList()) }
    var brainItems by remember { mutableStateOf<List<BrainItemEntity>>(emptyList()) }

    var activeNoteToRead by remember { mutableStateOf<Pair<NoteEntity, String>?>(null) }
    var activeNoteToEdit by remember { mutableStateOf<Pair<NoteEntity, String>?>(null) }
    var editContentText by remember { mutableStateOf("") }

    var isIndexing by remember { mutableStateOf(false) }
    var indexCurrentItem by remember { mutableStateOf("") }
    var indexProgress by remember { mutableStateOf(0 to 0) }

    val scope = rememberCoroutineScope()

    val repo = remember(container) {
        container?.let { c ->
            NoteRepository(
                noteDao = c.database.noteDao(),
                taskDao = c.database.obsidianTaskDao(),
                embeddingDao = c.database.noteEmbeddingDao(),
                notesDir = File(context.filesDir, "notes"),
                generateEmbedding = { text ->
                    val generator = BrainEmbeddingService.getGenerator(context)
                    generator(text)
                }
            )
        }
    }

    // Load data flow
    fun refreshData() {
        scope.launch {
            repo?.let { r ->
                notes = if (searchQuery.isBlank()) r.listAllNotes() else r.searchNotes(searchQuery)
                tasks = r.getOpenTasksList()
                container?.database?.brainItemDao()?.getReadyItems()?.let { brainItems = it }
            }
        }
    }

    LaunchedEffect(searchQuery, selectedTab) {
        refreshData()
    }

    // Launchers for importers
    val singleNotePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && repo != null) {
            scope.launch {
                val success = VaultImporter.importSingleNote(context, uri, repo)
                if (success) {
                    android.widget.Toast.makeText(context, "Note importée !", android.widget.Toast.LENGTH_SHORT).show()
                    refreshData()
                }
            }
        }
    }

    val folderTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null && repo != null) {
            isIndexing = true
            scope.launch {
                val success = VaultImporter.importObsidianVault(context, uri, repo) { idx, total, name ->
                    indexCurrentItem = name
                    indexProgress = idx to total
                }
                isIndexing = false
                if (success) {
                    android.widget.Toast.makeText(context, "Dossier indexé avec succès !", android.widget.Toast.LENGTH_SHORT).show()
                    refreshData()
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Second Cerveau", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Text("Espace de connaissances et notes sémantiques", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1216)),
                actions = {
                    if (selectedTab == 0) {
                        // Semantic Reindex button
                        IconButton(
                            onClick = {
                                isIndexing = true
                                scope.launch {
                                    BrainEmbeddingService.reindexAll(context) { idx, total, title ->
                                        indexCurrentItem = title
                                        indexProgress = idx to total
                                    }
                                    isIndexing = false
                                    android.widget.Toast.makeText(context, "Réindexation sémantique terminée !", android.widget.Toast.LENGTH_SHORT).show()
                                    refreshData()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Réindexer", tint = Color(0xFF38BDF8))
                        }
                    }
                }
            )
        },
    ) { padding ->
        if (container == null || repo == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.height(AgoraSpacing.Md))
                Text("Initialisation du coffre fort...", color = Color.White.copy(alpha = AgoraAlpha.Hint))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Indexing progress display
            AnimatedVisibility(visible = isIndexing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2833)),
                    shape = RoundedCornerShape(8.dp)__,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AgoraSpacing.Lg)
                ) {
                    Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(AgoraSpacing.Lg), strokeWidth = 2.dp__, color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                            Text("Indexation en cours...", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                        Text(
                            text = "Fichier : $indexCurrentItem",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                        LinearProgressIndicator(
                            progress = { if (indexProgress.second > 0) indexProgress.first.toFloat() / indexProgress.second else 0f },
                            color = Color(0xFF38BDF8),
                            trackColor = Color.White.copy(alpha = AgoraAlpha.Subtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AgoraSpacing.Xs)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            // Tab bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF38BDF8),
                divider = {}
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Notes Markdown", fontSize = 13.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Tâches Obsidian", fontSize = 13.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Contenus Ingérés", fontSize = 13.sp, fontWeight = FontWeight.Bold) })
            }

            Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

            // Search Bar & Import Controls (only for Notes / Ingested)
            if (selectedTab == 0 || selectedTab == 2) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher dans votre cerveau...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF38BDF8)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)__,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = AgoraAlpha.Subtle),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = AgoraAlpha.Subtle),
                        unfocusedContainerColor = Color.White.copy(alpha = AgoraAlpha.Subtle)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm)
                )
            }

            // Quick actions for Notes Tab
            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                ) {
                    Button(
                        onClick = { singleNotePicker.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = AgoraAlpha.Subtle)),
                        shape = RoundedCornerShape(8.dp)__,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NoteAdd, null, tint = Color.White, modifier = Modifier.size(AgoraSpacing.Lg))
                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                        Text("Importer .md", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { folderTreePicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = AgoraAlpha.Subtle)),
                        shape = RoundedCornerShape(8.dp)__,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderZip, null, tint = Color.White, modifier = Modifier.size(AgoraSpacing.Lg))
                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                        Text("Dossier Suivi", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // List area based on active tab
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> {
                        // Notes List
                        if (notes.isEmpty()) {
                            EmptyBrainState("Aucune note dans le coffre.", "Utilisez le chat ou importez des fichiers Markdown pour enrichir votre mémoire sémantique.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(notes) { note ->
                                    NoteRowCard(
                                        note = note,
                                        onRead = {
                                            scope.launch {
                                                repo.getNote(note.filePath)?.let { activeNoteToRead = it }
                                            }
                                        },
                                        onEdit = {
                                            scope.launch {
                                                repo.getNote(note.filePath)?.let {
                                                    activeNoteToEdit = it
                                                    editContentText = it.second
                                                }
                                            }
                                        },
                                        onPinToggle = {
                                            scope.launch {
                                                repo.togglePinned(note.filePath)
                                                refreshData()
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                repo.deleteNote(note.filePath)
                                                refreshData()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Obsidian Tasks List
                        if (tasks.isEmpty()) {
                            EmptyBrainState("Aucune tâche ouverte.", "Toutes les tâches - [ ] définies dans vos fichiers Markdown d'Obsidian sont résolues ou absentes.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(tasks) { task ->
                                    ObsidianTaskRowCard(
                                        task = task,
                                        onCheckedChange = {
                                            scope.launch {
                                                repo.toggleTask(task)
                                                refreshData()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Ingested Items List
                        val filteredItems = if (searchQuery.isBlank()) brainItems else brainItems.filter {
                            it.title.contains(searchQuery, ignoreCase = true) || it.sourceUrl.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredItems.isEmpty()) {
                            EmptyBrainState("Aucun contenu ingéré.", "Importez des fichiers PDF ou sauvegardez des pages web pour les indexer sémantiquement.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredItems) { item ->
                                    BrainItemRowCard(
                                        item = item,
                                        onDelete = {
                                            scope.launch {
                                                container.database.brainItemDao().deleteItem(item.id)
                                                refreshData()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Reading a Note
    activeNoteToRead?.let { pair ->
        AlertDialog(
            onDismissRequest = { activeNoteToRead = null },
            title = { Text(pair.first.title, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(pair.second, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { activeNoteToRead = null }) {
                    Text("Fermer", color = Color(0xFF38BDF8))
                }
            },
            shape = RoundedCornerShape(8.dp)__,
            containerColor = Color(0xFF141A21)
        )
    }

    // Dialog for Editing a Note
    activeNoteToEdit?.let { pair ->
        AlertDialog(
            onDismissRequest = { activeNoteToEdit = null },
            title = { Text("Modifier : ${pair.first.title}", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = editContentText,
                    onValueChange = { editContentText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = AgoraAlpha.Divider)
                    ),
                    shape = RoundedCornerShape(8.dp)__
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repo?.saveNote(pair.first.filePath, editContentText)
                            activeNoteToEdit = null
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Text("Sauvegarder", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeNoteToEdit = null }) {
                    Text("Annuler", color = Color.White.copy(alpha = AgoraAlpha.Hint))
                }
            },
            shape = RoundedCornerShape(8.dp)__,
            containerColor = Color(0xFF141A21)
        )
    }
}

@Composable
private fun NoteRowCard(
    note: NoteEntity,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp)__,
        color = Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Subtle)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPinToggle, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = "Pin",
                    tint = if (note.isPinned) Color(0xFF38BDF8) else Color.White.copy(alpha = AgoraAlpha.Hint),
                    modifier = Modifier.size(AgoraSpacing.Xxl)
                )
            }

            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))

            Column(
                modifier = Modifier.weight(1f).clickable(
                    role = Role.Button,
                    onClickLabel = "Lire la note",
                    onClick = onRead,
                )
            ) {
                Text(
                    text = note.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.tags.isNotBlank()) {
                    Text(
                        text = note.tags.split(",").joinToString(" ") { "#$it" },
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = note.filePath,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))

            Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Xs)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Edit, "Modifier", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(AgoraSpacing.Xxl))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color(0xFFEF5350), modifier = Modifier.size(AgoraSpacing.Xxl))
                }
            }
        }
    }
}

@Composable
private fun ObsidianTaskRowCard(
    task: ObsidianTaskEntity,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp)__,
        color = Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Subtle)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF38BDF8),
                    uncheckedColor = Color.White.copy(alpha = 0.4f),
                    checkmarkColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (task.isChecked) Color.White.copy(alpha = 0.4f) else Color.White,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = Color.White.copy(alpha = AgoraAlpha.Hint), modifier = Modifier.size(AgoraSpacing.Md))
                    Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                    Text(
                        text = task.noteFilePath.substringAfterLast("/"),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = AgoraAlpha.Hint)
                    )
                    
                    if (task.priority != null && task.priority != "NONE") {
                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Divider),
                            shape = RoundedCornerShape(8.dp)__
                        ) {
                            Text(
                                text = "Prio: ${task.priority}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = AgoraSpacing.Xs, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrainItemRowCard(
    item: BrainItemEntity,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp)__,
        color = Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Subtle)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp)__)
                    .background(Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Subtle))
            ) {
                Icon(
                    imageVector = if (item.type == "PDF") Icons.Default.Description else Icons.Default.Language,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(AgoraSpacing.Xl)
                )
            }

            Spacer(modifier = Modifier.width(AgoraSpacing.Md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.type} · ${item.sourceUrl.take(45)}...",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(AgoraSpacing.Xxl)
                )
            }
        }
    }
}

@Composable
private fun EmptyBrainState(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AgoraSpacing.Xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Hub,
            contentDescription = null,
            tint = Color.White.copy(alpha = AgoraAlpha.Divider),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = AgoraAlpha.Hint),
            textAlign = TextAlign.Center
        )
        Text(
            text = desc,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = AgoraAlpha.Hint),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AgoraSpacing.Xs)
        )
    }
}
