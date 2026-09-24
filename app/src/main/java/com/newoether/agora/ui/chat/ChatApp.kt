package com.newoether.agora.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.newoether.agora.ui.motion.MotionAwareCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.TopLevelPresentation
import com.newoether.agora.api.DebugProvider
import com.newoether.agora.data.forDisplay
import com.newoether.agora.data.replaceCustomProviderIdsForDisplay
import com.newoether.agora.util.gradientBlur
import com.newoether.agora.ui.chat.bottombar.CHAT_BOTTOM_BAR_OUTER_SHAPE
import com.newoether.agora.ui.chat.bottombar.ChatBottomBar
import com.newoether.agora.ui.chat.bottombar.LoopStatusBackdrop
import com.newoether.agora.ui.components.AnimatedBlobBackground
import com.newoether.agora.ui.components.clearFocusOnTap
import com.newoether.agora.ui.components.TypewriterMode
import com.newoether.agora.ui.components.TypewriterText
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.ui.common.rememberAgoraHaptics
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.FullLiveBridge
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import com.newoether.agora.model.StableMessageList
import com.newoether.agora.model.StableModelAliases
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.validChatModels
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun ChatApp(
    viewModel: ChatViewModel,
    initialComposerFocusReady: Boolean = true,
    onNavigateBack: (() -> Unit)? = null,
    drawerEnabled: Boolean = true,
    onOpenSettings: () -> Unit,
    onOpenTasks: (String?) -> Unit = {},
    onMediaClick: (List<String>, Int) -> Unit,
    onFileContentClick: ((String, String) -> Unit)? = null,
    onPdfPagesClick: ((List<String>, Int) -> Unit)? = null,
    onPdfPreviewSelect: ((List<String>, Int) -> Unit)? = null,
    pdfViewerSelection: Set<Int> = emptySet(),
    onTogglePdfSelection: ((Int) -> Unit)? = null,
    onInitPdfSelection: ((Set<Int>) -> Unit)? = null,
    fullScreenViewerUrls: List<String>? = null,
    topLevelPresentation: TopLevelPresentation = TopLevelPresentation.CHAT,
    onSnackbarOffsetChanged: (androidx.compose.ui.unit.Dp) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val motionPolicy = LocalAgoraMotionPolicy.current
    ConversationShareEffect(viewModel, context)
    val drawerState = rememberChatDrawerState()
    val conversations by viewModel.conversations.collectAsState()
    // Defer value reads to the narrow composition regions that actually render messages. The
    // State objects themselves are stable, so stream snapshots no longer recompose all ChatApp.
    val messagesState = viewModel.messages.collectAsState()
    val allMessagesState = viewModel.allMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCompacting by viewModel.isCompacting.collectAsState()
    val compactModel by viewModel.settings.contextCompactModel.collectAsState()
    val compactPrompt by viewModel.settings.contextCompactPrompt.collectAsState()
    val compactRetainCount by viewModel.settings.contextCompactRetainCount.collectAsState()
    val compactThresholdPercent by
        viewModel.settings.contextCompactThresholdPercent.collectAsState()
    val manualCompactDialogVisible = rememberSaveable { mutableStateOf(false) }
    var showGeminiLiveDialog by rememberSaveable { mutableStateOf(false) }
    var showGenMailDialog by rememberSaveable { mutableStateOf(false) }
    val genMailFromController by com.newoether.agora.ui.chat.fullflow.GenMailController.showGenMail.collectAsState()
    var showPlusSheet by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val dialogState = rememberChatAppDialogState(manualCompactDialogVisible)
    val queuedSends by viewModel.queuedSends.collectAsState()
    val isStopping by viewModel.isStopping.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    var pendingForkRequest by remember(currentConversationId) { mutableStateOf<ForkConversationRequest?>(null) }
    val currentConversation by viewModel.currentConversation.collectAsState()
    val loadedMessagesConversationId by viewModel.loadedMessagesConversationId.collectAsState()
    val currentLoop by viewModel.currentLoop.collectAsState()
    val runningLoopIds by viewModel.runningLoopConversationIds.collectAsState()
    val generationSnapshot by viewModel.generationSnapshot.collectAsState()
    val selectedConversationGenerationSnapshot by
        viewModel.selectedConversationGenerationSnapshot.collectAsState()
    val selectedModel by viewModel.currentActiveModel.collectAsState()
    val enabledModels by viewModel.settings.enabledModels.collectAsState()
    val developerOptionsEnabled by viewModel.settings.developerOptionsEnabled.collectAsState()
    val debugModelEnabled by viewModel.settings.debugModelEnabled.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val modelProviderNames by viewModel.settings.modelProviderNames.collectAsState()
    val chatEnabledModels = validChatModels(enabledModels, developerOptionsEnabled, debugModelEnabled)
    val chatModelAliases = if (DebugProvider.MODEL_ID in chatEnabledModels) {
        modelAliases + (DebugProvider.MODEL_ID to DebugProvider.PROVIDER_NAME)
    } else modelAliases
    val thoughtExpandedStates = remember(currentConversationId) { mutableStateMapOf<String, Boolean>() }
    val isNewChatMode by viewModel.isNewChatMode.collectAsState()
    val newChatEntryId by viewModel.newChatEntryId.collectAsState()
    val isSwitching by viewModel.isSwitching.collectAsState()
    val regenerationTransition by viewModel.regenerationTransition.collectAsState()
    val isTransitioningToNewChat by viewModel.isTransitioningToNewChat.collectAsState()
    val visualizeContextRollout by viewModel.settings.visualizeContextRollout.collectAsState()
    val customProviders by viewModel.settings.customProviders.collectAsState()
    val displayConversations = remember(conversations, customProviders) { conversations.orEmpty().map { it.forDisplay(customProviders) } }
    val displayMessagesState = remember(messagesState, customProviders) { derivedStateOf { messagesState.value.map { it.forDisplay(customProviders) } } }
    val webSearchApiKeys by viewModel.settings.webSearchApiKeys.collectAsState()
    val shellDevices by viewModel.settings.shellDevices.collectAsState()
    val amoledEnabled by viewModel.settings.amoledEnabled.collectAsState()
    val toolCallDisplayMode by viewModel.settings.toolCallDisplayMode.collectAsState()
    val thinkingSegmentDisplayMode by viewModel.settings.thinkingSegmentDisplayMode.collectAsState()
    val autoExpandActiveGroup by viewModel.settings.autoExpandActiveGroup.collectAsState()

    val parseInlineDollarMath by viewModel.settings.parseInlineDollarMath.collectAsState()
    val conversationControls = effectiveConversationControls(
        viewModel = viewModel,
        isNewChatMode = isNewChatMode,
        currentConversationId = currentConversationId,
        selectedModel = selectedModel,
        customProviders = customProviders,
    )
    val contextProjectionKey = rememberContextProjectionInvalidationKey(
        viewModel,
        listOf(
            conversationControls.codeExecutionEnabled,
            conversationControls.googleSearchEnabled,
            conversationControls.webSearchEnabled,
            conversationControls.shellEnabled,
            shellDevices,
            currentConversation?.systemPromptId,
            conversationControls.lowContextModeEnabled,
        ),
    )
    val contextProjection by viewModel.conversationContextProjection.collectAsState()
    LaunchedEffect(currentConversationId, currentConversation?.selectedBranchesJson, selectedModel, conversationControls.contextWindow, allMessagesState.value, contextProjectionKey) {
        viewModel.requestConversationContext(currentConversationId, currentConversation?.selectedBranchesJson, selectedModel, conversationControls.contextWindow)
    }
    val contextProjectionReady = contextProjection.completed && !contextProjection.loading && !contextProjection.failed && contextProjection.conversationId == currentConversationId && contextProjection.selectedBranchesJson == currentConversation?.selectedBranchesJson
    val contextUsage = contextProjection.usage ?: com.newoether.agora.api.util.ContextWindowUsage(0, conversationControls.contextWindow, 0, false)
    val blurEffectsEnabled by viewModel.settings.blurEffectsEnabled.collectAsState()
    val stickToBottom by viewModel.settings.stickToBottom.collectAsState()
    val reduceMotion = motionPolicy.reduceMotion
    val hapticsEnabled by viewModel.settings.hapticsEnabled.collectAsState()
    val haptics = rememberAgoraHaptics(hapticsEnabled)
    // The three send paths (manual Send, queue drain, loop cycle) converge in the Controller at
    // notifySendAccepted, the single choke point for Direct + Queued send acceptances. Wiring the
    // haptics there gives every accepted send exactly one confirm(), independent of which path
    // triggered it or which scroll policy applies.
    SendAcceptedHapticBindingEffect(viewModel, haptics)

    var isExpanded by remember { mutableStateOf(false) }
    // Composer-expand spacer collapse (44dp → 0). An Animatable driven from an effect replaces the
    // former hand-rolled clock, which wrote animation state DURING composition (Compose forbids
    // that — it makes the frame's output depend on when it happened to be composed) and ticked on
    // a fixed 16ms sleep that drifts against the real refresh rate.
    val composerSpacerAnimation = rememberComposerSpacerAnimation(
        isExpanded = isExpanded,
        allowSpatialTransitions = motionPolicy.allowSpatialTransitions,
        expandedHeightPx = with(density) { 44.dp.toPx() },
    )
    val isExpandAnimating = composerSpacerAnimation.isRunning
    val outerSpacerHeightPx = composerSpacerAnimation.outerHeightPx

    val windowHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.toFloat().coerceAtLeast(1f)
    var bottomBarHeightPx by rememberSaveable { mutableFloatStateOf(0f) }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
    var drawerProgress by remember { mutableFloatStateOf(0f) }
    // Bottom offset to clear the Settings button in the drawer.
    var settingsButtonTopDp by remember { mutableFloatStateOf(80f) }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // When expanded, the Surface fills the screen and the model-selector capsule sits
    // at the very bottom. Snackbar must clear: nav bar + IME + Surface outer padding + Box
    // bottom padding + Row height/margin + a small gap.
    val bottomInset = maxOf(navBarBottom, imeBottom)
    SnackbarOffsetEffect(
        drawerProgress = drawerProgress,
        isExpanded = isExpanded,
        bottomBarHeight = bottomBarHeight,
        settingsButtonTopDp = settingsButtonTopDp,
        bottomInset = bottomInset,
        onOffsetChanged = onSnackbarOffsetChanged,
    )
    val imeBottomPx = with(density) { imeBottom.roundToPx() }
    val scrollCoordinator = rememberChatScrollCoordinator(
        currentConversationId,
        imeBottomPx,
    )
    scrollCoordinator.BindLayoutObservation(
        currentConversationId = currentConversationId,
        loadedMessagesConversationId = loadedMessagesConversationId,
        imeBottomPx = imeBottomPx,
        density = density,
    )
    val listState = scrollCoordinator.listState
    val absoluteBottomScrollPhase = scrollCoordinator.absoluteBottomScrollPhase
    val isNearAbsoluteBottom = scrollCoordinator.isNearAbsoluteBottom
    val isWithinAbsoluteBottomAttachThreshold =
        scrollCoordinator.isWithinAbsoluteBottomAttachThreshold
    val imeBottomAnchorState = scrollCoordinator.imeBottomAnchorState
    val streamingTailController = scrollCoordinator.streamingTailController
    val messageLifecycleAppearanceRegistry = scrollCoordinator.messageLifecycleAppearanceRegistry
    val messageHeights = scrollCoordinator.messageHeights
    val viewportHeightPx = scrollCoordinator.viewportHeightPx
    val renderMessagesState = rememberScrollIsolatedMessages(
        conversationId = currentConversationId,
        upstream = messagesState,
        listState = listState,
        bypassScrollIsolation =
            streamingTailController.isAutoFollowing || absoluteBottomScrollPhase.isActive,
    )
    val messageHydration = rememberChatMessageHydrationBindings(viewModel, customProviders)
    val conversationInteraction = rememberConversationInteractionState(
        currentConversationId = currentConversationId,
        messages = displayMessagesState,
        listState = listState,
        searchMessages = messageHydration.searchMessages,
    )
    val conversationSearchActive = conversationInteraction.searchActive
    val conversationSearchQuery = conversationInteraction.searchQuery
    val conversationSearchMatchIndex = conversationInteraction.searchMatchIndex
    val shareSelectionActive = conversationInteraction.shareSelectionActive
    val selectedShareMessageIds = conversationInteraction.selectedShareMessageIds
    val selectableShareMessageIds = conversationInteraction.selectableShareMessageIds
    val shareSelectionBarSpace = if (shareSelectionActive) 68.dp else 0.dp
    val conversationSearchMatches = conversationInteraction.searchMatches
    val textFieldState = rememberSaveable(saver = androidx.compose.foundation.text.input.TextFieldState.Saver) { androidx.compose.foundation.text.input.TextFieldState() }
    val sandboxEnabled by viewModel.settings.sandboxEnabled.collectAsState()
    val conversationSettings by viewModel.settings.conversationSettings.collectAsState()
    val composer = com.newoether.agora.ui.chat.bottombar.rememberChatComposerState(
        sandboxEnabled,
        viewModel.isSandboxFlavor,
    )
    val inputFocusRequester = remember { FocusRequester() }
    var showLaunchContent by remember { mutableStateOf(false) }
    ChatLaunchInteractionEffects(
        initialComposerFocusReady = initialComposerFocusReady,
        inputFocusRequester = inputFocusRequester,
        onShowLaunchContent = { showLaunchContent = true },
    )

    scrollCoordinator.BindTransitionEffects(
        currentConversationId = currentConversationId,
        currentConversation = currentConversation,
        loadedMessagesConversationId = loadedMessagesConversationId,
        messages = messagesState,
        density = density,
        motionPolicy = motionPolicy,
        bottomBarHeight = bottomBarHeight,
        shareSelectionBarSpace = shareSelectionBarSpace,
        imeBottomPx = imeBottomPx,
        viewModel = viewModel,
        haptics = haptics,
    )

    val composerOwnerId = if (isNewChatMode) com.newoether.agora.viewmodel.NEW_CHAT_WORKSPACE_ID else currentConversationId ?: com.newoether.agora.viewmodel.NEW_CHAT_WORKSPACE_ID
    val composerSnapshot by rememberComposerDraftSnapshot(
        ownerId = composerOwnerId,
        controller = viewModel.conversationComposer,
        viewModel = viewModel,
        textFieldState = textFieldState,
    )

    val animatedScrollRequest by viewModel.animatedScrollRequest.collectAsState()
    scrollCoordinator.BindRequestEffects(
        currentConversationId = currentConversationId,
        isNewChatMode = isNewChatMode,
        isLoading = isLoading,
        isStopping = isStopping,
        isSwitching = isSwitching,
        conversationSearchActive = conversationSearchActive,
        shareSelectionActive = shareSelectionActive,
        regenerationTransition = regenerationTransition,
        animatedScrollRequest = animatedScrollRequest,
        messages = messagesState,
        density = density,
        motionPolicy = motionPolicy,
        bottomBarHeight = bottomBarHeight,
        shareSelectionBarSpace = shareSelectionBarSpace,
        viewModel = viewModel,
    )

    ChatNavigationEffects(
        drawerState = drawerState,
        focusManager = focusManager,
        scope = scope,
        motionPolicy = motionPolicy,
        onNavigateBack = onNavigateBack,
        conversationInteraction = conversationInteraction,
        onCollapseComposer = { isExpanded = false },
    )

    AnsweringHapticEffect(
        generationSnapshot = selectedConversationGenerationSnapshot,
        topLevelPresentation = topLevelPresentation,
        hapticsEnabled = hapticsEnabled,
        haptics = haptics,
    )

    CompositionLocalProvider(LocalAgoraHaptics provides haptics) {
    ChatDrawerHost(
        state = drawerState,
        drawerEnabled = drawerEnabled,
        motionPolicy = motionPolicy,
        onDrawerProgress = { drawerProgress = it },
        drawerContent = { drawerWidth, onRequestClose ->
            ChatDrawerContent(
                viewModel = viewModel,
                drawerWidth = drawerWidth,
                scope = scope,
                inputFocusRequester = inputFocusRequester,
                onRequestClose = onRequestClose,
                onSettingsButtonTop = { settingsButtonTopDp = it },
                onOpenSettings = onOpenSettings,
                onOpenTasks = { onOpenTasks(null) },
                onRequestRename = dialogState::requestRename,
                onRequestDelete = { conversationId ->
                    if (!viewModel.isConversationDeleteLocked(conversationId)) {
                        dialogState.requestDelete(conversationId)
                    }
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clearFocusOnTap()
                .onSizeChanged { scrollCoordinator.recordViewportHeight(it.height) }
        ) {
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val (targetCa, targetQa) = if (!dark) {
                0.00f to 0.00f
            } else if (isNewChatMode) {
                0.20f to 0.10f
            } else {
                0.02f to 0.01f
            }
            val ca by animateFloatAsState(targetCa, tween(800))
            val qa by animateFloatAsState(targetQa, tween(800))
            val newChatMotion = newChatMotionPolicy(
                reduceMotion = reduceMotion,
                isNewChatMode = isNewChatMode,
                isLoading = isLoading,
                isSwitching = isSwitching,
                newChatEntryId = newChatEntryId,
            )
            if (!amoledEnabled) {
                AnimatedBlobBackground(
                    centerAlpha = ca,
                    quarterAlpha = qa,
                    blurRadius = 40f,
                    dark = dark,
                    blurEnabled = blurEffectsEnabled,
                    motionEnabled = newChatMotion.animateBackground,
                )
            }

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (!isNewChatMode || conversationSearchActive) {
                        ChatTopBar(
                        isNewChatMode = isNewChatMode,
                        conversations = displayConversations,
                        currentConversationId = currentConversationId,
                        currentConversationTitle = currentConversation?.title?.let {
                            replaceCustomProviderIdsForDisplay(it, customProviders)
                        },
                        totalTokens = contextUsage.estimatedTokenCount,
                        contextTokenBudget = contextUsage.tokenBudget,
                        searchActive = conversationSearchActive,
                        searchQuery = conversationSearchQuery,
                        searchMatchIndex = conversationSearchMatchIndex,
                        searchMatchCount = conversationSearchMatches.size,
                        conversationActionsEnabled =
                            !isNewChatMode && currentConversationId != null && !isLoading &&
                                !shareSelectionActive,
                        systemPromptEnabled = !conversationControls.lowContextModeEnabled,
                        onNavigateBack = onNavigateBack,
                        onOpenDrawer = {
                            if (drawerEnabled) {
                                scope.launch { drawerState.toggle(motionPolicy) }
                            }
                        },
                        onSearchQueryChange = { query ->
                            conversationInteraction.updateSearchQuery(query)
                        },
                        onSearchPrevious = {
                            if (conversationInteraction.previousSearchMatch()) {
                                haptics.selection()
                            }
                        },
                        onSearchNext = {
                            if (conversationInteraction.nextSearchMatch()) {
                                haptics.selection()
                            }
                        },
                        onSearchDismiss = {
                            conversationInteraction.dismissSearch()
                            focusManager.clearFocus()
                        },
                        onSearchClick = {
                            conversationInteraction.activateSearch()
                        },
                        onSystemPromptClick = dialogState::showPrompt,
                        onForkConversation = { pendingForkRequest = ForkConversationRequest(messageId = null) },
                        onShareConversation = {
                            conversationInteraction.dismissSearch()
                            focusManager.clearFocus()
                            conversationInteraction.activateShareSelection()
                        },
                        onExportConversation = { showExportDialog = true },
                        onLaunchGeminiLive = { showGeminiLiveDialog = true },
                        hasBackground = currentConversationId?.let { conversationSettings[it]?.backgroundImageUri }?.let { File(it).exists() } == true,
                        onGenerateBackground = { currentConversationId?.let { viewModel.generateBackground(it) } },
                        onRemoveBackground = { currentConversationId?.let { viewModel.removeBackground(it) } },
                        onGeneratePodcast = { currentConversationId?.let { viewModel.generatePodcast(it) } },
                        onNewChat = {
                            if (!isNewChatMode) {
                                isExpanded = false
                                viewModel.createNewChat()
                                inputFocusRequester.requestFocus()
                            }
                        },
                    )
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val topBarH = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
                    val pivotY =
                        ((windowHeightDp + topBarH.value / 2f - bottomBarHeight.value) / 2f)
                            .coerceAtLeast(0f) / windowHeightDp
                    AnimatedContent(
                        targetState = Pair(isNewChatMode, showLaunchContent),
                        transitionSpec = {
                            val targetNewChat = targetState.first
                            val targetShowLaunch = targetState.second
                            val initialNewChat = initialState.first
                            val initialShowLaunch = initialState.second

                            if (targetNewChat && (targetShowLaunch != initialShowLaunch || targetNewChat != initialNewChat)) {
                                val fadeInSpec = tween<Float>(500)
                                val enter = if (motionPolicy.allowSpatialTransitions) {
                                    val enterSpec = tween<Float>(
                                        700,
                                        easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f),
                                    )
                                    fadeIn(animationSpec = fadeInSpec) +
                                        scaleIn(
                                            initialScale = 0.6f,
                                            transformOrigin = TransformOrigin(0.5f, pivotY),
                                            animationSpec = enterSpec,
                                        )
                                } else {
                                    fadeIn(animationSpec = fadeInSpec)
                                }
                                enter
                                    .togetherWith(fadeOut(animationSpec = tween(300)))
                            } else if (!targetNewChat && !initialNewChat) {
                                // Switching between existing conversations: no animation
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                // Returning from new-chat to an existing conversation
                                fadeIn(animationSpec = tween(300))
                                    .togetherWith(fadeOut(animationSpec = tween(300)))
                            }
                        },
                        label = "MainContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { (targetNewChat, targetShowLaunch) ->
                        if (!targetNewChat) {
                            val streamingFollowAvailability = streamingTailAvailability(
                                generationActive = isLoading,
                                blocked =
                                    isStopping ||
                                        isSwitching ||
                                        conversationSearchActive ||
                                        shareSelectionActive ||
                                        !motionPolicy.allowProgrammaticScrollMotion,
                                programmaticHandoff =
                                    imeBottomAnchorState.active ||
                                        absoluteBottomScrollPhase.isActive ||
                                        animatedScrollRequest?.conversationId ==
                                            currentConversationId ||
                                        regenerationTransition?.conversationId ==
                                            currentConversationId,
                            )
                            Box(modifier = Modifier.fillMaxSize()) {
                                val currentBgPath = currentConversationId?.let { conversationSettings[it]?.backgroundImageUri }
                                if (!currentBgPath.isNullOrBlank()) {
                                    val bgFile = remember(currentBgPath) { File(currentBgPath) }
                                    if (bgFile.exists()) {
                                        val bgOpacity by viewModel.settings.backgroundGenOpacity.collectAsState()
                                        coil.compose.AsyncImage(
                                            model = bgFile,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    MaterialTheme.colorScheme.background.copy(
                                                        alpha = (1f - bgOpacity).coerceIn(0.25f, 0.95f)
                                                    )
                                                )
                                        )
                                    }
                                }
                                MessageList(
                                messages = StableMessageList(renderMessagesState.value),
                                authoritativeMessages = StableMessageList(displayMessagesState.value),
                                allMessages = StableMessageList(allMessagesState.value),
                                conversationId = currentConversationId,
                                modifier = Modifier.fillMaxSize().gradientBlur(
                                    blurAtTopDp = if (blurEffectsEnabled) 8f else 0f,
                                    blurAtBottomDp = 0f,
                                    fadeHeightDp = 40f,
                                    bottomOverlayHeight = bottomBarHeight + with(density) { outerSpacerHeightPx.toDp() } + 12.dp,
                                ),
                                state = listState,
                                // Per-conversation generation gate: isLoading mirrors the OPEN
                                // conversation's slot only (ConversationGenerationState.onActive
                                // gates on current == id), so message actions freeze while THIS
                                // conversation generates — background conversations don't affect it.
                                isLoading = isLoading,
                                isCompacting = isCompacting,
                                compactPreview = viewModel.compactPreview,
                                isStopping = isStopping,
                                isSwitching = isSwitching,
                                streamingMessage = generationSnapshot.streamingMessage?.forDisplay(customProviders),
                                streamingAutoFollowEnabled =
                                    streamingFollowAvailability.enabled && stickToBottom,
                                streamingAutoFollowPaused =
                                    streamingFollowAvailability.paused,
                                streamingTailWithinAttachThreshold =
                                    isWithinAbsoluteBottomAttachThreshold,
                                programmaticScrollActive =
                                    animatedScrollRequest?.conversationId ==
                                        currentConversationId,
                                streamingTailController = streamingTailController,
                                regenerationTransition = regenerationTransition,
                                onRegenerationFadeOutFinished =
                                    viewModel::acknowledgeRegenerationFade,
                                visualizeContextRollout = visualizeContextRollout && contextProjectionReady,
                                toolCallDisplayMode = toolCallDisplayMode,
                                thinkingSegmentDisplayMode = thinkingSegmentDisplayMode,
                                autoExpandActiveGroup = autoExpandActiveGroup,
                                parseInlineDollarMath = parseInlineDollarMath,
                                contextRetainedMessageIds = contextProjection.retainedMessageIds.orEmpty(),
                                modelAliases = StableModelAliases(modelAliases, modelProviderNames),
                                customProviders = customProviders,
                                bottomBarHeight = bottomBarHeight + shareSelectionBarSpace,
                                viewportHeight = viewportHeightPx,
                                messageHeights = messageHeights,
                                observeMessage = messageHydration.observeMessage,
                                onMessageHydrated = scrollCoordinator::recordMessageHydrated,
                                lifecycleAppearanceRegistry = messageLifecycleAppearanceRegistry,
                                lifecycleEntranceTargetMessageId = animatedScrollRequest
                                    ?.takeIf { it.conversationId == currentConversationId }
                                    ?.targetMessageId,
                                onEditMessage = { id, text ->
                                    val accepted = viewModel.editMessage(id, text)
                                    if (accepted) haptics.confirm()
                                    accepted
                                },
                                onSwitchBranch = { parentId, currentMessageId, direction ->
                                    haptics.selection()
                                    viewModel.switchBranch(parentId, currentMessageId, direction)
                                },
                                onRegenerate = { id ->
                                    val accepted = viewModel.regenerate(id)
                                    if (accepted) haptics.confirm()
                                    accepted
                                },
                                onFork = { id -> pendingForkRequest = ForkConversationRequest(messageId = id) },
                                onShare = { id ->
                                    viewModel.shareGeneration(id)
                                },
                                onRecompact = { id ->
                                    viewModel.startContextRecompact(id)
                                },
                                onDelete = { id, result ->
                                    viewModel.deleteMessage(id, result) > 0
                                },
                                onDeleteConversation = { expectedIds, result ->
                                    currentConversationId?.let { id ->
                                        viewModel.deleteConversation(id, expectedIds, result)
                                    } ?: false
                                },
                                searchQuery = if (conversationSearchActive) {
                                    conversationSearchQuery
                                } else {
                                    ""
                                },
                                activeSearchMatch = conversationSearchMatches
                                    .getOrNull(conversationSearchMatchIndex),
                                onSearchMatchDistance =
                                    conversationInteraction::recordSearchMatchDistance,
                                onSearchTurnsChanged = conversationInteraction::recordSearchTurns,
                                selectionMode = shareSelectionActive,
                                selectedMessageIds = selectedShareMessageIds,
                                onToggleMessageSelection = { messageId ->
                                    haptics.selection()
                                    conversationInteraction.toggleShareMessage(messageId)
                                },
                                onMediaClick = { urls, index ->
                                    onMediaClick(urls, index)
                                },
                                onFileContentClick = onFileContentClick?.let { open ->
                                    { name, content ->
                                        open(name, content)
                                    }
                                },
                                onPdfPagesClick = { pages, idx ->
                                    onPdfPagesClick?.invoke(pages, idx)
                                },
                                thoughtExpandedStates = thoughtExpandedStates,
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = 140.dp,
                                    bottom = bottomBarHeight + shareSelectionBarSpace + 8.dp
                                )
                            )
                            }
                        } else if (targetShowLaunch) {
                            var activeTab by rememberSaveable { mutableStateOf(FullFlowTab.HOME) }

                            Scaffold(
                                containerColor = Color.Transparent,
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = Color(0xFF0F1216),
                                        tonalElevation = 0.dp,
                                        modifier = Modifier.height(72.dp)
                                    ) {
                                        NavigationBarItem(
                                            selected = activeTab == FullFlowTab.HOME,
                                            onClick = { activeTab = FullFlowTab.HOME },
                                            icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
                                            label = { Text("Accueil", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = FullLiveBridge.Cyan,
                                                selectedTextColor = FullLiveBridge.Cyan,
                                                unselectedIconColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                unselectedTextColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                indicatorColor = Color.Transparent
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = activeTab == FullFlowTab.RECHERCHE,
                                            onClick = { activeTab = FullFlowTab.RECHERCHE },
                                            icon = { Icon(Icons.Default.Search, contentDescription = "Recherche") },
                                            label = { Text("Recherche", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = FullLiveBridge.Cyan,
                                                selectedTextColor = FullLiveBridge.Cyan,
                                                unselectedIconColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                unselectedTextColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                indicatorColor = Color.Transparent
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = activeTab == FullFlowTab.PODCAST,
                                            onClick = { activeTab = FullFlowTab.PODCAST },
                                            icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Audio") },
                                            label = { Text("Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = FullLiveBridge.Cyan,
                                                selectedTextColor = FullLiveBridge.Cyan,
                                                unselectedIconColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                unselectedTextColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                indicatorColor = Color.Transparent
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = activeTab == FullFlowTab.IMAGE,
                                            onClick = { activeTab = FullFlowTab.IMAGE },
                                            icon = { Icon(Icons.Default.Image, contentDescription = "Images") },
                                            label = { Text("Images", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = FullLiveBridge.Cyan,
                                                selectedTextColor = FullLiveBridge.Cyan,
                                                unselectedIconColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                unselectedTextColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                indicatorColor = Color.Transparent
                                            )
                                        )
                                        NavigationBarItem(
                                            selected = activeTab == FullFlowTab.VIDEO,
                                            onClick = { activeTab = FullFlowTab.VIDEO },
                                            icon = { Icon(Icons.Default.Videocam, contentDescription = "Vidéos") },
                                            label = { Text("Vidéos", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = FullLiveBridge.Cyan,
                                                selectedTextColor = FullLiveBridge.Cyan,
                                                unselectedIconColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                unselectedTextColor = Color.White.copy(alpha = AgoraAlpha.Hint),
                                                indicatorColor = Color.Transparent
                                            )
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = innerPadding.calculateBottomPadding())
                                ) {
                                    when (activeTab) {
                                        FullFlowTab.HOME -> {
                                            com.newoether.agora.ui.chat.fullflow.FullFlowHomeHost(
                                                isNewChatMode = true,
                                                conversationsCount = displayConversations.size,
                                                conversations = displayConversations,
                                                viewModel = viewModel,
                                                onOpenSearch = { conversationInteraction.activateSearch() },
                                                onOpenDrawer = {
                                                    if (drawerEnabled) scope.launch { drawerState.toggle(motionPolicy) }
                                                },
                                                onActivateComposer = { text ->
                                                    if (!text.isNullOrEmpty()) {
                                                        textFieldState.edit {
                                                            replace(0, length, text)
                                                        }
                                                    }
                                                    inputFocusRequester.requestFocus()
                                                },
                                                onSendTask = { text ->
                                                    val submitted = viewModel.conversationComposerSubmission.submit(
                                                        ownerId = composerOwnerId,
                                                        text = text,
                                                        attachmentIds = emptyList(),
                                                    )
                                                    if (submitted) {
                                                        com.newoether.agora.ui.common.SoundIdentity.playSound(context, com.newoether.agora.ui.common.SoundIdentity.SoundType.SEND)
                                                    }
                                                    if (!submitted && !viewModel.conversationComposerSubmission.isFrozen(composerOwnerId)) {
                                                        textFieldState.edit { replace(0, length, text) }
                                                        inputFocusRequester.requestFocus()
                                                    }
                                                },
                                                onResetHome = { viewModel.createNewChat() },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        FullFlowTab.RECHERCHE -> {
                                            com.newoether.agora.ui.webresearch.ResearchScreen(
                                                viewModel = viewModel,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        FullFlowTab.PODCAST -> {
                                            com.newoether.agora.studio.reader.PodcastAndReaderScreen(
                                                viewModel = viewModel,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        FullFlowTab.IMAGE -> {
                                            com.newoether.agora.studio.image.GeminiImageStudioScreen(
                                                onClose = { activeTab = FullFlowTab.HOME },
                                                onInsertToChat = { image ->
                                                    com.newoether.agora.studio.image.GeminiImageStudioController.sendToChat(image)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        FullFlowTab.VIDEO -> {
                                            com.newoether.agora.studio.video.GeminiVideoStudioScreen(
                                                onClose = { activeTab = FullFlowTab.HOME },
                                                onInsertToChat = { video ->
                                                    com.newoether.agora.studio.video.GeminiVideoStudioController.sendToChat(video)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }

                    // Recreate for every plain value captured by this derived state.
                    val regenerationScrollActive =
                        regenerationTransition?.conversationId == currentConversationId &&
                            regenerationTransition?.scrollFinished == false
                    val showButton by remember(
                        currentConversationId,
                        loadedMessagesConversationId,
                        isNewChatMode,
                        isSwitching,
                        shareSelectionActive,
                        isNearAbsoluteBottom,
                        absoluteBottomScrollPhase,
                        listState,
                        streamingTailController,
                        regenerationScrollActive,
                        imeBottomAnchorState.active,
                    ) {
                        derivedStateOf {
                            val totalItemsCount = listState.layoutInfo.totalItemsCount
                            shouldShowAbsoluteBottomButton(
                                isNewChatMode = isNewChatMode,
                                isSwitching = isSwitching,
                                conversationContentReady =
                                    currentConversationId != null &&
                                        loadedMessagesConversationId == currentConversationId,
                                shareSelectionActive = shareSelectionActive,
                                hasItems = totalItemsCount > 1,
                                canScrollForward = listState.canScrollForward,
                                isNearBottom = isNearAbsoluteBottom,
                                isStreamingAutoFollowing =
                                    streamingTailController.isAutoFollowing,
                                scrollPhase = absoluteBottomScrollPhase,
                                competingProgrammaticScrollActive =
                                    regenerationScrollActive ||
                                        imeBottomAnchorState.active,
                            )
                        }
                    }
                    val fabElevation by animateDpAsState(
                        targetValue = if (showButton) 4.dp else 0.dp,
                        animationSpec = if (motionPolicy.allowSpatialTransitions) {
                            tween(400)
                        } else {
                            snap()
                        }
                    )
                    AnimatedVisibility(
                        visible = showButton,
                        enter = if (motionPolicy.allowSpatialTransitions) {
                            fadeIn(tween(400)) +
                                scaleIn(initialScale = 0.6f, animationSpec = tween(400))
                        } else {
                            fadeIn(tween(400))
                        },
                        exit = if (motionPolicy.allowSpatialTransitions) {
                            fadeOut(tween(400)) +
                                scaleOut(targetScale = 0.6f, animationSpec = tween(400))
                        } else {
                            fadeOut(tween(400))
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBarHeight + 8.dp)
                    ) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            FloatingActionButton(onClick = {
                                scrollCoordinator.requestAbsoluteBottomScroll()
                            }, containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp), contentColor = MaterialTheme.colorScheme.onSurface, shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(fabElevation), modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.scroll_to_bottom), modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = shareSelectionActive,
                        enter = if (motionPolicy.allowSpatialTransitions) {
                            fadeIn(tween(220)) + scaleIn(
                                initialScale = 0.86f,
                                animationSpec = tween(220),
                            )
                        } else {
                            fadeIn(tween(220))
                        },
                        exit = if (motionPolicy.allowSpatialTransitions) {
                            fadeOut(tween(180)) + scaleOut(
                                targetScale = 0.86f,
                                animationSpec = tween(180),
                            )
                        } else {
                            fadeOut(tween(180))
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomBarHeight + 10.dp),
                    ) {
                        ShareSelectionFab(
                            allSelected = selectableShareMessageIds.isNotEmpty() &&
                                selectedShareMessageIds.containsAll(selectableShareMessageIds),
                            hasSelection = selectedShareMessageIds.isNotEmpty(),
                            onDismiss = {
                                conversationInteraction.dismissShareSelection()
                            },
                            onToggleAll = {
                                haptics.selection()
                                conversationInteraction.toggleAllShareMessages()
                            },
                            onConfirm = {
                                if (selectedShareMessageIds.isNotEmpty()) {
                                    showExportDialog = true
                                }
                            },
                        )
                    }

                    AnimatedVisibility(
                        visible = isSwitching && !isTransitioningToNewChat,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            val expandedGradientTopPaddingPx = with(density) { 20.dp.toPx() }
            val gradientWidthPx = with(density) { 40.dp.toPx() }
            val bgColor = MaterialTheme.colorScheme.background
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(if (isExpanded) Modifier.fillMaxHeight().statusBarsPadding() else Modifier)
                    .drawBehind {
                        val totalH = size.height
                        if (isExpanded && totalH > 0f) {
                            val h = expandedGradientTopPaddingPx.coerceAtMost(totalH * 0.12f)
                            val w = gradientWidthPx.coerceAtMost(totalH * 0.24f)
                            val transparentEnd = h / totalH
                            val fadeEnd = (h + w) / totalH
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to bgColor.copy(alpha = 0f),
                                        transparentEnd to bgColor.copy(alpha = 0f),
                                        fadeEnd to bgColor,
                                    ),
                                    startY = 0f,
                                    endY = totalH
                                )
                            )
                        }
                    },
                color = Color.Transparent
            ) {
                Column {
                    if (!isExpanded) Spacer(modifier = Modifier.height(12.dp))
                    if (outerSpacerHeightPx > 0f) {
                        Spacer(modifier = Modifier.height(with(density) { outerSpacerHeightPx.toDp() }))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isExpanded) Modifier.fillMaxHeight() else Modifier)
                            .onSizeChanged {
                                if (!isExpanded) bottomBarHeightPx = it.height.toFloat()
                            }
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(8.dp),
                    ) {
                        // This is a sibling behind the complete outer bar, not a child of the
                        // composer. Its lower overflow is therefore occluded by the 28dp Surface
                        // and shadow below.
                        LoopStatusBackdrop(
                            loop = currentLoop,
                            isRunning = currentConversationId in runningLoopIds,
                            onStop = { viewModel.stopCurrentLoop() },
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isExpanded) Modifier.weight(1f) else Modifier),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 8.dp,
                            shape = CHAT_BOTTOM_BAR_OUTER_SHAPE,
                        ) {
                            Box(
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                ChatBottomBar(
                        submissionController = viewModel.conversationComposerSubmission,
                        composerOwnerId = composerOwnerId,
                        composerController = viewModel.conversationComposer,
                        composerSnapshot = composerSnapshot,
                        onStopGeneration = { haptics.interrupt(); viewModel.stopGeneration() },
                        isLoading = isLoading,
                        isCompacting = isCompacting,
                        isSwitching = isSwitching,
                        enabledModels = chatEnabledModels,
                        selectedModel = selectedModel,
                        modelAliases = chatModelAliases,
                        modelProviderNames = modelProviderNames,
                        customProviders = customProviders,
                        codeExecutionEnabled = conversationControls.codeExecutionEnabled,
                        googleSearchEnabled = conversationControls.googleSearchEnabled,
                        thinkingEnabled = conversationControls.thinkingEnabled,
                        thinkingLevel = conversationControls.thinkingLevel,
                        thinkingBudgetEnabled = conversationControls.thinkingBudgetEnabled,
                        thinkingBudgetTokens = conversationControls.thinkingBudgetTokens,
                        openAiWebSearchAvailable = conversationControls.openAiWebSearchAvailable,
                        openAiWebSearchEnabled = conversationControls.openAiWebSearchEnabled,
                        onOpenAiWebSearchToggle = { enabled -> updateOpenAiNativeSearch(viewModel, conversationControls.settingsOwnerId, haptics, enabled) },
                        openAiServiceTierAvailable = conversationControls.openAiServiceTierState.available,
                        openAiServiceTierEnabled = conversationControls.openAiServiceTierState.enabled,
                        openAiServiceTier = conversationControls.openAiServiceTierState.tier,
                        onOpenAiServiceTierToggle = { enabled -> updateOpenAiConversationServiceTierEnabled(viewModel, conversationControls.settingsOwnerId, haptics, enabled) },
                        onOpenAiServiceTierChange = { tier -> updateOpenAiConversationServiceTier(viewModel, conversationControls.settingsOwnerId, haptics, tier) },
                        onCodeExecutionToggle = { enabled -> haptics.toggle(enabled); viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(codeExecutionEnabled = enabled) } },
                        onGoogleSearchToggle = { enabled -> haptics.toggle(enabled); viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(googleSearchEnabled = enabled) } },
                        onThinkingToggle = { enabled -> haptics.toggle(enabled); viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(thinkingEnabled = enabled) } },
                        onThinkingLevelChange = { level -> viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(thinkingLevel = level) } },
                        onThinkingBudgetEnabledChange = { enabled -> haptics.toggle(enabled); viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(thinkingBudgetEnabled = enabled) } },
                        onThinkingBudgetTokensChange = { tokens -> viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(thinkingBudgetTokens = tokens) } },
                        webSearchEnabled = conversationControls.webSearchEnabled,
                        onWebSearchToggle = { enabled -> haptics.toggle(enabled); viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(webSearchEnabled = enabled) } },
                        shellEnabled = conversationControls.shellEnabled,
                        onShellToggle = { enabled -> haptics.toggle(enabled); viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(shellEnabled = enabled) } },
                        showLowContextMode = conversationControls.showLowContextMode,
                        lowContextModeEnabled = conversationControls.lowContextModeEnabled,
                        onLowContextModeToggle = { enabled ->
                            haptics.toggle(enabled)
                            viewModel.updateConversationSetting(conversationControls.settingsOwnerId) { it.copy(lowContextModeEnabled = enabled) }
                        },
                        // The model row owns its selection tick. Repeating it here produced the
                        // previous double buzz for one physical tap.
                        onModelSelect = { viewModel.setActiveModel(it) },
                        onAllMediaClick = { urls, idx -> onMediaClick(urls, idx) },
                        onFileContentClick = { name, content -> viewModel.showFilePreview(name, content) },
                        modifier = Modifier,
                        textFieldState = textFieldState,
                        composerState = composer,
                        focusRequester = inputFocusRequester,
                        onInputFocusChanged = { focused ->
                            scrollCoordinator.setComposerInputFocused(focused)
                        },
                        isExpanded = isExpanded,
                        isExpandAnimating = isExpandAnimating,
                        onCollapse = { isExpanded = false },
                        onExpand = { isExpanded = true },
                        showWebSearch = conversationControls.webSearchAvailable,
                        showShell = shellDevices.isNotEmpty() && conversationControls.shellAvailable,
                        onPdfPagesClick = { pages, idx -> onPdfPagesClick?.invoke(pages, idx) },
                        onPdfPreviewSelect = { pages, idx -> onPdfPreviewSelect?.invoke(pages, idx) },
                        pdfViewerSelection = pdfViewerSelection,
                        onTogglePdfSelection = onTogglePdfSelection,
                        onInitPdfSelection = onInitPdfSelection,
                        fullScreenViewerUrls = fullScreenViewerUrls,
                        compactDefaultModel = compactModel,
                        compactDefaultPrompt = compactPrompt,
                        compactDefaultRetainCount = compactRetainCount,
                        contextEstimatedTokens = contextUsage.estimatedTokenCount,
                        contextTokenBudget = contextUsage.tokenBudget,
                        contextCompactThresholdPercent = compactThresholdPercent,
                        canCompact = currentConversationId != null && !isLoading && !isSwitching && !isStopping,
                        onCompactClick = {
                            dialogState.showManualCompact()
                        },
                        onAdvancedClick = dialogState::showAdvanced,
                        queuedSends = queuedSends,
                        onRemoveQueuedSend = viewModel::removeQueuedSend,
                        isStopping = isStopping,
                        onLaunchGeminiLive = { showGeminiLiveDialog = true },
                    )
                            }
                        }
                    }
                }
            }
            }
        }
        }

    ChatAppDialogHost(
        state = dialogState,
        viewModel = viewModel,
        haptics = haptics,
        compactModel = compactModel,
        selectedModel = selectedModel,
        compactPrompt = compactPrompt,
        compactRetainCount = compactRetainCount,
        enabledModels = chatEnabledModels,
        modelAliases = chatModelAliases,
        customProviders = customProviders,
        modelProviderNames = modelProviderNames,
        isCompacting = isCompacting,
        geminiLiveVisible = showGeminiLiveDialog,
        onDismissGeminiLive = { showGeminiLiveDialog = false },
        fullFlowGenMailVisible = showGenMailDialog || genMailFromController,
        onDismissFullFlowGenMail = {
            showGenMailDialog = false
            com.newoether.agora.ui.chat.fullflow.GenMailController.closeGenMail()
        },
        onGenerateMail = { inputFocusRequester.requestFocus() },
        fullFlowPlusVisible = showPlusSheet,
        onDismissFullFlowPlus = { showPlusSheet = false },
        onLaunchGeminiLiveFromPlus = { showGeminiLiveDialog = true },
        onNewChatFromPlus = { viewModel.createNewChat() },
        onOpenTasksFromPlus = { onOpenTasks(null) },
        onOpenSettingsFromPlus = onOpenSettings,
        composerOwnerId = composerOwnerId,
    )

    com.newoether.agora.ui.chat.audio.FullFlowAudioHost(viewModel, textFieldState, inputFocusRequester) { showGenMailDialog = true }
    com.newoether.agora.wand.WandInputBarHost(
        textFieldState = textFieldState,
        settings = viewModel.settings,
        skillManager = viewModel.skillManager,
    )
    // Post-response Magic Wand: reformulate/amplify any existing message text.
    com.newoether.agora.wand.WandMessagePickerHost(
        sourceText = com.newoether.agora.wand.WandMessageTrigger.pendingSourceText,
        onDismiss = { com.newoether.agora.wand.WandMessageTrigger.clear() },
    )
    ChatForkConfirmationHost(pendingForkRequest, viewModel) { pendingForkRequest = null }

    DisposableEffect(composerOwnerId) {
        com.newoether.agora.ui.chat.message.FollowUpSuggestionController.onSuggestionSelected = { suggestion ->
            textFieldState.edit {
                replace(0, length, suggestion)
            }
            inputFocusRequester.requestFocus()
        }
        com.newoether.agora.ui.chat.message.FollowUpSuggestionController.onSuggestionSend = { suggestion ->
            val submitted = viewModel.conversationComposerSubmission.submit(
                ownerId = composerOwnerId,
                text = suggestion,
                attachmentIds = emptyList(),
            )
            if (!submitted && !viewModel.conversationComposerSubmission.isFrozen(composerOwnerId)) {
                textFieldState.edit { replace(0, length, suggestion) }
                inputFocusRequester.requestFocus()
            }
        }
        onDispose {
            com.newoether.agora.ui.chat.message.FollowUpSuggestionController.onSuggestionSelected = null
            com.newoether.agora.ui.chat.message.FollowUpSuggestionController.onSuggestionSend = null
        }
    }

    // ── Save to Second Brain: when the user taps the bookmark on a message,
    // the text is saved as a Markdown note in the vault. ──
    val brainSaveText = com.newoether.agora.tool.BrainSaveController.pendingSave
    val brainContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(brainSaveText) {
        if (brainSaveText != null) {
            val container = runCatching {
                (brainContext.applicationContext as? com.newoether.agora.AgoraApplication)?.requireContainer()
            }.getOrNull()
            if (container != null) {
                val db = container.database
                val repo = com.newoether.agora.data.notes.NoteRepository(
                    noteDao = db.noteDao(),
                    taskDao = db.obsidianTaskDao(),
                    embeddingDao = db.noteEmbeddingDao(),
                    notesDir = java.io.File(brainContext.filesDir, "notes"),
                    generateEmbedding = { text ->
                        val generator = com.newoether.agora.service.BrainEmbeddingService.getGenerator(brainContext)
                        generator(text)
                    },
                )
                val timestamp = System.currentTimeMillis()
                val title = brainSaveText.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: "Saved note"
                val filePath = "Chat/Saved_${timestamp}.md"
                val content = "# $title\n\n$brainSaveText"
                repo.saveNote(filePath, content)
                com.newoether.agora.ui.common.SoundIdentity.playSound(brainContext, com.newoether.agora.ui.common.SoundIdentity.SoundType.BOOKMARK)
                android.widget.Toast.makeText(brainContext, "Sauvé dans le Second Cerveau", android.widget.Toast.LENGTH_SHORT).show()
            }
            com.newoether.agora.tool.BrainSaveController.clear()
        }
    }

    val isMeshStudioOpen by com.newoether.agora.mesh.MeshController.isStudioOpen.collectAsState()
    if (isMeshStudioOpen) {
        com.newoether.agora.mesh.ui.MeshStudioDialog(
            onDismissRequest = { com.newoether.agora.mesh.MeshController.closeStudio() }
        )
    }

    if (showExportDialog) {
        var selectedFormat by remember { mutableStateOf(ExportFormat.TXT) }
        var exportScopeSelected by remember(selectedShareMessageIds) {
            mutableStateOf(selectedShareMessageIds.isNotEmpty())
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Exporter la conversation",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Périmètre :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = !exportScopeSelected,
                            onClick = { exportScopeSelected = false },
                            label = { Text("Tous les messages") }
                        )

                        val hasSelected = selectedShareMessageIds.isNotEmpty()
                        androidx.compose.material3.FilterChip(
                            selected = exportScopeSelected,
                            onClick = {
                                if (hasSelected) {
                                    exportScopeSelected = true
                                } else {
                                    showExportDialog = false
                                    conversationInteraction.activateShareSelection()
                                }
                            },
                            label = {
                                Text(if (hasSelected) "${selectedShareMessageIds.size} sélectionné(s)" else "Sélectionner...")
                            }
                        )
                    }

                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    Text(
                        text = "Format d'exportation :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    
                    ExportFormat.values().forEach { format ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFormat = format }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = (selectedFormat == format),
                                onClick = { selectedFormat = format }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = format.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                val description = when (format) {
                                    ExportFormat.TXT -> "Format texte brut universel, parfait pour un archivage simple."
                                    ExportFormat.MD -> "Format Markdown structuré, idéal pour Notion, Obsidian ou GitHub."
                                    ExportFormat.HTML -> "Page web stylisée interactive, prête à être lue sur n'importe quel navigateur."
                                    ExportFormat.JSON -> "Format structuré JSON, destiné aux développeurs et à l'analyse de données."
                                }
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showExportDialog = false
                        val convId = currentConversationId ?: return@Button
                        val targetSelection = if (exportScopeSelected && selectedShareMessageIds.isNotEmpty()) {
                            selectedShareMessageIds
                        } else null
                        if (exportScopeSelected) {
                            conversationInteraction.dismissShareSelection()
                        }
                        scope.launch {
                            val exportMessages = viewModel.getExportableMessages(
                                conversationId = convId,
                                selectedMessageIds = targetSelection
                            )
                            ConversationExporter.export(
                                context = context,
                                messages = exportMessages,
                                format = selectedFormat,
                                conversationTitle = currentConversation?.title
                            )
                        }
                    }
                ) {
                    Text("Exporter")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showExportDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

enum class FullFlowTab {
    HOME,
    RECHERCHE,
    PODCAST,
    IMAGE,
    VIDEO
}
