package com.newoether.agora.ui.chat.fullflow

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.mesh.MeshController
import com.newoether.agora.mesh.ui.MeshHUDIndicator
import com.newoether.agora.model.ChatConversation
import com.newoether.agora.studio.image.GeminiImageStudioController
import com.newoether.agora.ui.chat.audio.FullFlowAudioController
import com.newoether.agora.ui.theme.OutfitFamily

@Composable
fun FullFlowHomeScreen(
    conversationsCount: Int,
    conversations: List<ChatConversation> = emptyList(),
    viewModel: com.newoether.agora.viewmodel.ChatViewModel? = null,
    onOpenSearch: () -> Unit,
    onOpenDrawer: () -> Unit,
    onActivateComposer: (String?) -> Unit,
    onSendTask: ((String) -> Unit)? = null,
    onResetHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour in 5..11 -> "Bonjour !"
            hour in 12..17 -> "Bon après-midi !"
            else -> "Bonsoir !"
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
        // Subtle ambient background light
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Action Bar ([brand] ... [-]  Home  [34]  ≡)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand label on far left + Pi-Mesh Live HUD
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FULLFLOW",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                    MeshHUDIndicator()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lens / Scan / Focus icon [-]
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Scan / Recherche",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }

                    Spacer(modifier = Modifier.width(AgoraSpacing.Xs))

                    // Home icon
                    IconButton(
                        onClick = onResetHome,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Accueil",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }

                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))

                    // Tab / Conversation Count Badge [34]
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = AgoraAlpha.Disabled),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = onOpenDrawer
                            )
                            .padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayCount = if (conversationsCount > 0) conversationsCount.toString() else "34"
                        Text(
                            text = displayCount,
                            fontFamily = OutfitFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))

                    // Drawer Hamburger Menu ≡
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu des conversations",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Primary Centered Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Greeting word
                Text(
                    text = greeting,
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 32.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(AgoraSpacing.Xs))

                // Greeting question
                Text(
                    text = "Comment puis-je vous aider aujourd'hui ?",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 17.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(AgoraSpacing.Xl))

                // Proactive Intelligence: 3 Dynamic Work Recommendations
                ProactiveWorkSection(
                    conversations = conversations,
                    viewModel = viewModel,
                    onTaskClick = { prompt -> onActivateComposer(prompt) },
                    onSendTask = onSendTask,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(AgoraSpacing.Xl))

                // Workspace Tools row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { com.newoether.agora.workspace.drive.GoogleDriveWorkspaceController.openDriveDashboard() },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = AgoraAlpha.Disabled),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = AgoraAlpha.Handle))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_drive),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(AgoraSpacing.Lg)
                            )
                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                            Text(
                                text = "Google Drive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Surface(
                        onClick = { FullFlowAudioController.openLiveTranscription() },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = AgoraAlpha.Disabled),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(AgoraSpacing.Lg)
                            )
                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                            Text(
                                text = "Studio Audio",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Surface(
                        onClick = { GeminiImageStudioController.openStudio() },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = AgoraAlpha.Disabled),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = AgoraAlpha.Handle))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nano_banana),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(AgoraSpacing.Lg)
                            )
                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                            Text(
                                text = "Nano Banana 2",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Surface(
                        onClick = { MeshController.openStudio() },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = AgoraAlpha.Disabled),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Disabled))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(AgoraSpacing.Lg)
                            )
                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                            Text(
                                text = "Pi-Mesh (Atelier)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Bottom Title Wordmark
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AgoraSpacing.Xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "FULLFLOW",
                    style = TextStyle(
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 6.sp,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                )
            }
        }
    }
}

@Composable
fun FullFlowSearchCapsule(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(elevation = 12.dp, shape = pillShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(pillShape)
            .background(Color(0xFF1E242B).copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = AgoraAlpha.Subtle),
                        Color.White.copy(alpha = 0.20f),
                    )
                ),
                shape = pillShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(horizontal = AgoraSpacing.Xl),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Recherche",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(AgoraSpacing.Xl)
            )
            Spacer(modifier = Modifier.width(AgoraSpacing.Md))
            Text(
                text = stringResource(R.string.search_fullflow_hint),
                fontFamily = OutfitFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

data class ProactiveTaskUi(
    val badge: String,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val icon: ImageVector,
    val accentColor: Color,
)

private fun resolveTaskVisuals(badge: String): Pair<ImageVector, Color> {
    return when {
        badge.contains("Reprendre", ignoreCase = true) || badge.contains("Poursuivre", ignoreCase = true) ->
            Icons.Default.AutoAwesome to Color(0xFFA78BFA)
        badge.contains("Approfondir", ignoreCase = true) || badge.contains("Cas pratique", ignoreCase = true) ->
            Icons.Default.Explore to Color(0xFF38BDF8)
        badge.contains("Synthèse", ignoreCase = true) || badge.contains("Transversal", ignoreCase = true) || badge.contains("Synergie", ignoreCase = true) ->
            Icons.Default.Hub to Color(0xFF34A853)
        badge.contains("Automatisation", ignoreCase = true) || badge.contains("Pipeline", ignoreCase = true) ->
            Icons.Default.Refresh to Color(0xFFF59E0B)
        badge.contains("Veille", ignoreCase = true) || badge.contains("Innovation", ignoreCase = true) ->
            Icons.Default.Search to Color(0xFF22D3EE)
        badge.contains("Stratégie", ignoreCase = true) ->
            Icons.Default.AutoAwesome to Color(0xFFA78BFA)
        badge.contains("Analyse", ignoreCase = true) ->
            Icons.Default.Explore to Color(0xFF38BDF8)
        else -> Icons.Default.Hub to Color(0xFF34A853)
    }
}

@Composable
fun ProactiveWorkSection(
    conversations: List<ChatConversation>,
    viewModel: com.newoether.agora.viewmodel.ChatViewModel? = null,
    onTaskClick: (String) -> Unit,
    onSendTask: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val liveTasks = viewModel?.proactiveIntelligence?.tasks?.collectAsState()?.value.orEmpty()
    val isGenerating = viewModel?.proactiveIntelligence?.isGenerating?.collectAsState()?.value == true



    val liveTasksKey = remember(liveTasks) {
        liveTasks.map { it.badge + it.title + it.prompt }.hashCode()
    }

    val tasks = remember(conversations, liveTasksKey) {
        if (liveTasks.isNotEmpty()) {
            liveTasks.map { t ->
                val (icon, color) = resolveTaskVisuals(t.badge)
                ProactiveTaskUi(
                    badge = t.badge,
                    title = t.title,
                    subtitle = t.subtitle,
                    prompt = t.prompt,
                    icon = icon,
                    accentColor = color,
                )
            }
        } else {
            val nonBlank = conversations.filter { it.title.isNotBlank() }
            val first = nonBlank.getOrNull(0)
            val second = nonBlank.getOrNull(1)

            if (first != null) {
                val task1 = ProactiveTaskUi(
                    badge = "Reprendre & Poursuivre",
                    title = "Continuer : ${first.title.take(38)}",
                    subtitle = "Explorer les prochaines étapes et livrables",
                    prompt = "Poursuivons sur '${first.title}' : quelles sont les prochaines étapes d'implémentation et les points critiques à vérifier ?",
                    icon = Icons.Default.AutoAwesome,
                    accentColor = Color(0xFFA78BFA)
                )

                val task2 = if (second != null) {
                    ProactiveTaskUi(
                        badge = "Approfondir",
                        title = "Approfondir : ${second.title.take(38)}",
                        subtitle = "Mise en pratique concrète et cas d'usage",
                        prompt = "Concernant '${second.title}' : peux-tu proposer une application concrète ou une étude de cas détaillée ?",
                        icon = Icons.Default.Explore,
                        accentColor = Color(0xFF38BDF8)
                    )
                } else {
                    ProactiveTaskUi(
                        badge = "Approfondir",
                        title = "Cas pratique sur ${first.title.take(32)}",
                        subtitle = "Mise en application opérationnelle",
                        prompt = "Concernant '${first.title}' : propose un plan de mise en œuvre opérationnel étape par étape avec un exemple concret.",
                        icon = Icons.Default.Explore,
                        accentColor = Color(0xFF38BDF8)
                    )
                }

                val task3 = if (second != null) {
                    ProactiveTaskUi(
                        badge = "Synthèse transversale",
                        title = "Croiser les enseignements récents",
                        subtitle = "Relier '${first.title.take(16)}' et '${second.title.take(16)}'",
                        prompt = "Fais une analyse croisée reliant '${first.title}' et '${second.title}' pour en dégager des synergies et opportunités stratégiques.",
                        icon = Icons.Default.Hub,
                        accentColor = Color(0xFF34A853)
                    )
                } else {
                    ProactiveTaskUi(
                        badge = "Synthèse transversale",
                        title = "Perspectives d'avenir & Synergies",
                        subtitle = "Élargir les angles d'analyse",
                        prompt = "Quelles sont les perspectives émergentes et les impacts transversaux majeurs liés à '${first.title}' ?",
                        icon = Icons.Default.Hub,
                        accentColor = Color(0xFF34A853)
                    )
                }

                val task4 = ProactiveTaskUi(
                    badge = "Automatisation & Pipelines",
                    title = "Automatiser : ${first.title.take(30)}",
                    subtitle = "Transformer la récurrence en processus fluide",
                    prompt = "À partir de notre travail sur '${first.title}' : identifie les étapes répétitives ou manuelles et propose un pipeline d'automatisation concret, étape par étape, avec les points de contrôle qualité.",
                    icon = Icons.Default.Refresh,
                    accentColor = Color(0xFFF59E0B)
                )

                val task5 = ProactiveTaskUi(
                    badge = "Veille & Innovation",
                    title = "Veille ciblée : ${first.title.take(28)}",
                    subtitle = "Avancées récentes et applications concrètes",
                    prompt = "Réalise une veille structurée sur les avancées les plus récentes liées à '${first.title}' : quelles innovations majeures sont apparues et comment les exploiter concrètement ?",
                    icon = Icons.Default.Search,
                    accentColor = Color(0xFF22D3EE)
                )

                listOf(task1, task2, task3, task4, task5)
            } else {
                listOf(
                    ProactiveTaskUi(
                        badge = "Stratégie",
                        title = "Plan d'action & Structuration",
                        subtitle = "Cadrer un projet ou des objectifs clés",
                        prompt = "Aide-moi à structurer un plan d'action clair pour un nouveau projet avec ses objectifs clés et étapes prioritaires.",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = Color(0xFFA78BFA)
                    ),
                    ProactiveTaskUi(
                        badge = "Analyse",
                        title = "Analyse critique & Rédaction",
                        subtitle = "Rédiger un document professionnel rigoureux",
                        prompt = "Je souhaite rédiger une analyse structurée et approfondie : guide-moi avec un plan et des arguments solides.",
                        icon = Icons.Default.Explore,
                        accentColor = Color(0xFF38BDF8)
                    ),
                    ProactiveTaskUi(
                        badge = "Veille",
                        title = "Veille & Synthèse IA",
                        subtitle = "Découvrir les opportunités émergentes",
                        prompt = "Quelles sont les avancées majeures et opportunités d'application récentes dans mon domaine à exploiter ?",
                        icon = Icons.Default.Hub,
                        accentColor = Color(0xFF34A853)
                    ),
                    ProactiveTaskUi(
                        badge = "Automatisation & Pipelines",
                        title = "Fluidifier vos flux de travail",
                        subtitle = "Identifier quoi automatiser en priorité",
                        prompt = "Aide-moi à identifier les tâches répétitives de mon quotidien de travail et à concevoir un pipeline d'automatisation simple et mesurable pour la plus coûteuse d'entre elles.",
                        icon = Icons.Default.Refresh,
                        accentColor = Color(0xFFF59E0B)
                    ),
                    ProactiveTaskUi(
                        badge = "Veille & Innovation",
                        title = "Veille du moment",
                        subtitle = "Les avancées récentes à exploiter",
                        prompt = "Dresse un panorama des innovations récentes dans mon domaine : quelles avancées majeures devrais-je exploiter cette semaine et par où commencer concrètement ?",
                        icon = Icons.Default.Search,
                        accentColor = Color(0xFF22D3EE)
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AgoraSpacing.Xs, vertical = AgoraSpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                Text(
                    text = "Travaux recommandés",
                    fontFamily = OutfitFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewModel != null) {
                    IconButton(
                        onClick = { viewModel.proactiveIntelligence.refresh(forceRefresh = true) },
                        enabled = !isGenerating,
                        modifier = Modifier.size(26.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = Color(0xFFA78BFA),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFA78BFA).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = AgoraAlpha.Handle))
                ) {
                    Text(
                    text = "Proactif IA",
                    fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA),
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = 3.dp)
                    )
                }
            }
        }

        tasks.forEach { task ->
            Surface(
                onClick = { onTaskClick(task.prompt) },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E242B).copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = AgoraAlpha.Divider)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(task.accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = task.icon,
                            contentDescription = null,
                            tint = task.accentColor,
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }

                    Spacer(modifier = Modifier.width(AgoraSpacing.Md))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = task.accentColor
                        )
                        }
                        Text(
                            text = task.title,
                            fontFamily = OutfitFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2
                        )
                        Text(
                            text = task.subtitle,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = AgoraAlpha.Hint),
                            maxLines = 2
                        )
                    }

                    if (onSendTask != null) {
                    IconButton(
                        onClick = { onSendTask(task.prompt) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Envoyer directement cette recommandation",
                            tint = task.accentColor,
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }
                }
            }
        }
    }
}

