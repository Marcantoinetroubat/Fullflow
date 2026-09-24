package com.newoether.agora.studio.video

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.ui.chat.VideoPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiVideoStudioScreen(
    onClose: () -> Unit,
    onInsertToChat: (GeneratedStudioVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedModel by GeminiVideoStudioController.selectedModel.collectAsState()
    val useSettingsModel by GeminiVideoStudioController.useSettingsModel.collectAsState()
    val customModelId by GeminiVideoStudioController.customModelId.collectAsState()

    val selectedRatio by GeminiVideoStudioController.selectedAspectRatio.collectAsState()
    val selectedDuration by GeminiVideoStudioController.selectedDuration.collectAsState()
    val isGenerating by GeminiVideoStudioController.isGenerating.collectAsState()
    val statusMessage by GeminiVideoStudioController.statusMessage.collectAsState()
    val errorMessage by GeminiVideoStudioController.errorMessage.collectAsState()
    val generatedVideos by GeminiVideoStudioController.generatedVideos.collectAsState()
    val activePlayingVideo by GeminiVideoStudioController.activePlayingVideo.collectAsState()

    val app = context.applicationContext as? com.newoether.agora.AgoraApplication
    val container = app?.requireContainer()
    val settingsRepo = container?.settingsRepository
    val settingsVideoModel by (settingsRepo?.videoGenModel ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val modelAliases by (settingsRepo?.modelAliases ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
    val customProviders by (settingsRepo?.customProviders ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()

    val activeModelDisplayName = remember(selectedModel, useSettingsModel, customModelId, settingsVideoModel, modelAliases, customProviders) {
        if (useSettingsModel) {
            if (customModelId != null) {
                com.newoether.agora.data.modelAliasDisplayName(customModelId!!, modelAliases, customProviders)
            } else if (!settingsVideoModel.isNullOrBlank()) {
                com.newoether.agora.data.modelAliasDisplayName(settingsVideoModel!!, modelAliases, customProviders)
            } else {
                "Modèle global"
            }
        } else {
            selectedModel.displayName
        }
    }

    var promptText by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        GeminiVideoStudioController.autoSelectBestModel(context)
        GeminiVideoStudioController.resumePending(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.newoether.agora.studio.common.StudioTokens.ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            StudioVideoTopBar(
                activeModelDisplayName = activeModelDisplayName,
                onBackClick = onClose,
                onModelClick = { showModelSheet = true },
                onNewClick = {
                    promptText = ""
                    GeminiVideoStudioController.clearError()
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    StudioVideoHeroHeader(
                        modelName = activeModelDisplayName,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                if (errorMessage != null) {
                    item {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = Color(0xFFFEE2E2),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { GeminiVideoStudioController.clearError() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fermer",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFA78BFA),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = statusMessage.ifBlank { "Génération de la vidéo en cours..." },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (generatedVideos.isNotEmpty()) {
                    item {
                        Text(
                            text = "Vidéos générées",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    item {
                        StudioVideoResultsRow(
                            videos = generatedVideos,
                            onPlay = { GeminiVideoStudioController.playVideo(it) },
                            onSave = { video ->
                                coroutineScope.launch {
                                    val ok = GeminiVideoStudioController.saveToDeviceGallery(context, video)
                                    val msg = if (ok) "Vidéo enregistrée dans Galerie/Movies" else "Échec de l'enregistrement"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = { GeminiVideoStudioController.shareVideo(context, it) },
                            onInsertChat = { onInsertToChat(it) }
                        )
                    }
                }

                // Preset 1: Large Hero card (Monde miniature)
                item {
                    val heroPreset = StudioVideoPresetTemplate.PRESETS[0]
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        StudioVideoHeroPresetCard(
                            preset = heroPreset,
                            onClick = { promptText = heroPreset.prompt }
                        )
                    }
                }

                // Presets 2 & 3: Row of 2 (Anime & Aventure 8 bits)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val animePreset = StudioVideoPresetTemplate.PRESETS[1]
                        StudioVideoSubPresetCard(
                            preset = animePreset,
                            onClick = { promptText = animePreset.prompt },
                            modifier = Modifier.weight(1f)
                        )

                        val retroPreset = StudioVideoPresetTemplate.PRESETS[2]
                        StudioVideoSubPresetCard(
                            preset = retroPreset,
                            onClick = { promptText = retroPreset.prompt },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Floating Video Composer (matches Screenshot 2)
        StudioVideoFloatingComposer(
            promptText = promptText,
            onPromptChange = { promptText = it },
            isGenerating = isGenerating,
            onGenerate = {
                GeminiVideoStudioController.generateVideo(
                    context = context,
                    prompt = promptText,
                    onSuccess = { promptText = "" }
                )
            },
            selectedRatio = selectedRatio,
            onRatioChange = { GeminiVideoStudioController.setAspectRatio(it) },
            selectedDuration = selectedDuration,
            onDurationChange = { GeminiVideoStudioController.setDuration(it) },
            activeModelDisplayName = activeModelDisplayName,
            onOpenModelPicker = { showModelSheet = true },
            onVoiceInput = {
                Toast.makeText(context, "Écoutez... Décrivez votre vidéo", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Model Picker Bottom Sheet
        if (showModelSheet) {
            StudioVideoModelPickerSheet(
                currentModel = selectedModel,
                useSettingsModel = useSettingsModel,
                customModelId = customModelId,
                onSelectModel = { model ->
                    GeminiVideoStudioController.setModel(model)
                },
                onSelectSettingsModel = {
                    GeminiVideoStudioController.setUseSettingsModel(true)
                },
                onSelectCustomModel = { modelId ->
                    GeminiVideoStudioController.setCustomModelId(modelId)
                },
                onDismiss = { showModelSheet = false }
            )
        }

        // In-app Video Player playback
        activePlayingVideo?.let { playingVideo ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayer(
                    uri = playingVideo.uri,
                    onClose = { GeminiVideoStudioController.closeVideoPlayer() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
