package com.newoether.agora.ui.onboarding

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.newoether.agora.R
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

// Contract: local model configuration defaults for local models
// nCtx = 16384
// maxTokens = 1024

data class WelcomePage(
    val title: String,
    val description: String,
    val darkVideoResId: Int,
    val lightVideoResId: Int
)

private fun resolveVideoRes(isDarkTheme: Boolean, darkResId: Int, lightResId: Int): Int =
    if (isDarkTheme) darkResId else lightResId

@Composable
fun WelcomeScreen(
    onComplete: () -> Unit,
    isDarkTheme: Boolean = true,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val motionPolicy = LocalAgoraMotionPolicy.current

    val pages = remember {
        listOf(
            WelcomePage(
                context.getString(R.string.onboarding_welcome_title),
                context.getString(R.string.onboarding_welcome_desc),
                R.raw.welcome_video_1,
                R.raw.welcome_video_1_light
            ),
            WelcomePage(
                context.getString(R.string.onboarding_byok_title),
                context.getString(R.string.onboarding_byok_desc),
                R.raw.welcome_video_2,
                R.raw.welcome_video_2_light
            ),
            WelcomePage(
                context.getString(R.string.onboarding_model_video_title),
                context.getString(R.string.onboarding_model_video_desc),
                R.raw.welcome_video_3,
                R.raw.welcome_video_3_light
            ),
            WelcomePage(
                context.getString(R.string.onboarding_done_title),
                context.getString(R.string.onboarding_done_desc),
                R.raw.welcome_video_4,
                R.raw.welcome_video_4_light
            )
        )
    }

    // Initialize the 4 ExoPlayers with loop & muted playback
    val players = remember(isDarkTheme) {
        pages.map { page ->
            val resId = resolveVideoRes(isDarkTheme, page.darkVideoResId, page.lightVideoResId)
            val uri = "android.resource://${context.packageName}/$resId"
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = false
                setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                prepare()
            }
        }
    }

    DisposableEffect(players) {
        onDispose {
            players.forEach { it.release() }
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    var exiting by remember { mutableStateOf(false) }

    // Play active page video, pause others
    LaunchedEffect(pagerState.currentPage, motionPolicy.allowContinuousMotion) {
        players.forEachIndexed { i, player ->
            if (i == pagerState.currentPage && motionPolicy.allowContinuousMotion) {
                player.seekTo(0)
                player.playWhenReady = true
                player.play()
            } else {
                player.playWhenReady = false
                player.pause()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                players.forEach { it.pause() }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (motionPolicy.allowContinuousMotion && pagerState.currentPage in players.indices) {
                    players[pagerState.currentPage].play()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(exiting) {
        if (exiting) {
            kotlinx.coroutines.delay(200)
            onComplete()
        }
    }

    AnimatedVisibility(visible = !exiting, exit = fadeOut(tween(250))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar with Skip action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (pagerState.currentPage < pages.size - 1) {
                        TextButton(
                            onClick = { exiting = true }
                        ) {
                            Text(
                                stringResource(R.string.onboarding_skip),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.height(48.dp))
                    }
                }

                // 4 Video Pages
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 1
                ) { index ->
                    val page = pages[index]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // High-contrast video card with rounded corners
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Black,
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(24.dp))
                        ) {
                            LoopVideo(players[index])
                        }

                        Spacer(Modifier.height(32.dp))

                        // Title
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        // Description
                        Text(
                            text = page.description,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Modern Pill Indicator
                Row(
                    modifier = Modifier.padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { idx ->
                        val sel = pagerState.currentPage == idx
                        val dotSize by animateDpAsState(if (sel) 10.dp else 8.dp, tween(120))
                        val dotColor by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, tween(120))
                        Box(Modifier.padding(horizontal = 4.dp).size(10.dp), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }
                }

                // Action button: Continue on pages 0..2, Get Started on page 3
                val last = pagerState.currentPage == pages.size - 1
                Button(
                    onClick = {
                        if (last) { exiting = true }
                        else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 24.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (last) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_continue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun LoopVideo(player: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            val view = LayoutInflater.from(ctx).inflate(R.layout.view_texture_video_player, null, false) as PlayerView
            view.player = player
            view
        },
        update = { view ->
            if (view.player != player) {
                view.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
