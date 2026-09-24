package com.newoether.agora.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraRadii
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.settings.datacontrol.SettingsDataControlPage
import com.newoether.agora.viewmodel.ChatViewModel

/** When true, [SettingsGroup] inside a [SettingsGroupColumn] suppresses its own bottom padding
 *  (spacing is handled by the column's [Arrangement.spacedBy] instead). */
val LocalSettingsGroupSpacing = staticCompositionLocalOf { false }

/** Settings page content container: uniform 24dp spacing between groups (and any other elements),
 *  with zero trailing after the last element. */
@Composable
fun SettingsGroupColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = AgoraSpacing.Xxl,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalSettingsGroupSpacing provides true) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = AgoraSpacing.Xxl,
    items: List<@Composable () -> Unit>
) {
    val effectiveBottom = if (LocalSettingsGroupSpacing.current) 0.dp else bottomPadding
    Column(modifier = modifier.fillMaxWidth().padding(bottom = effectiveBottom)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
                }
                val isFirst = index == 0
                val isLast = index == items.lastIndex
                val shape = when {
                    items.size == 1 -> RoundedCornerShape(5.dp)__
                    isFirst -> RoundedCornerShape(5.dp)__
                    isLast -> RoundedCornerShape(5.dp)__
                    else -> RoundedCornerShape(5.dp)__
                }
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp__,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item()
                }
            }
        }
    }
}

/** Shared body for a [SettingsGroup] item with a primary-tinted leading icon and content column. */
@Composable
fun SettingsIconContent(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Lg)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = AgoraSpacing.Xxs),
            )
            Spacer(Modifier.width(AgoraSpacing.Lg))
            Column(modifier = Modifier.weight(1f), content = content)
        }
    }
}

@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    leadingSpacing: Dp = AgoraSpacing.Lg,
    endPadding: Dp = AgoraSpacing.Lg,
) {
    val verticalPadding = if (supportingContent == null) AgoraSpacing.Md else AgoraSpacing.Lg
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AgoraSpacing.Lg,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                leadingContent()
            }
            Spacer(modifier = Modifier.width(leadingSpacing))
        }
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                headlineContent()
            }
            if (supportingContent != null) {
                Spacer(modifier = Modifier.height(3.dp))
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    supportingContent()
                }
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
            trailingContent()
        }
    }
}

/**
 * Canonical centered add action for settings groups.
 *
 * Keep this aligned with the settings row contract instead of recreating its
 * dimensions in individual pages.
 */
@Composable
fun SettingsAddItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = AgoraAlpha.Disabled)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = AgoraSpacing.Lg),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(AgoraSpacing.Xl),
                tint = contentColor,
            )
            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private data class SettingsCategory(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
)

private data class SettingsGroupData(
    val titleRes: Int? = null,
    val items: List<SettingsCategory>
)

private val baseSettingsGroups = listOf(
    SettingsGroupData(titleRes = R.string.settings_group_services, items = listOf(
        SettingsCategory("provider", R.string.settings_provider, R.string.settings_provider_desc, Icons.Default.Cloud),
        SettingsCategory("models", R.string.settings_models, R.string.settings_models_desc, Icons.AutoMirrored.Filled.Chat),
        SettingsCategory("workspace", R.string.settings_workspace, R.string.settings_workspace_desc, Icons.Default.Work),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_responses, items = listOf(
        SettingsCategory("prompts", R.string.settings_prompts, R.string.settings_prompts_desc, Icons.Default.Psychology),
        SettingsCategory("generation", R.string.settings_generation, R.string.settings_generation_desc, Icons.Default.Tune),
        SettingsCategory("context", R.string.context_title, R.string.context_desc, Icons.Default.Memory),
        SettingsCategory("titlegen", R.string.settings_title_gen, R.string.settings_title_gen_desc, Icons.Default.Edit),
        SettingsCategory("proactive", R.string.settings_proactive, R.string.settings_proactive_desc, Icons.Default.TipsAndUpdates),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_multimodal, items = listOf(
        SettingsCategory("transcription", R.string.settings_transcription, R.string.settings_transcription_desc, Icons.Default.ImageSearch),
        SettingsCategory("imagegen", R.string.settings_image_gen, R.string.settings_image_gen_desc, Icons.Default.AddPhotoAlternate),
        SettingsCategory("videogen", R.string.settings_video_gen, R.string.settings_video_gen_desc, Icons.Default.Videocam),
        SettingsCategory("backgroundgen", R.string.settings_background_gen, R.string.settings_background_gen_desc, Icons.Default.Wallpaper),
        SettingsCategory("podcastgen", R.string.settings_podcast_gen, R.string.settings_podcast_gen_desc, Icons.Default.GraphicEq),
        SettingsCategory("podcastdist", R.string.settings_podcast_dist, R.string.settings_podcast_dist_desc, Icons.Default.RssFeed),
        SettingsCategory("editorial", R.string.settings_editorial, R.string.settings_editorial_desc, Icons.Default.Book),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_tools, items = listOf(
        SettingsCategory("websearch", R.string.settings_web_search, R.string.settings_web_search_desc, Icons.Default.Language),
        SettingsCategory("search", R.string.search_title, R.string.search_desc, Icons.Default.Search),
        SettingsCategory("shell", R.string.shell_title, R.string.shell_desc, Icons.Default.Terminal),
        SettingsCategory(
            "mcp",
            R.string.mcp_title,
            R.string.mcp_desc,
            iconRes = R.drawable.ic_mcp,
        ),
        SettingsCategory("automation", R.string.settings_automation, R.string.settings_automation_desc, Icons.Default.Repeat),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_network, items = listOf(
        SettingsCategory("proxy", R.string.settings_proxy, R.string.settings_proxy_desc, Icons.Default.Lan),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_memory_data, items = listOf(
        SettingsCategory("memory", R.string.settings_memory, R.string.settings_memory_desc, Icons.Default.Description),
        SettingsCategory("secondbrain", R.string.settings_second_brain, R.string.settings_second_brain_desc, Icons.Default.Psychology),
        SettingsCategory("personas", R.string.settings_personas, R.string.settings_personas_desc, Icons.Default.Person),
        SettingsCategory("agents", R.string.settings_agents, R.string.settings_agents_desc, Icons.Default.SmartToy),
        SettingsCategory("skills", R.string.settings_skills, R.string.settings_skills_desc, Icons.Default.Extension),
        SettingsCategory("wand", R.string.settings_wand, R.string.settings_wand_desc, Icons.Default.AutoAwesome),
        SettingsCategory("datacontrol", R.string.settings_data_control, R.string.settings_data_control_desc, Icons.Default.Storage),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_appearance_language, items = listOf(
        SettingsCategory("appearance", R.string.settings_appearance, R.string.settings_appearance_desc, Icons.Default.Palette),
        SettingsCategory("language", R.string.language_title, R.string.language_desc, Icons.Default.Translate),
    )),
)

private val developerSettingsGroup = SettingsGroupData(
    titleRes = R.string.settings_group_developer,
    items = listOf(
        SettingsCategory(
            "developer",
            R.string.settings_developer,
            R.string.settings_developer_desc,
            Icons.Default.BugReport,
        ),
    ),
)

private val aboutSettingsGroup = SettingsGroupData(
    titleRes = R.string.settings_group_about,
    items = listOf(
        SettingsCategory("about", R.string.settings_about, R.string.settings_about_desc, Icons.Default.Info),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val developerOptionsEnabled by viewModel.settings.developerOptionsEnabled.collectAsState()
    val settingsGroups = remember(developerOptionsEnabled) {
        buildList {
            addAll(baseSettingsGroups)
            if (developerOptionsEnabled) add(developerSettingsGroup)
            add(aboutSettingsGroup)
        }
    }
    val listState = rememberLazyListState()

    BackHandler {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GuardedAnimatedContent(
            targetState = selectedCategory,
            forward = selectedCategory != null
        ) { category ->
            when (category) {
                "provider" -> SettingsProviderPage(viewModel, onBack = { selectedCategory = null })
                "prompts" -> SettingsPromptsPage(viewModel, onBack = { selectedCategory = null })
                "models" -> SettingsModelsPage(viewModel, onBack = { selectedCategory = null })
                "workspace" -> SettingsWorkspacePage(viewModel, onBack = { selectedCategory = null })
                "generation" -> SettingsGenerationPage(viewModel, onBack = { selectedCategory = null })
                "context" -> SettingsContextPage(viewModel, onBack = { selectedCategory = null })
                "websearch" -> SettingsWebSearchPage(viewModel, onBack = { selectedCategory = null })
                "imagegen" -> SettingsImageGenPage(viewModel, onBack = { selectedCategory = null })
                "videogen" -> SettingsVideoGenPage(viewModel, onBack = { selectedCategory = null })
                "backgroundgen" -> SettingsBackgroundGenPage(viewModel, onBack = { selectedCategory = null })
                "podcastgen" -> SettingsPodcastGenPage(viewModel, onBack = { selectedCategory = null })
                "editorial" -> SettingsEditorialPage(viewModel, onBack = { selectedCategory = null })
                "agents" -> SettingsAgentsPage(viewModel, onBack = { selectedCategory = null })
                "podcastdist" -> SettingsPodcastDistributionPage(viewModel, onBack = { selectedCategory = null })
                "shell" -> SettingsShellPage(viewModel, onBack = { selectedCategory = null })
                "mcp" -> SettingsMcpPage(viewModel, onBack = { selectedCategory = null })
                "automation" -> SettingsAutomationPage(viewModel, onBack = { selectedCategory = null })
                "proxy" -> SettingsProxyPage(viewModel, onBack = { selectedCategory = null })
                "language" -> SettingsLanguagePage(viewModel, onBack = { selectedCategory = null })
                "titlegen" -> SettingsTitleGenPage(viewModel, onBack = { selectedCategory = null })
                "proactive" -> SettingsProactivePage(viewModel, onBack = { selectedCategory = null })
                "transcription" -> SettingsTranscriptionPage(viewModel, onBack = { selectedCategory = null })
                "search" -> SettingsSearchPage(viewModel, onBack = { selectedCategory = null })
                "memory" -> SettingsMemoryPage(viewModel, onBack = { selectedCategory = null })
                "secondbrain" -> {
                    // Open the Second Brain screen directly (full-screen dialog)
                    selectedCategory = null
                    com.newoether.agora.ui.chat.live.SecondBrainController.open()
                }
                "personas" -> SettingsPersonasPage(viewModel, onBack = { selectedCategory = null })
                "skills" -> SettingsSkillsPage(viewModel, onBack = { selectedCategory = null })
                "wand" -> SettingsWandPage(
                    viewModel = viewModel,
                    onBack = { selectedCategory = null },
                    onOpenMcp = { selectedCategory = "mcp" },
                )
                "datacontrol" -> SettingsDataControlPage(viewModel, onBack = { selectedCategory = null })
                "appearance" -> SettingsAppearancePage(viewModel, onBack = { selectedCategory = null })
                "developer" -> SettingsDeveloperPage(
                    viewModel = viewModel,
                    onBack = { selectedCategory = null },
                    onDisabled = { selectedCategory = null },
                )
                "about" -> SettingsAboutPage(viewModel, onBack = { selectedCategory = null })
                else -> {
                    CollapsingSettingsLazyScaffold(
                        title = stringResource(R.string.settings_title),
                        onBack = onBack,
                        listState = listState
                    ) {
                        items(settingsGroups.size) { groupIndex ->
                            val group = settingsGroups[groupIndex]
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (group.titleRes != null) {
                                    Text(
                                        text = stringResource(group.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)
                                    )
                                }
                                group.items.forEachIndexed { index, cat ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
                                    }
                                    val shape = AgoraRadii.stackedShape(index, group.items.size)
                                    Surface(
                                        shape = shape,
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 1.dp__,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .clickable { selectedCategory = cat.key }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Lg),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (cat.iconRes != null) {
                                                Icon(
                                                    painter = painterResource(cat.iconRes),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(AgoraSpacing.Xxl),
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = checkNotNull(cat.icon),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(AgoraSpacing.Xxl),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(cat.titleRes),
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = stringResource(cat.descriptionRes),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (groupIndex < settingsGroups.size - 1) {
                                Spacer(modifier = Modifier.height(AgoraSpacing.Xl))
                            }
                        }
                    }
                }
            }
        }

    }
}
