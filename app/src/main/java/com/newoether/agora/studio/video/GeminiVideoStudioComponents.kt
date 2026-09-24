package com.newoether.agora.studio.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.theme.OutfitFamily
import com.newoether.agora.model.ModelId

@Composable
fun StudioVideoTopBar(
    activeModelDisplayName: String,
    onBackClick: () -> Unit,
    onModelClick: () -> Unit,
    onNewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.newoether.agora.studio.common.StudioTopBarCommon(
        modelDisplayName = activeModelDisplayName,
        onBackClick = onBackClick,
        onModelClick = onModelClick,
        onNewClick = onNewClick,
        modifier = modifier,
        newContentDescription = "Nouvelle vidéo",
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(AgoraSpacing.Xxxl)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6)),
                contentAlignment = Alignment.Center
            ) {
                Text("FF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
    )
}

@Composable
fun StudioVideoHeroHeader(
    modelName: String,
    modifier: Modifier = Modifier,
) {
    com.newoether.agora.studio.common.StudioHeroHeaderCommon(
        title = "Essayez un modèle ou décrivez une vidéo dans le chat",
        subtitle = "Créez avec $modelName",
        accent = Color(0xFFA78BFA),
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(26.dp)
            )
        },
    )
}

@Composable
fun StudioVideoHeroPresetCard(
    preset: StudioVideoPresetTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF121822),
        border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Divider)),
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(32.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = preset.fallbackGradientColors.map { Color(it) }
                        )
                    )
            ) {
                // Drone tiny planet landscape visual simulation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.5f), Color.Transparent),
                                radius = 400f
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.88f),
                            ),
                            startY = 70f,
                        )
                    )
            )

            Text(
                text = preset.title,
                fontFamily = OutfitFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Lg)
            )
        }
    }
}

@Composable
fun StudioVideoSubPresetCard(
    preset: StudioVideoPresetTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF121822),
        border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Divider)),
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(26.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = preset.fallbackGradientColors.map { Color(it) }
                        )
                    )
            ) {
                if (preset.id == "anime") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF34D399).copy(alpha = 0.4f), Color.Transparent),
                                    radius = 260f
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFEC4899).copy(alpha = 0.4f), Color.Transparent),
                                    radius = 260f
                                )
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.88f),
                            ),
                            startY = 50f,
                        )
                    )
            )

            Text(
                text = preset.title,
                fontFamily = OutfitFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)
            )
        }
    }
}

@Composable
fun StudioVideoResultsRow(
    videos: List<GeneratedStudioVideo>,
    onPlay: (GeneratedStudioVideo) -> Unit,
    onSave: (GeneratedStudioVideo) -> Unit,
    onShare: (GeneratedStudioVideo) -> Unit,
    onInsertChat: (GeneratedStudioVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Lg)
    ) {
        items(videos, key = { it.id }) { video ->
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF131A24),
                border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Divider)),
                modifier = Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color(0xFF0F172A))
                            .clickable { onPlay(video) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = AgoraAlpha.Hint)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Lire la vidéo",
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(AgoraSpacing.Sm)
                        ) {
                            Text(
                                text = video.duration.label,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xxs)
                            )
                        }
                    }

                    Text(
                        text = video.prompt,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSave(video) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Enregistrer",
                                tint = Color.White,
                                modifier = Modifier.size(AgoraSpacing.Xxl)
                            )
                        }
                        IconButton(onClick = { onShare(video) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Partager",
                                tint = Color.White,
                                modifier = Modifier.size(AgoraSpacing.Xxl)
                            )
                        }
                        Button(
                            onClick = { onInsertChat(video) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA78BFA)),
                            contentPadding = PaddingValues(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Xs),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Chat", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioVideoModelPickerSheet(
    currentModel: StudioVideoModel,
    useSettingsModel: Boolean,
    customModelId: String?,
    onSelectModel: (StudioVideoModel) -> Unit,
    onSelectSettingsModel: () -> Unit,
    onSelectCustomModel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as? com.newoether.agora.AgoraApplication
    val container = app?.requireContainer()
    val settingsRepo = container?.settingsRepository
    val settingsModel by (settingsRepo?.videoGenModel ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val availableModels by (settingsRepo?.availableModels ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
    val modelAliases by (settingsRepo?.modelAliases ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
    val customProviders by (settingsRepo?.customProviders ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()

    val allAvailableList = remember(availableModels) {
        val models = availableModels.values.flatten().distinct()
        if (models.isNotEmpty()) {
            models.sorted()
        } else {
            listOf(
                "openai:sora",
                "openai:gpt-4o",
                "openai:gpt-4o-mini",
                "anthropic:claude-3-5-sonnet-20241022",
                "groq:llama-3.3-70b-versatile",
                "mistral:mistral-large-latest",
                "local:llama3"
            )
        }
    }

    val videoSpecializedList = remember(allAvailableList) {
        val keywords = listOf(
            "veo", "sora", "gen2", "gen-2", "gen3", "gen-3", "kling", "luma", "runway", "pika",
            "hailuo", "minimax", "dream-machine", "dreammachine", "cogvideo", "hunyuan-video",
            "hunyuanvideo", "wan2", "wan-2", "mochi", "ltx-video", "ltxvideo", "fuyu", "animatediff",
            "svd", "stable-video", "stablevideo", "videocrafter", "vlogger", "dynami", "omni"
        )
        allAvailableList.filter { modelId ->
            val id = modelId.substringAfter(":").lowercase()
            keywords.any { id.contains(it) }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Tous les LLMs, 1: Spécialisés Vidéo, 2: Google Studio
    var searchQuery by remember { mutableStateOf("") }
    val apiKeys by (settingsRepo?.apiKeys ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141922),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = AgoraSpacing.Md)
                    .width(36.dp)
                    .height(AgoraSpacing.Xs)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = AgoraAlpha.Handle))
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Sm)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sélection du Modèle LLM / Vidéo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Search bar
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B2230),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Divider)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFA78BFA)),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Rechercher un modèle ou fournisseur...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Effacer",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(AgoraSpacing.Xl)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }
            }

            // Filter Tabs: [Tous les LLMs] [Spécialisés Vidéo] [Google Studio]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                ) {
                    val tabs = listOf("Tous les LLMs", "Spécialisés Vidéo", "Google Studio")
                    tabs.forEachIndexed { index, label ->
                        val isTabSelected = selectedTab == index
                        Surface(
                            onClick = { selectedTab = index },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isTabSelected) Color(0xFFA78BFA) else Color(0xFF1F2937),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTabSelected) Color.Black else Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = AgoraSpacing.Sm, horizontal = AgoraSpacing.Xs)
                            )
                        }
                    }
                }
            }

            if (!settingsModel.isNullOrBlank() && (selectedTab == 0 || selectedTab == 1)) {
                val cleanSettingsModelName = settingsModel?.substringAfter(":") ?: "Modèle global"
                val isSelected = useSettingsModel && customModelId == null
                item {
                    Surface(
                        onClick = {
                            onSelectSettingsModel()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF1F2937) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFFA78BFA)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(AgoraSpacing.Lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cleanSettingsModelName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFFA78BFA) else Color.White
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFA78BFA).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Configuré globalement",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA78BFA),
                                            modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xxs)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
                                Text(
                                    text = "Modèle vidéo multi-LLM des Paramètres ($settingsModel)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA)
                                )
                            }
                        }
                    }
                }
            }

            // Google Studio models tab
            if (selectedTab == 2) {
                item {
                    Text(
                        text = "Modèles du Studio (Google Gemini Veo & Omni)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA),
                        modifier = Modifier.padding(bottom = AgoraSpacing.Xxs)
                    )
                }

                items(StudioVideoModel.values().toList().filter {
                    searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true)
                }) { model ->
                    val isSelected = model == currentModel && !useSettingsModel
                    Surface(
                        onClick = {
                            onSelectModel(model)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF1F2937) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFFA78BFA)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(AgoraSpacing.Lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFFA78BFA) else Color.White
                                    )
                                    if (model.isPrimary) {
                                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFA78BFA).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "Recommandé",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFA78BFA),
                                                modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xxs)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
                                Text(
                                    text = model.description,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA)
                                )
                            }
                        }
                    }
                }
            }

            // Multi-Provider models (Tab 0: All, Tab 1: Video specialized)
            if (selectedTab == 0 || selectedTab == 1) {
                val candidateList = if (selectedTab == 1) videoSpecializedList else allAvailableList
                val filteredList = candidateList.filter {
                    searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true)
                }

                item {
                    Text(
                        text = if (selectedTab == 1) "Modèles Spécialisés Génération Vidéo" else "Tous les Fournisseurs & Modèles LLM",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA),
                        modifier = Modifier.padding(top = AgoraSpacing.Xs, bottom = AgoraSpacing.Xxs)
                    )
                }

                if (filteredList.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1A222F),
                            modifier = Modifier.fillMaxWidth().padding(vertical = AgoraSpacing.Sm)
                        ) {
                            Text(
                                text = "Aucun modèle ne correspond à votre recherche.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(AgoraSpacing.Lg),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                items(filteredList) { modelId ->
                    val isSelected = useSettingsModel && customModelId == modelId
                    val parsed = ModelId.parse(modelId)
                    val displayName = com.newoether.agora.data.modelAliasDisplayName(modelId, modelAliases, customProviders)
                    val providerName = com.newoether.agora.data.providerDisplayName(parsed.providerName, customProviders)
                    val hasKey = apiKeys.any { it.provider.equals(parsed.providerName, ignoreCase = true) && it.key.isNotBlank() } ||
                                 parsed.providerName.equals("local", ignoreCase = true)

                    Surface(
                        onClick = {
                            onSelectCustomModel(modelId)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF1F2937) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFFA78BFA)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(AgoraSpacing.Lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFFA78BFA) else Color.White
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (hasKey) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (hasKey) "Clé prête" else "Clé à vérifier",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasKey) Color(0xFF34D399) else Color(0xFFFBBF24),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
                                Text(
                                    text = "$providerName · $modelId",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(AgoraSpacing.Lg))
            }
        }
    }
}
