package com.newoether.agora.studio.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.studio.reader.db.ReaderDatabase
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.studio.reader.db.ReaderDocumentEntity
import com.newoether.agora.ui.chat.audio.FullFlowTtsEngine
import com.newoether.agora.ui.chat.audio.PersonalPodcasterTab
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastAndReaderScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ttsEngine = remember { FullFlowTtsEngine(context) }
    val db = remember { ReaderDatabase.getInstance(context) }
    val documents by db.readerDao().getAllDocumentsFlow().collectAsState(initial = emptyList())

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Documents, 1: Podcaster
    var activeDocument by remember { mutableStateOf<ReaderDocumentEntity?>(null) }
    var activeDocumentText by remember { mutableStateOf("") }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var currentChunkIndex by remember { mutableStateOf(0) }
    var textChunks by remember { mutableStateOf<List<String>>(emptyList()) }
    var playSpeed by remember { mutableFloatStateOf(1.0f) }
    var sleepTimerRemainingMinutes by remember { mutableStateOf<Int?>(null) }

    var isImporting by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val success = importDocumentToReader(context, uri, db)
                isImporting = false
            }
        }
    }

    // Handles playing active document text sequentially
    LaunchedEffect(isTtsPlaying, currentChunkIndex, textChunks) {
        if (isTtsPlaying && textChunks.isNotEmpty() && currentChunkIndex in textChunks.indices) {
            val chunk = textChunks[currentChunkIndex]
            
            // Speak chunk and trigger next when completed
            ttsEngine.speak(chunk) {
                scope.launch {
                    if (currentChunkIndex + 1 < textChunks.size) {
                        currentChunkIndex++
                        // Update progress in database
                        activeDocument?.let { doc ->
                            val progressPercent = (currentChunkIndex.toFloat() / textChunks.size).coerceIn(0f, 1f)
                            db.readerDao().updateProgress(doc.id, currentChunkIndex, progressPercent)
                        }
                    } else {
                        isTtsPlaying = false
                        currentChunkIndex = 0
                    }
                }
            }
        } else if (!isTtsPlaying) {
            ttsEngine.stop()
        }
    }

    // Sleep Timer countdown simulation
    LaunchedEffect(sleepTimerRemainingMinutes) {
        sleepTimerRemainingMinutes?.let { minutes ->
            if (minutes > 0) {
                kotlinx.coroutines.delay(60000)
                sleepTimerRemainingMinutes = minutes - 1
            } else {
                isTtsPlaying = false
                sleepTimerRemainingMinutes = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1216),
                        Color(0xFF0B0D10),
                        Color(0xFF060709)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Shared Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(AgoraSpacing.Xxl)
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                Column {
                    Text(
                        text = "Podcast & Documents",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Écoute de documents ElevenReader style & création de podcasts",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Tab switcher
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF38BDF8),
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Bibliothèque", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Podcaster", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(AgoraSpacing.Md))

            // Main Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> {
                        // Library Tab
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top controls & Import Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vos documents (${documents.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.7f)
                                )

                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    if (isImporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(AgoraSpacing.Lg),
                                            color = Color.Black,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.UploadFile,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(AgoraSpacing.Lg)
                                            )
                                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                            Text("Importer", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (documents.isEmpty()) {
                                // Library is empty
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(AgoraSpacing.Xxxl),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryBooks,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = AgoraAlpha.Divider),
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Spacer(modifier = Modifier.height(AgoraSpacing.Lg))
                                    Text(
                                        text = "Aucun document importé",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Importez un PDF, fichier Texte (.txt) ou Markdown (.md) pour l'écouter.",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.25f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = AgoraSpacing.Xs)
                                    )
                                }
                            } else {
                                // Documents list
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                                    verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
                                ) {
                                    items(documents) { doc ->
                                        DocumentRowCard(
                                            doc = doc,
                                            isActive = activeDocument?.id == doc.id,
                                            isPlaying = isTtsPlaying && activeDocument?.id == doc.id,
                                            onPlayToggle = {
                                                scope.launch {
                                                    if (activeDocument?.id == doc.id) {
                                                        isTtsPlaying = !isTtsPlaying
                                                    } else {
                                                        // Stop previous
                                                        isTtsPlaying = false
                                                        ttsEngine.stop()
                                                        
                                                        // Load document text
                                                        val text = withContext(Dispatchers.IO) {
                                                            File(doc.filePath).readText(Charsets.UTF_8)
                                                        }
                                                        
                                                        activeDocument = doc
                                                        activeDocumentText = text
                                                        // Simple split by sentence-ending characters to get clean spoken sentences
                                                        textChunks = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                                                        currentChunkIndex = doc.lastReadPosition.coerceIn(0, textChunks.size - 1)
                                                        isTtsPlaying = true
                                                    }
                                                }
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    if (activeDocument?.id == doc.id) {
                                                        isTtsPlaying = false
                                                        ttsEngine.stop()
                                                        activeDocument = null
                                                        textChunks = emptyList()
                                                    }
                                                    File(doc.filePath).delete()
                                                    db.readerDao().deleteDocument(doc)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Persistent document playback controller bar
                            AnimatedVisibility(
                                visible = activeDocument != null,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                activeDocument?.let { doc ->
                                    PlaybackControllerBar(
                                        title = doc.title,
                                        isPlaying = isTtsPlaying,
                                        progress = if (textChunks.isNotEmpty()) currentChunkIndex.toFloat() / textChunks.size else 0f,
                                        speed = playSpeed,
                                        sleepTimerMinutes = sleepTimerRemainingMinutes,
                                        onPlayToggle = { isTtsPlaying = !isTtsPlaying },
                                        onForward = {
                                            if (currentChunkIndex + 1 < textChunks.size) {
                                                currentChunkIndex++
                                            }
                                        },
                                        onRewind = {
                                            if (currentChunkIndex > 0) {
                                                currentChunkIndex--
                                            }
                                        },
                                        onSpeedChange = {
                                            playSpeed = when (playSpeed) {
                                                1.0f -> 1.25f
                                                1.25f -> 1.5f
                                                1.5f -> 1.75f
                                                1.75f -> 2.0f
                                                else -> 1.0f
                                            }
                                             ttsEngine.updatePlaybackSpeed(playSpeed)
                                        },
                                        onSleepTimerToggle = {
                                            sleepTimerRemainingMinutes = when (sleepTimerRemainingMinutes) {
                                                null -> 15
                                                15 -> 30
                                                30 -> 45
                                                45 -> 60
                                                else -> null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Podcaster Tab (natively embedded!)
                        PersonalPodcasterTab(
                            ttsEngine = ttsEngine,
                            onInsertIntoChat = { script ->
                                // Optional callback when script is exported
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentRowCard(
    doc: ReaderDocumentEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Subtle) else Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(
            1.dp,
            if (isActive) Color(0xFF38BDF8).copy(alpha = 0.25f) else Color.White.copy(alpha = AgoraAlpha.Subtle)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Type Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (doc.type) {
                            "pdf" -> Color(0xFFEF5350).copy(alpha = AgoraAlpha.Divider)
                            else -> Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Divider)
                        }
                    )
            ) {
                Icon(
                    imageVector = when (doc.type) {
                        "pdf" -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = when (doc.type) {
                        "pdf" -> Color(0xFFEF5350)
                        else -> Color(0xFF38BDF8)
                    },
                    modifier = Modifier.size(AgoraSpacing.Xl)
                )
            }

            Spacer(modifier = Modifier.width(AgoraSpacing.Md))

            // Title & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.type.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = AgoraAlpha.Hint)
                    )
                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                    Text(
                        text = "${doc.sizeBytes / 1024} KB",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = AgoraAlpha.Hint)
                    )
                }

                // Progress Indicator
                if (doc.lastReadPercent > 0f) {
                    Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                    LinearProgressIndicator(
                        progress = { doc.lastReadPercent },
                        color = Color(0xFF38BDF8),
                        trackColor = Color.White.copy(alpha = AgoraAlpha.Subtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))

            // Play / Pause Button
            IconButton(
                onClick = onPlayToggle,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = AgoraAlpha.Subtle))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    tint = if (isActive) Color(0xFF38BDF8) else Color.White,
                    modifier = Modifier.size(AgoraSpacing.Xl)
                )
            }

            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = Color.White.copy(alpha = AgoraAlpha.Hint),
                    modifier = Modifier.size(AgoraSpacing.Xl)
                )
            }
        }
    }
}

@Composable
private fun PlaybackControllerBar(
    title: String,
    isPlaying: Boolean,
    progress: Float,
    speed: Float,
    sleepTimerMinutes: Int?,
    onPlayToggle: () -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onSpeedChange: () -> Unit,
    onSleepTimerToggle: () -> Unit
) {
    Surface(
        color = Color(0xFF141A21),
        border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)) {
            // Header: Title & Timer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(AgoraSpacing.Lg)
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Sleep Timer Display
                Text(
                    text = sleepTimerMinutes?.let { "Timer: $it min" } ?: "Timer off",
                    fontSize = 10.sp,
                    color = if (sleepTimerMinutes != null) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .clickable { onSleepTimerToggle() }
                        .padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xxs)
                )
            }

            Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                color = Color(0xFF38BDF8),
                trackColor = Color.White.copy(alpha = AgoraAlpha.Subtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AgoraSpacing.Xs)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(AgoraSpacing.Md))

            // Controllers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Speed multiplier button
                TextButton(
                    onClick = onSpeedChange,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "${speed}x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }

                // Skip / Control Buttons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRewind) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Précédent", tint = Color.White)
                    }

                    IconButton(
                        onClick = onPlayToggle,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Lecture/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(AgoraSpacing.Xxl)
                        )
                    }

                    IconButton(onClick = onForward) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Suivant", tint = Color.White)
                    }
                }

                // Extra placeholder so layout remains symmetrical
                Spacer(modifier = Modifier.width(36.dp))
            }
        }
    }
}

/**
 * Imports files on-device cleanly and inserts metadata into SQLite database.
 */
private suspend fun importDocumentToReader(
    context: Context,
    uri: Uri,
    database: ReaderDatabase
): Boolean = withContext(Dispatchers.IO) {
    try {
        val cr = context.contentResolver
        var fileName = "Document_Importé"
        var fileSize = 0L

        cr.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
            }
        }

        val type = when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "pdf"
            fileName.endsWith(".txt", ignoreCase = true) -> "txt"
            fileName.endsWith(".md", ignoreCase = true) -> "md"
            else -> "txt"
        }

        // Copy content stream locally
        val targetDir = File(context.filesDir, "reader_docs").also { it.mkdirs() }
        val targetFile = File(targetDir, "doc_${System.currentTimeMillis()}.$type")

        cr.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext false

        // Extract text
        val textContent = if (type == "pdf") {
            PdfExtractor.extractTextFromPdf(context, targetFile)
        } else {
            targetFile.readText(Charsets.UTF_8)
        }

        // Save stripped clean plain-text version (overwrites targetFile so we read standard plain-text during TTS streaming)
        targetFile.writeText(textContent, Charsets.UTF_8)

        val docEntity = ReaderDocumentEntity(
            title = fileName.removeSuffix(".pdf").removeSuffix(".txt").removeSuffix(".md"),
            filePath = targetFile.absolutePath,
            type = type,
            sizeBytes = targetFile.length(),
            dateAdded = System.currentTimeMillis(),
            textLength = textContent.length
        )

        database.readerDao().insertDocument(docEntity)
        true
    } catch (e: Exception) {
        false
    }
}
