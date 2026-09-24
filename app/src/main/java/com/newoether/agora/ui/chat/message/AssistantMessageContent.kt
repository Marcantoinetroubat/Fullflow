@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.newoether.agora.ui.chat.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraSpacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.util.noOpBringIntoView
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.CitationPolicy
import com.newoether.agora.model.CitationRecord
import com.newoether.agora.model.MessageSegment
import com.newoether.agora.model.MessageStatus
import com.newoether.agora.model.Participant
import com.newoether.agora.model.TokenUsage
import com.newoether.agora.model.ToolCallDisplayModes
import com.newoether.agora.model.ThinkingSegmentDisplayModes
import com.newoether.agora.model.citationRecords
import com.newoether.agora.ui.chat.GenerationActivityDot
import com.newoether.agora.ui.chat.StreamingTailAnchorHeight
import com.newoether.agora.ui.chat.shouldShowStreamingTailIndicator
import com.newoether.agora.ui.common.LocalAgoraHaptics
import com.newoether.agora.ui.theme.ChatType

internal val AssistantMessageHorizontalInset = 8.dp
private val FormerAssistantStatusSpacerHeight = 6.dp
private val AssistantInlineActivityHeight = StreamingTailAnchorHeight

internal data class TokenUsagePresentation(
    val input: Int?,
    val cachedInput: Int?,
    val output: Int?,
)

internal fun tokenUsagePresentation(
    usage: TokenUsage?,
): TokenUsagePresentation {
    if (usage == null) return TokenUsagePresentation(null, null, null)
    val input = usage.inputTokenCount
        ?: if (
            usage.cachedInputTokenCount != null &&
            usage.uncachedInputTokenCount != null
        ) {
            TokenUsage.addCounts(
                usage.cachedInputTokenCount,
                usage.uncachedInputTokenCount,
            )
        } else {
            usage.outputTokenCount
                ?.let { output -> (usage.totalTokenCount - output).takeIf { it >= 0 } }
        }
    val output = usage.outputTokenCount
        ?: input?.let { inputCount ->
            (usage.totalTokenCount - inputCount).takeIf { it >= 0 }
        }
    return TokenUsagePresentation(
        input = input,
        cachedInput = usage.cachedInputTokenCount,
        output = output,
    )
}

internal enum class AssistantInlineActivityMode {
    NONE,
    EMPTY,
    RETRY,
}

internal fun assistantInlineActivityMode(
    generationActive: Boolean,
    hasAnswer: Boolean,
    hasVisibleInfoSegment: Boolean,
    retryText: String?,
): AssistantInlineActivityMode = when {
    !generationActive -> AssistantInlineActivityMode.NONE
    !retryText.isNullOrBlank() -> AssistantInlineActivityMode.RETRY
    !hasAnswer && !hasVisibleInfoSegment -> AssistantInlineActivityMode.EMPTY
    else -> AssistantInlineActivityMode.NONE
}

internal data class AssistantInlineActivityPresentation(
    val mode: AssistantInlineActivityMode,
    val retainLayout: Boolean,
)

internal fun assistantInlineActivityPresentation(
    generationActive: Boolean,
    isStopping: Boolean,
    hasAnswer: Boolean,
    hasVisibleInfoSegment: Boolean,
    retryText: String?,
): AssistantInlineActivityPresentation {
    val ownedMode = assistantInlineActivityMode(
        generationActive,
        hasAnswer,
        hasVisibleInfoSegment,
        retryText,
    )
    return AssistantInlineActivityPresentation(
        mode = if (isStopping) AssistantInlineActivityMode.NONE else ownedMode,
        retainLayout = isStopping && ownedMode != AssistantInlineActivityMode.NONE,
    )
}

@Composable
private fun AssistantInlineActivity(
    mode: AssistantInlineActivityMode,
    retryText: String?,
    visibilityTransition: Transition<Boolean>,
    activityOpacity: Float,
    retainExitLayout: Boolean,
    terminalText: String?,
    terminalIsError: Boolean,
    terminalShowLocalContextHelp: Boolean,
    precededByCard: Boolean,
) {
    var retainedMode by remember {
        mutableStateOf(
            mode.takeUnless { it == AssistantInlineActivityMode.NONE }
                ?: AssistantInlineActivityMode.EMPTY,
        )
    }
    var retainedRetryText by remember { mutableStateOf(retryText) }
    LaunchedEffect(mode, retryText) {
        if (mode != AssistantInlineActivityMode.NONE) {
            retainedMode = mode
            retainedRetryText = retryText
        }
    }
    val activityVisible = visibilityTransition.targetState
    val ownsCurrentActivity = activityVisible && mode != AssistantInlineActivityMode.NONE
    val visibleMode = if (ownsCurrentActivity) mode else retainedMode
    val visibleRetryText = if (ownsCurrentActivity) retryText else retainedRetryText
    if (visibilityTransition.targetState || retainExitLayout || terminalText != null) {
        Box(
            modifier = Modifier
                .padding(top = if (precededByCard) 12.dp else 0.dp)
                .heightIn(min = AssistantInlineActivityHeight),
        ) {
            Crossfade(
                targetState = terminalText,
                animationSpec = tween(durationMillis = 180, easing = LinearEasing),
                label = "AssistantInlineTerminalTransition",
            ) { visibleTerminalText ->
                if (visibleTerminalText == null) {
                    Row(
                        modifier = Modifier.graphicsLayer {
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            // Crossfade exclusively owns alpha after the terminal handoff begins.
                            alpha = if (terminalText == null) activityOpacity else 1f
                            clip = false
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (visibleMode == AssistantInlineActivityMode.RETRY) {
                            RetryActivityIndicator(label = visibleRetryText.orEmpty() + "...")
                        } else {
                            GenerationActivityDot()
                        }
                    }
                } else if (terminalIsError) {
                    GenerationErrorBar(
                        errorText = visibleTerminalText,
                        showLocalContextHelp = terminalShowLocalContextHelp,
                        topPadding = 0.dp,
                    )
                } else {
                    GenerationTerminalText(visibleTerminalText)
                }
            }
        }
    }
}


/**
 * The left-aligned assistant (and error) message content: the streaming status header,
 * the thinking / tool-call timeline or compact segment block, the debounced markdown
 * body, any generated images, the stopped indicator, and the regenerate/overflow
 * action row.
 *
 * Extracted from [MessageItem]. The parent owns the reported-height bookkeeping and the
 * segment-detail sheet, so this composable reports the thought block height through
 * [setThoughtBlockHeight] and surfaces clicked segments through [onSegmentSelected].
 */
@Composable
internal fun AssistantMessageContent(
    message: ChatMessage,
    segmentAppearanceRegistry: SegmentAppearanceRegistry,
    contextAlpha: Modifier,
    isStreaming: Boolean,
    isLoading: Boolean,
    isStopping: Boolean,
    isRegenerationExiting: Boolean,
    isEditingAllowed: Boolean,
    showActions: Boolean,
    actionCopyText: String?,
    showBranchSelector: Boolean,
    toolCallDisplayMode: String,
    thinkingSegmentDisplayMode: String,
    autoExpandActiveGroup: Boolean,

    groupedSegmentAutoExpansionController: GroupedSegmentAutoExpansionController,
    thoughtExpandedStates: SnapshotStateMap<String, Boolean>,
    renderContext: ChatMarkdownRenderContext,
    searchHighlight: SearchHighlightSpec?,
    branchIndex: Int,
    totalBranches: Int,
    onSwitchBranch: (Int) -> Unit,
    onRegenerate: (String) -> Boolean,
    onFork: () -> Unit,
    onShare: () -> Unit,
    onMediaClick: (List<String>, Int) -> Unit,
    onShowInfo: () -> Unit,
    onShowDelete: () -> Unit,
    onSegmentSelected: (List<Int>, Boolean) -> Unit,
    onLayoutMutationStarted: (String) -> Unit,
    onLayoutMutationSettled: (String) -> Unit,
    setThoughtBlockHeight: (Int) -> Unit,
    userPrompt: String? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember(context) { context.applicationContext as? com.newoether.agora.AgoraApplication }
    val container = remember(app) { app?.requireContainer() }
    val settingsRepo = remember(container) { container?.settingsRepository }
    val editorialEnabled = settingsRepo?.editorialEnabled?.collectAsState()?.value ?: false

    // Chime only on the true→false streaming transition (never on first composition,
    // otherwise every already-completed message would ring when opening a conversation).
    var wasStreaming by remember(message.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isStreaming) {
        if (wasStreaming == true && !isStreaming && message.text.isNotBlank()) {
            com.newoether.agora.ui.common.SoundIdentity.playSound(context, com.newoether.agora.ui.common.SoundIdentity.SoundType.RECEIVE)
        }
        wasStreaming = isStreaming
    }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalAgoraHaptics.current
    val uriHandler = LocalUriHandler.current
    val citations = remember(message.text, message.segments) {
        message.citationRecords()
    }
    var selectedCitation by remember(message.id) { mutableStateOf<CitationRecord?>(null) }
    var showCitationSources by remember(message.id) { mutableStateOf(false) }
    var groupedCitationSources by remember(message.id) {
        mutableStateOf<List<CitationRecord>?>(null)
    }
    val onSingleCitationActivate: (CitationRecord) -> Unit = { source ->
        val safeUrl = CitationPolicy.safeHttpUrl(source.url)
        if (safeUrl == null || runCatching { uriHandler.openUri(safeUrl) }.isFailure) {
            selectedCitation = source
        }
    }
    val onCitationActivate: (List<CitationRecord>) -> Unit = { sources ->
        if (sources.size > 1) {
            showCitationSources = false
            groupedCitationSources = sources
        } else {
            sources.singleOrNull()?.let(onSingleCitationActivate)
        }
    }
    selectedCitation?.let { source ->
        CitationSourceDetailDialog(
            source = source,
            onDismiss = { selectedCitation = null },
        )
    }
    var showMenu by remember(message.id) { mutableStateOf(false) }
    var regenerateRequested by remember(message.id) { mutableStateOf(false) }
    var observedRegenerationExit by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(isRegenerationExiting) {
        if (isRegenerationExiting) {
            observedRegenerationExit = true
        } else if (observedRegenerationExit) {
            // An aborted transition keeps the old answer composed. Restore its controls only
            // after the externally-owned regeneration state has genuinely ended.
            regenerateRequested = false
            observedRegenerationExit = false
        }
    }
    val regenerationActionsExiting = regenerateRequested || isRegenerationExiting
    val actionAvailability = assistantActionAvailability(
        isStreaming = isStreaming,
        isLoading = isLoading,
        regenerateRequested = regenerationActionsExiting,
    )
    val sourcesSummaryVisible = citationSummaryVisible(
        showActions = showActions,
        informationVisible = actionAvailability.informationVisible,
        sourceCount = citations.size,
    )
    LaunchedEffect(regenerationActionsExiting, sourcesSummaryVisible) {
        if (regenerationActionsExiting) showMenu = false
        if (!sourcesSummaryVisible) showCitationSources = false
    }
    if (showCitationSources) {
        CitationSourcesBottomSheet(
            messageId = message.id,
            citations = citations,
            searchSpec = null,
            onActivate = { source ->
                haptics.confirm()
                onSingleCitationActivate(source)
            },
            onDismiss = { showCitationSources = false },
        )
    }
    groupedCitationSources?.let { groupedSources ->
        CitationSourcesBottomSheet(
            messageId = message.id,
            citations = groupedSources,
            searchSpec = null,
            onActivate = { source ->
                haptics.confirm()
                onSingleCitationActivate(source)
            },
            onDismiss = { groupedCitationSources = null },
        )
    }
    // During generation, eat horizontal nested-scroll so code blocks
    // cannot be panned. Vertical scroll and taps (thinking header,
    // stop button) pass through normally. Text selection is already
    // prevented during streaming by the stable Markdown selection host.
    val horizontalScrollEater = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)
        }
    }
    val segmentsOrNull = message.segments
    val mergedSegments = remember(segmentsOrNull) {
        mergeAdjacentSegments(segmentsOrNull.orEmpty())
    }
    val answerTextDeltas = remember(mergedSegments) {
        mergedSegments
            .filter { it.isVisibleAnswerSegment() }
            .flatMap { it.streamingTextDeltas }
    }
    val answerFadeTracker =
        segmentAppearanceRegistry.streamingFadeTracker("${message.id}:answer")
    val generationActive = message.participant == Participant.MODEL &&
        (
            isStreaming ||
                message.status == MessageStatus.SENDING ||
                message.status == MessageStatus.THINKING ||
                message.status == MessageStatus.TOOL_CALLING ||
                message.status == MessageStatus.TRANSCRIBING
        )
    val hasAnswerContent =
        message.text.isNotBlank() || mergedSegments.any { it.isVisibleAnswerSegment() }
    val inlineActivityPresentation = assistantInlineActivityPresentation(
        generationActive = generationActive,
        isStopping = isStopping,
        hasAnswer = hasAnswerContent,
        hasVisibleInfoSegment = mergedSegments.any { it.isInfoSegment() },
        retryText = message.retryText,
    )
    val inlineActivityMode = inlineActivityPresentation.mode
    val inlineActivityTransition = updateTransition(
        targetState = inlineActivityMode != AssistantInlineActivityMode.NONE ||
            inlineActivityPresentation.retainLayout,
        label = "AssistantInlineActivityVisibility",
    )
    val inlineActivityOpacity by inlineActivityTransition.animateFloat(
        transitionSpec = { snap() },
        label = "AssistantInlineActivityOpacity",
    ) { visible ->
        if (visible) 1f else 0f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AssistantMessageHorizontalInset)
            .then(contextAlpha)
            .then(if (isStreaming) Modifier.nestedScroll(horizontalScrollEater) else Modifier)
    ) {
        Column {
            Spacer(modifier = Modifier.height(FormerAssistantStatusSpacerHeight))

            // GenerationManager already publishes a bounded stream cadence. A second UI debounce
            // delayed every chunk, retained a stale text job through Stop, and then replaced the
            // whole document at terminalization. Feed the latest immutable snapshot directly to
            // the off-main Markdown parser.
            val renderedText = message.text

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                    val isError = message.status == MessageStatus.ERROR || message.participant == Participant.ERROR

                // Only zero out thought height when legacy thought block is not shown
                if (message.segments != null || message.thoughts.isNullOrBlank()) {
                    setThoughtBlockHeight(0)
                }

                val failedToGenerateText = stringResource(R.string.failed_to_generate)
                val errorContent = remember(
                    message.text,
                    message.status,
                    message.participant,
                    message.modelName,
                    mergedSegments,
                    failedToGenerateText,
                ) {
                    assistantErrorContent(message, mergedSegments, failedToGenerateText)
                }
                val hasImageGenerationBoundary =
                    mergedSegments.any { it.isImageGenerationSegment() }
                val orderedFallbackAnswerText =
                    if (
                        hasImageGenerationBoundary &&
                        mergedSegments.none { it.isVisibleAnswerSegment() }
                    ) {
                        errorContent?.answerText ?: renderedText.takeIf { !isError }
                    } else {
                        null
                    }
                val orderedSegments = remember(mergedSegments, orderedFallbackAnswerText) {
                    orderedFallbackAnswerText
                        ?.takeIf { it.isNotBlank() }
                        ?.let { fallback ->
                            mergedSegments + MessageSegment(type = "answer", content = fallback)
                        }
                        ?: mergedSegments
                }
                val normalizedToolCallDisplayMode = ToolCallDisplayModes.normalize(toolCallDisplayMode)
                val useThinkingSheet =
                    ThinkingSegmentDisplayModes.effectiveMode(
                        thinkingSegmentDisplayMode,
                        normalizedToolCallDisplayMode,
                    ) == ThinkingSegmentDisplayModes.BOTTOM_SHEET
                val groupAdjacentTimelineTools = normalizedToolCallDisplayMode == ToolCallDisplayModes.GROUPED_TIMELINE
                val groupOrderedInfoBlocks =
                    groupAdjacentTimelineTools ||
                        (
                            hasImageGenerationBoundary &&
                                normalizedToolCallDisplayMode != ToolCallDisplayModes.TIMELINE
                            )
                val useTimelineSegments =
                    hasImageGenerationBoundary ||
                        (
                            !useThinkingSheet &&
                                normalizedToolCallDisplayMode != ToolCallDisplayModes.COMPACT &&
                                (
                                    mergedSegments.any { it.type == "answer" } ||
                                        (
                                            groupAdjacentTimelineTools &&
                                                mergedSegments.any { it.isInfoSegment() }
                                            )
                                    )
                            )
                val detailSegments = remember(mergedSegments) {
                    mergedSegments.filter { it.type != "answer" && it.type != "error" }
                }
                val compactVisible = !useTimelineSegments && detailSegments.isNotEmpty()
                val sheetCollapsedStates = remember(message.id) {
                    mutableStateMapOf<String, Boolean>()
                }
                val compactAppearanceKey = compactSegmentBlockAppearanceKey(message.id)
                val compactCardAppearanceKey = "$compactAppearanceKey:card"
                val latestVisibleAnswerIndex =
                    mergedSegments.indexOfLast { it.isVisibleAnswerSegment() }
                val latestVisibleAnswer = mergedSegments.getOrNull(latestVisibleAnswerIndex)
                val compactAnswerAppearanceKey = latestVisibleAnswer?.let { segment ->
                    "${segmentAppearanceKey(
                        message.id,
                        latestVisibleAnswerIndex,
                        segment,
                    )}:compact-answer"
                }

                if (useTimelineSegments) {
                    TimelineSegmentsContent(
                        segments = orderedSegments,
                        detailSegments = detailSegments,
                        message = message,
                        isStreaming = isStreaming,
                        generationActive = generationActive,
                        groupAdjacentBlocks = groupOrderedInfoBlocks,
                        autoExpandActiveGroup =
                            groupAdjacentTimelineTools && autoExpandActiveGroup,
                        autoExpansionController = groupedSegmentAutoExpansionController,
                        expandedStates =
                            if (useThinkingSheet) sheetCollapsedStates else thoughtExpandedStates,
                        renderContext = renderContext,
                        searchHighlight = searchHighlight,
                        citations = citations,
                        onCitationActivate = onCitationActivate,
                        segmentAppearanceRegistry = segmentAppearanceRegistry,
                        onLayoutMutationStarted = onLayoutMutationStarted,
                        onLayoutMutationSettled = onLayoutMutationSettled,
                        onMediaClick = onMediaClick,
                        opensDetailSheet = useThinkingSheet,
                        preserveInitialCompactIdentity =
                            normalizedToolCallDisplayMode == ToolCallDisplayModes.COMPACT ||
                                useThinkingSheet,
                        onGroupHeaderClick = if (useThinkingSheet) {
                            { indices -> onSegmentSelected(indices, true) }
                        } else {
                            null
                        },
                        onSegmentClick = { indices ->
                            onSegmentSelected(indices, false)
                        }
                    )
                }

                // Compact segment block: single block, newest title/icon when collapsed.
                // Answer segments are timeline anchors only; compact mode still renders
                // message.text below as the complete answer.
                if (compactVisible) {
                    AnimatedTimelineBlockAppearance(
                        animationKey = compactAppearanceKey,
                        appearanceRegistry = segmentAppearanceRegistry,
                        isStreaming = isStreaming,
                        forceOpaque = detailSegments.any { it.type == "tool" },
                    ) {
                        CompactSegmentBlock(
                            segs = detailSegments,
                            segmentIndices = detailSegments.indices.toList(),
                            message = message,
                            isStreaming = isStreaming,
                            useLiveStatus = true,
                            generationActive = generationActive,
                            isCurrentCard = !hasAnswerContent,
                            expandedStates = if (useThinkingSheet) sheetCollapsedStates else thoughtExpandedStates,
                            expansionKey = message.id,
                            cardAppearanceKey = compactCardAppearanceKey,
                            segmentAppearanceRegistry = segmentAppearanceRegistry,
                            onExpansionStarted = onLayoutMutationStarted,
                            onExpansionSettled = onLayoutMutationSettled,
                            onSegmentClick = { index ->
                                if (useThinkingSheet) {
                                    onSegmentSelected(detailSegments.indices.toList(), true)
                                } else {
                                    onSegmentSelected(listOf(index), false)
                                }
                            },
                            onHeaderClick = if (useThinkingSheet) {
                                {
                                    onSegmentSelected(
                                        detailSegments.indices.toList(),
                                        true,
                                    )
                                }
                            } else {
                                null
                            },
                            opensDetailSheet = useThinkingSheet,
                            onBlockHeightChanged = setThoughtBlockHeight,
                        )
                    }
                }

                val answerBodyText = errorContent?.answerText ?: renderedText.takeIf { !isError }
                val answerProjection = remember(answerBodyText, citations, isStreaming) {
                    citationMarkdownProjection(
                        answerText = answerBodyText.orEmpty(),
                        citations = citations,
                        isStreaming = isStreaming,
                    )
                }
                val answerContent = answerProjection?.markdown ?: answerBodyText.orEmpty()
                val lastVisibleTerminalPredecessor = if (useTimelineSegments) {
                    mergedSegments.lastOrNull { segment ->
                        segment.isVisibleAnswerSegment() || segment.isInfoSegment()
                    }
                } else {
                    null
                }
                val terminalImmediatelyFollowsCard = if (useTimelineSegments) {
                    lastVisibleTerminalPredecessor?.isInfoSegment() == true
                } else {
                    compactVisible && answerContent.isEmpty()
                }
                val inlineTerminalText = when {
                    hasAnswerContent -> null
                    errorContent != null -> errorContent.errorText
                    !isStreaming && message.status == MessageStatus.STOPPED ->
                        stringResource(R.string.generation_stopped)
                    else -> null
                }
                if (message.participant == Participant.MODEL) {
                    AssistantInlineActivity(
                        mode = inlineActivityMode,
                        retryText = message.retryText,
                        visibilityTransition = inlineActivityTransition,
                        activityOpacity = inlineActivityOpacity,
                        retainExitLayout = inlineActivityPresentation.retainLayout,
                        terminalText = inlineTerminalText,
                        terminalIsError = errorContent != null,
                        terminalShowLocalContextHelp =
                            errorContent?.showLocalContextHelp == true,
                        precededByCard = terminalImmediatelyFollowsCard,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noOpBringIntoView()
                ) {
                    if (answerContent.isNotEmpty() && !useTimelineSegments) {
                        CitationTerminalProjectionHost(
                            animationKey = "${message.id}:answer",
                            projection = answerProjection,
                            isStreaming = isStreaming,
                            onLayoutMutationStarted = onLayoutMutationStarted,
                            onLayoutMutationSettled = onLayoutMutationSettled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { presentedProjection, presentedIsStreaming ->
                            val presentedContent =
                                presentedProjection?.markdown ?: answerBodyText.orEmpty()
                            CitationInlineContentHost(
                                projection = presentedProjection,
                                onActivate = onCitationActivate,
                            ) {
                                CompositionLocalProvider(
                                    LocalSearchHighlightSpec provides searchHighlight,
                                ) {
                                if (editorialEnabled && presentedContent.isNotBlank() && !presentedIsStreaming) {
                                    com.newoether.agora.studio.editorial.EditorialMessageContent(
                                        text = presentedContent,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    if (compactAnswerAppearanceKey != null) {
                                        AnimatedTimelineBlockAppearance(
                                            animationKey = compactAnswerAppearanceKey,
                                            appearanceRegistry = segmentAppearanceRegistry,
                                            isStreaming = isStreaming,
                                        ) {
                                            StreamingMarkdownMessage(
                                                content = presentedContent,
                                                isStreaming = presentedIsStreaming,
                                                renderContext = renderContext,
                                                modifier = Modifier.fillMaxWidth(),
                                                selectionEnabled = !presentedIsStreaming,
                                                textDeltas = answerTextDeltas,
                                                fadeTracker = answerFadeTracker,
                                            )
                                        }
                                    } else {
                                        StreamingMarkdownMessage(
                                            content = presentedContent,
                                            isStreaming = presentedIsStreaming,
                                            renderContext = renderContext,
                                            modifier = Modifier.fillMaxWidth(),
                                            selectionEnabled = !presentedIsStreaming,
                                            textDeltas = answerTextDeltas,
                                            fadeTracker = answerFadeTracker,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                var retainedErrorText by remember { mutableStateOf("") }
                var retainedShowLocalContextHelp by remember { mutableStateOf(false) }
                LaunchedEffect(errorContent) {
                    errorContent?.let {
                        retainedErrorText = it.errorText
                        retainedShowLocalContextHelp = it.showLocalContextHelp
                    }
                }
                AnimatedVisibility(
                    visible = hasAnswerContent && errorContent != null,
                    enter = fadeIn(tween(durationMillis = 180, easing = LinearEasing)),
                    exit = fadeOut(tween(durationMillis = 180, easing = LinearEasing)),
                ) {
                    GenerationErrorBar(
                        errorText = errorContent?.errorText ?: retainedErrorText,
                        precededByCard = terminalImmediatelyFollowsCard,
                        showLocalContextHelp =
                            errorContent?.showLocalContextHelp
                                ?: retainedShowLocalContextHelp,
                    )
                }
                AnimatedVisibility(
                    visible = hasAnswerContent && !isStreaming &&
                        message.status == MessageStatus.STOPPED,
                    enter = fadeIn(tween(durationMillis = 180, easing = LinearEasing)),
                    exit = fadeOut(tween(durationMillis = 180, easing = LinearEasing)),
                ) {
                    StoppedGenerationBar(
                        precededByCard = terminalImmediatelyFollowsCard,
                    )
                }
                if (message.participant == Participant.MODEL && message.images.isNotEmpty()) {
                    val genImages = message.images
                    // Generated images are primary output, not input references:
                    // render as a full-width square card, image cropped to fill
                    // with rounded corners, tap to view fullscreen.
                    Column(
                        modifier = Modifier.padding(top = if (renderedText.isNotEmpty()) 8.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        genImages.forEachIndexed { idx, path ->
                            val isVideo = path.endsWith(".mp4", true) ||
                                path.endsWith(".webm", true) ||
                                path.endsWith(".mov", true)
                            if (isVideo) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onMediaClick(genImages, idx) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = androidx.compose.ui.graphics.Color(0xFF0F172A),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFA78BFA).copy(alpha = 0.4f)),
                                    shadowElevation = 8.dp
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                                        listOf(
                                                            androidx.compose.ui.graphics.Color(0xFF0F172A),
                                                            androidx.compose.ui.graphics.Color(0xFF1E1B4B),
                                                            androidx.compose.ui.graphics.Color(0xFF0F172A)
                                                        )
                                                    )
                                                )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .align(Alignment.Center)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(androidx.compose.ui.graphics.Color(0xFFA78BFA)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Lire la vidéo",
                                                tint = androidx.compose.ui.graphics.Color(0xFF0F172A),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        0f to androidx.compose.ui.graphics.Color.Transparent,
                                                        1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
                                                    )
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Vidéo générée",
                                                fontSize = 12.sp,
                                                color = androidx.compose.ui.graphics.Color(0xFFE2E8F0),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Lire la vidéo",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = androidx.compose.ui.graphics.Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                coil.compose.AsyncImage(
                                    model = path,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = { onMediaClick(genImages, idx) },
                                            onLongClick = { haptics.longPress() },
                                        )
                                )
                            }
                        }
                    }
                }
                if (message.participant == Participant.MODEL && showActions) {
                    val informationActionsAlpha by animateFloatAsState(
                        targetValue = if (actionAvailability.informationVisible) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (actionAvailability.informationVisible) {
                                ACTIONS_ENTER_DURATION_MS
                            } else {
                                ACTIONS_EXIT_DURATION_MS
                            },
                            easing = LinearEasing,
                        ),
                        label = "assistantInformationActions:${message.id}",
                    )
                    val terminalActionsAlpha by animateFloatAsState(
                        targetValue = if (actionAvailability.terminalVisible) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (actionAvailability.terminalVisible) {
                                ACTIONS_ENTER_DURATION_MS
                            } else {
                                ACTIONS_EXIT_DURATION_MS
                            },
                            easing = LinearEasing,
                        ),
                        label = "assistantActions:${message.id}",
                    )
                    val enabledActionTint =
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    val terminalActionTint =
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (actionAvailability.terminalEnabled) 0.6f else 0.3f
                        )
                    val destructiveActionTint =
                        MaterialTheme.colorScheme.error.copy(
                            alpha = if (actionAvailability.terminalEnabled) 1f else 0.38f
                        )
                    if (sourcesSummaryVisible || informationActionsAlpha > 0f) {
                        CitationSourcesSummaryCapsule(
                            messageId = message.id,
                            citations = citations,
                            searchSpec = null,
                            visible = sourcesSummaryVisible,
                            enabled = sourcesSummaryVisible,
                            onClick = {
                                groupedCitationSources = null
                                showCitationSources = true
                            },
                            modifier = Modifier
                                .offset(x = (-AUXILIARY_CARD_START_EXTENSION_DP).dp)
                                .padding(top = 12.dp)
                                .graphicsLayer { alpha = informationActionsAlpha },
                        )
                    }
                    val answerTailVisible = shouldShowStreamingTailIndicator(isStreaming, isStopping, message)
                    val actionContent: @Composable RowScope.() -> Unit = {
                        if (!actionCopyText.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(actionCopyText))
                                    haptics.confirm()
                                },
                                enabled = actionAvailability.informationEnabled,
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer { alpha = informationActionsAlpha },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.copy),
                                    modifier = Modifier.size(17.dp),
                                    tint = enabledActionTint,
                                )
                            }
                        }
                        val textToSpeak = actionCopyText ?: message.text
                        if (!textToSpeak.isNullOrBlank()) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val isCurrentSpeaking = com.newoether.agora.ui.chat.audio.FullFlowAudioController.currentlySpeakingMessageId == message.id &&
                                com.newoether.agora.ui.chat.audio.FullFlowAudioController.isSpeakingInChat
                            IconButton(
                                onClick = {
                                    if (isCurrentSpeaking) {
                                        com.newoether.agora.ui.chat.audio.FullFlowAudioController.stopInChat()
                                    } else {
                                        com.newoether.agora.ui.chat.audio.FullFlowAudioController.playInChat(context, message.id, textToSpeak)
                                    }
                                },
                                enabled = actionAvailability.informationEnabled,
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer { alpha = informationActionsAlpha },
                            ) {
                                Icon(
                                    if (isCurrentSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isCurrentSpeaking) "Arrêter la lecture" else "Lire à voix haute",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isCurrentSpeaking) MaterialTheme.colorScheme.primary else enabledActionTint,
                                )
                            }
                        }
                        if (!actionCopyText.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    com.newoether.agora.wand.WandMessageTrigger.request(actionCopyText)
                                    haptics.confirm()
                                },
                                enabled = actionAvailability.informationEnabled,
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer { alpha = informationActionsAlpha },
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Reformuler avec la baguette magique",
                                    modifier = Modifier.size(17.dp),
                                    tint = if (actionAvailability.informationEnabled) {
                                        androidx.compose.ui.graphics.Color(0xFFA78BFA)
                                    } else {
                                        enabledActionTint
                                    },
                                )
                            }
                        }
                        if (!actionCopyText.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    com.newoether.agora.tool.BrainSaveController.requestSave(actionCopyText)
                                    haptics.confirm()
                                },
                                enabled = actionAvailability.informationEnabled,
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer { alpha = informationActionsAlpha },
                            ) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = "Sauver dans le Second Cerveau",
                                    modifier = Modifier.size(17.dp),
                                    tint = if (actionAvailability.informationEnabled) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        enabledActionTint
                                    },
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (onRegenerate(message.id)) {
                                    regenerateRequested = true
                                    showMenu = false
                                }
                            },
                            enabled = actionAvailability.terminalEnabled,
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer { alpha = terminalActionsAlpha },
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.retry),
                                modifier = Modifier.size(19.dp),
                                tint = terminalActionTint,
                            )
                        }
                        IconButton(
                            onClick = onFork,
                            enabled = actionAvailability.terminalEnabled,
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer { alpha = terminalActionsAlpha },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = stringResource(R.string.conversation_fork_from_here),
                                modifier = Modifier.size(18.dp),
                                tint = terminalActionTint,
                            )
                        }
                        IconButton(
                            onClick = onShare,
                            enabled = actionAvailability.terminalEnabled,
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer { alpha = terminalActionsAlpha },
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.conversation_share),
                                modifier = Modifier.size(17.dp),
                                tint = terminalActionTint,
                            )
                        }
                        Box {
                            IconButton(
                                onClick = {
                                    showMenu = true
                                },
                                enabled = actionAvailability.informationEnabled,
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer { alpha = informationActionsAlpha },
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = enabledActionTint,
                                )
                            }
                            DropdownMenu(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 16.dp,
                                shape = RoundedCornerShape(12.dp),
                                expanded = showMenu && actionAvailability.informationVisible,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.info)) },
                                    onClick = {
                                        showMenu = false
                                        onShowInfo()
                                    },
                                    enabled = actionAvailability.informationEnabled,
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.delete),
                                            color = destructiveActionTint,
                                        )
                                    },
                                    onClick = {
                                        if (actionAvailability.terminalEnabled) {
                                            showMenu = false
                                            onShowDelete()
                                        }
                                    },
                                    enabled = actionAvailability.terminalEnabled,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = destructiveActionTint,
                                        )
                                    },
                                )
                            }
                        }

                        if (showBranchSelector && totalBranches > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(start = AgoraSpacing.Xs)
                                    .graphicsLayer { alpha = terminalActionsAlpha }
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AgoraAlpha.Hint)
                                    )
                                    .padding(horizontal = AgoraSpacing.Xs, vertical = AgoraSpacing.Xxs),
                            ) {
                                IconButton(
                                    onClick = { onSwitchBranch(-1) },
                                    enabled =
                                        actionAvailability.terminalEnabled &&
                                            branchIndex > 0 &&
                                            isEditingAllowed,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Branche précédente",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    "${branchIndex + 1} / $totalBranches",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = AgoraSpacing.Xxs),
                                )
                                IconButton(
                                    onClick = { onSwitchBranch(1) },
                                    enabled = actionAvailability.terminalEnabled &&
                                        branchIndex < totalBranches - 1 &&
                                        isEditingAllowed,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Branche suivante",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (message.participant == Participant.MODEL && !isStreaming && !generationActive && !message.text.isNullOrBlank() && message.status != MessageStatus.ERROR) {
                        FollowUpSuggestionsSection(
                            messageText = message.text,
                            userPrompt = userPrompt,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }

                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (answerTailVisible) {
                                GenerationActivityDot()
                                Spacer(Modifier.width(4.dp))
                            }
                            actionContent()
                        }
                    }
                }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

@Composable
private fun FollowUpSuggestionsSection(
    messageText: String,
    userPrompt: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val memorySnippet = remember {
        try {
            val app = context.applicationContext as? com.newoether.agora.AgoraApplication
            app?.requireContainer()?.memoryManager?.getActiveMemory()?.take(300)
        } catch (_: Exception) {
            null
        }
    }
    val suggestions = remember(messageText, userPrompt, memorySnippet) {
        FollowUpSuggestionsGenerator.generate(messageText, userPrompt, memorySnippet)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFFA78BFA),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Pistes de réflexion suggérées",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }

        suggestions.forEach { suggestion ->
            Surface(
                onClick = {
                    FollowUpSuggestionController.selectSuggestion(suggestion.text)
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (suggestion.type) {
                            FollowUpType.DEEP_DIVE -> androidx.compose.ui.graphics.Color(0xFF38BDF8).copy(alpha = 0.15f)
                            FollowUpType.EXPANSION -> androidx.compose.ui.graphics.Color(0xFF34A853).copy(alpha = 0.15f)
                            FollowUpType.TRANSVERSAL -> androidx.compose.ui.graphics.Color(0xFFA78BFA).copy(alpha = 0.15f)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                    text = suggestion.type.badge,
                    fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = when (suggestion.type) {
                                FollowUpType.DEEP_DIVE -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
                                FollowUpType.EXPANSION -> androidx.compose.ui.graphics.Color(0xFF34A853)
                                FollowUpType.TRANSVERSAL -> androidx.compose.ui.graphics.Color(0xFFA78BFA)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = suggestion.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f)
                    )

                        IconButton(
                            onClick = { FollowUpSuggestionController.sendSuggestion(suggestion.text) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Envoyer directement cette suggestion",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                }
            }
        }

        // « Transformer en livrable » : convertit la réponse en mindmap, dashboard,
        // image, podcast ou vidéo — toutes formes réellement rendues par FullFlow.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = "Transformer en :",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(end = 6.dp)
            )
            com.newoether.agora.ui.chat.DeliverableFormatChips(
                selected = null,
                onSelect = { format ->
                    FollowUpSuggestionController.selectSuggestion(format.conversionPrompt())
                },
            )
        }
    }
}

