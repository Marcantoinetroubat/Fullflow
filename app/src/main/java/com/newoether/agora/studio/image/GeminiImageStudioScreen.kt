package com.newoether.agora.studio.image

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.theme.OutfitFamily
import kotlinx.coroutines.launch

@Composable
fun GeminiImageStudioScreen(
    onClose: () -> Unit,
    onInsertToChat: (GeneratedStudioImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        GeminiImageStudioController.init(context)
    }

    val selectedModel by GeminiImageStudioController.selectedModel.collectAsState()
    val useSettingsModel by GeminiImageStudioController.useSettingsModel.collectAsState()
    val customModelId by GeminiImageStudioController.customModelId.collectAsState()

    val app = context.applicationContext as? com.newoether.agora.AgoraApplication
    val container = app?.requireContainer()
    val settingsRepo = container?.settingsRepository
    val settingsModel by (settingsRepo?.imageGenModel ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()

    val activeModelDisplayName = if (useSettingsModel) {
        if (customModelId != null) {
            val customProviders = settingsRepo?.customProviders?.value ?: emptyList()
            val modelAliases = settingsRepo?.modelAliases?.value ?: emptyMap()
            com.newoether.agora.data.modelAliasDisplayName(customModelId!!, modelAliases, customProviders)
        } else if (!settingsModel.isNullOrBlank()) {
            val customProviders = settingsRepo?.customProviders?.value ?: emptyList()
            val modelAliases = settingsRepo?.modelAliases?.value ?: emptyMap()
            com.newoether.agora.data.modelAliasDisplayName(settingsModel!!, modelAliases, customProviders)
        } else {
            "Modèle global"
        }
    } else {
        selectedModel.displayName
    }

    LaunchedEffect(Unit) {
        GeminiImageStudioController.autoSelectBestModel(context)
    }

    val selectedRatio by GeminiImageStudioController.selectedAspectRatio.collectAsState()
    val selectedResolution by GeminiImageStudioController.selectedResolution.collectAsState()
    val burstCount by GeminiImageStudioController.burstCount.collectAsState()
    val attachedBitmap by GeminiImageStudioController.attachedBitmapForEdit.collectAsState()
    val isGenerating by GeminiImageStudioController.isGenerating.collectAsState()
    val statusMessage by GeminiImageStudioController.statusMessage.collectAsState()
    val errorMessage by GeminiImageStudioController.errorMessage.collectAsState()
    val generatedImages by GeminiImageStudioController.generatedImages.collectAsState()
    val activeZoomImage by GeminiImageStudioController.activeZoomImage.collectAsState()

    val cachedRoomImages by GeminiImageStudioController.cachedRoomImages.collectAsState()
    val savedPrompts by GeminiImageStudioController.savedPrompts.collectAsState()
    val isSelectionMode by GeminiImageStudioController.isSelectionMode.collectAsState()
    val selectedImageIds by GeminiImageStudioController.selectedImageIds.collectAsState()
    val isBatchDownloading by GeminiImageStudioController.isBatchDownloading.collectAsState()

    var currentTab by remember { mutableStateOf(StudioTab.STUDIO) }
    var promptText by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showConfigRow by remember { mutableStateOf(false) }
    var showSavedPromptsSheet by remember { mutableStateOf(false) }
    var showSavePromptDialog by remember { mutableStateOf(false) }
    var promptToSave by remember { mutableStateOf("") }

    val galleryImages = remember(cachedRoomImages) {
        cachedRoomImages.map { it.toGeneratedStudioImage() }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            GeminiImageStudioController.attachUriForEdit(context, uri)
        }
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
            StudioTopBar(
                selectedModel = selectedModel,
                onBackClick = onClose,
                onModelClick = { showModelSheet = true },
                onNewClick = {
                    promptText = ""
                    GeminiImageStudioController.clearAttachedImage()
                    GeminiImageStudioController.clearError()
                },
                modelDisplayNameOverride = activeModelDisplayName,
            )

            StudioTabsHeader(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                    if (tab != StudioTab.GALLERY && isSelectionMode) {
                        GeminiImageStudioController.setSelectionMode(false)
                    }
                },
                cachedCount = galleryImages.size,
                savedPromptsCount = savedPrompts.size,
                onOpenSavedPrompts = { showSavedPromptsSheet = true }
            )

            AnimatedVisibility(
                visible = currentTab == StudioTab.GALLERY && isSelectionMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                StudioSelectionHeader(
                    selectedCount = selectedImageIds.size,
                    totalCount = galleryImages.size,
                    onExitSelection = { GeminiImageStudioController.setSelectionMode(false) },
                    onSelectAll = { GeminiImageStudioController.selectAll(galleryImages.map { it.id }) },
                    onDeselectAll = { GeminiImageStudioController.clearSelection() },
                    onDownloadSelected = {
                        coroutineScope.launch {
                            val count = GeminiImageStudioController.batchDownloadSelected(context, galleryImages)
                            val msg = context.getString(R.string.image_studio_batch_download_success, count)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteSelected = {
                        coroutineScope.launch {
                            val count = selectedImageIds.size
                            GeminiImageStudioController.deleteSelectedImages(context)
                            Toast.makeText(context, context.getString(R.string.image_studio_delete_success, count), Toast.LENGTH_SHORT).show()
                        }
                    },
                    isDownloading = isBatchDownloading
                )
            }

            if (currentTab == StudioTab.STUDIO) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    item {
                        StudioHeroHeader(
                            modelName = activeModelDisplayName,
                            modifier = Modifier.padding(horizontal = AgoraSpacing.Xxl, vertical = AgoraSpacing.Lg)
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
                                    .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Sm)
                            ) {
                                Row(
                                    modifier = Modifier.padding(AgoraSpacing.Lg),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(AgoraSpacing.Xl)
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                                    Text(
                                        text = errorMessage.orEmpty(),
                                        color = Color(0xFFFEE2E2),
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { GeminiImageStudioController.clearError() },
                                        modifier = Modifier.size(AgoraSpacing.Xxl)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fermer",
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(AgoraSpacing.Lg)
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
                                    .padding(AgoraSpacing.Xxl),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFFFD54F),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = statusMessage.ifBlank { stringResource(R.string.image_studio_generating) },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    if (generatedImages.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Résultats Récents",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                TextButton(onClick = { currentTab = StudioTab.GALLERY }) {
                                    Text(
                                        text = "Voir dans la galerie →",
                                        fontSize = 12.sp,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }

                        item {
                            StudioResultsRow(
                                images = generatedImages,
                                onZoom = { GeminiImageStudioController.openZoomImage(it) },
                                onRetouch = { image ->
                                    GeminiImageStudioController.prepareRetouchFromGenerated(image)
                                    promptText = "Retoucher: "
                                },
                                onSave = { image ->
                                    coroutineScope.launch {
                                        val ok = GeminiImageStudioController.saveToDeviceGallery(context, image)
                                        val msg = if (ok) context.getString(R.string.image_studio_saved_success) else "Erreur d'enregistrement"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShare = { GeminiImageStudioController.shareImage(context, it) },
                                onInsertChat = { onInsertToChat(it) }
                            )
                        }
                    }

                    if (savedPrompts.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = AgoraSpacing.Xl, end = AgoraSpacing.Xl, top = AgoraSpacing.Lg, bottom = AgoraSpacing.Sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(AgoraSpacing.Xl)
                                    )
                                    Text(
                                        text = "Styles favoris enregistrés",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                TextButton(onClick = { showSavedPromptsSheet = true }) {
                                    Text("Gérer", fontSize = 12.sp, color = Color(0xFF93C5FD))
                                }
                            }
                        }

                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg),
                                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                            ) {
                                items(savedPrompts) { promptItem ->
                                    Surface(
                                        onClick = {
                                            promptText = promptItem.prompt
                                            Toast.makeText(context, "Style appliqué", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF141D2B),
                                        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                                        ) {
                                            Text(
                                                text = promptItem.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFFD54F).copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = promptItem.styleCategory,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFFD54F),
                                                    modifier = Modifier.padding(horizontal = AgoraSpacing.Xs, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val presetTrios = StudioPresetTemplate.PRESETS.chunked(3)
                    items(presetTrios) { trio ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Xs),
                            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                        ) {
                            trio.forEach { preset ->
                                StudioPresetCard(
                                    preset = preset,
                                    onClick = {
                                        promptText = preset.prompt
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - trio.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // GALLERY TAB (Room Database cache)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (!isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Historique en local (Room)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${galleryImages.size} image(s) enregistrée(s)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            if (galleryImages.isNotEmpty()) {
                                Button(
                                    onClick = { GeminiImageStudioController.setSelectionMode(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(AgoraSpacing.Lg)
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                                    Text(
                                        text = stringResource(R.string.image_studio_select_mode),
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    StudioGalleryGrid(
                        images = galleryImages,
                        isSelectionMode = isSelectionMode,
                        selectedIds = selectedImageIds,
                        onToggleSelect = { GeminiImageStudioController.toggleSelectImage(it) },
                        onZoomImage = { GeminiImageStudioController.openZoomImage(it) },
                        onEnterSelectionMode = { imageId ->
                            GeminiImageStudioController.setSelectionMode(true)
                            GeminiImageStudioController.toggleSelectImage(imageId)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Floating Composer (visible in STUDIO tab, or as quick button in GALLERY tab)
        if (currentTab == StudioTab.STUDIO) {
            StudioFloatingComposer(
                promptText = promptText,
                onPromptChange = { promptText = it },
                attachedBitmap = attachedBitmap,
                onRemoveAttached = { GeminiImageStudioController.clearAttachedImage() },
                onPickImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                isGenerating = isGenerating,
                onGenerate = {
                    GeminiImageStudioController.generateOrEdit(
                        context = context,
                        prompt = promptText,
                        onSuccess = { promptText = "" }
                    )
                },
                selectedRatio = selectedRatio,
                onRatioChange = { GeminiImageStudioController.setAspectRatio(it) },
                selectedResolution = selectedResolution,
                onResolutionChange = { GeminiImageStudioController.setResolution(it) },
                burstCount = burstCount,
                onBurstCountChange = { GeminiImageStudioController.setBurstCount(it) },
                showConfigRow = showConfigRow,
                onToggleConfigRow = { showConfigRow = !showConfigRow },
                activeModelDisplayName = activeModelDisplayName,
                onOpenModelPicker = { showModelSheet = true },
                onSavePromptClick = {
                    promptToSave = promptText
                    showSavePromptDialog = true
                },
                onVoiceInput = {
                    Toast.makeText(context, "Écoutez... Décrivez votre image", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)
            )
        } else if (!isSelectionMode) {
            // Quick action button to create from gallery
            FloatingActionButton(
                onClick = { currentTab = StudioTab.STUDIO },
                containerColor = Color(0xFFFFD54F),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(AgoraSpacing.Xl)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AgoraSpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text("Créer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        activeZoomImage?.let { zoomImg ->
            StudioZoomDialog(
                image = zoomImg,
                onDismiss = { GeminiImageStudioController.closeZoomImage() },
                onRetouch = {
                    GeminiImageStudioController.prepareRetouchFromGenerated(zoomImg)
                    currentTab = StudioTab.STUDIO
                    promptText = "Retoucher: "
                },
                onSave = {
                    coroutineScope.launch {
                        val ok = GeminiImageStudioController.saveToDeviceGallery(context, zoomImg)
                        val msg = if (ok) context.getString(R.string.image_studio_saved_success) else "Erreur d'enregistrement"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = { GeminiImageStudioController.shareImage(context, zoomImg) },
                onInsertChat = {
                    GeminiImageStudioController.closeZoomImage()
                    onInsertToChat(zoomImg)
                }
            )
        }

        if (showModelSheet) {
            StudioModelPickerSheet(
                currentModel = selectedModel,
                useSettingsModel = useSettingsModel,
                customModelId = customModelId,
                onSelectModel = {
                    GeminiImageStudioController.setModel(it)
                    showModelSheet = false
                },
                onSelectSettingsModel = {
                    GeminiImageStudioController.setUseSettingsModel(true)
                    showModelSheet = false
                },
                onSelectCustomModel = {
                    GeminiImageStudioController.setCustomModelId(it)
                    showModelSheet = false
                },
                onDismiss = { showModelSheet = false }
            )
        }

        if (showSavedPromptsSheet) {
            StudioSavedPromptsSheet(
                savedPrompts = savedPrompts,
                onSelectPrompt = { item ->
                    promptText = item.prompt
                    currentTab = StudioTab.STUDIO
                    showSavedPromptsSheet = false
                    Toast.makeText(context, "Style appliqué !", Toast.LENGTH_SHORT).show()
                },
                onDeletePrompt = { item ->
                    coroutineScope.launch {
                        GeminiImageStudioController.deleteSavedPrompt(context, item.id)
                        Toast.makeText(context, context.getString(R.string.image_studio_saved_prompt_deleted), Toast.LENGTH_SHORT).show()
                    }
                },
                onCreateNewPrompt = {
                    promptToSave = promptText
                    showSavePromptDialog = true
                },
                onDismiss = { showSavedPromptsSheet = false }
            )
        }

        if (showSavePromptDialog) {
            StudioSavePromptDialog(
                initialPrompt = promptToSave,
                onSave = { title, prompt, category ->
                    coroutineScope.launch {
                        GeminiImageStudioController.savePrompt(context, title, prompt, category)
                        showSavePromptDialog = false
                        Toast.makeText(context, context.getString(R.string.image_studio_save_prompt_success), Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showSavePromptDialog = false }
            )
        }
    }
}

