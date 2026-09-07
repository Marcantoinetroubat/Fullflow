package com.newoether.agora.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import kotlinx.coroutines.delay

@Composable
fun MainNavigationSnackbarHost(
    snackbarHostState: SnackbarHostState,
    snackbarVersion: Int,
    accessibilityManager: AccessibilityManager?,
    motionPolicy: AgoraMotionPolicy,
    snackbarBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val current = snackbarHostState.currentSnackbarData
    var showing by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf<SnackbarData?>(null) }

    LaunchedEffect(current, snackbarVersion) {
        if (current != null) {
            if (showing) { showing = false; delay(200) }
            content = current
            showing = true
        } else {
            showing = false
            delay(400)
            content = null
        }
    }

    LaunchedEffect(content, accessibilityManager) {
        val data = content ?: return@LaunchedEffect
        val timeoutMillis = snackbarTimeoutMillis(data.visuals, accessibilityManager)
        if (timeoutMillis != Long.MAX_VALUE) {
            delay(timeoutMillis)
            if (snackbarHostState.currentSnackbarData === data) {
                data.dismiss()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showing,
            enter = if (motionPolicy.allowSpatialTransitions) {
                fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.8f)
            } else {
                fadeIn(tween(400))
            },
            exit = if (motionPolicy.allowSpatialTransitions) {
                fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.8f)
            } else {
                fadeOut(tween(400))
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = snackbarBottomPadding + 2.dp)
        ) {
            content?.let { data ->
                Snackbar(
                    modifier = Modifier.padding(horizontal = 12.dp).padding(vertical = 10.dp).shadow(6.dp, RoundedCornerShape(12.dp), clip = false),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    dismissAction = @Composable {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(onClick = { data.dismiss() }, modifier = Modifier.size(28.dp).clip(CircleShape)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    action = data.visuals.actionLabel?.let { label ->
                        @Composable { TextButton(onClick = { data.performAction() }) { Text(label) } }
                    },
                    content = { Text(data.visuals.message) }
                )
            }
        }
    }
}

fun snackbarTimeoutMillis(
    visuals: SnackbarVisuals,
    accessibilityManager: AccessibilityManager?
): Long {
    val durationMillis = when (visuals.duration) {
        SnackbarDuration.Short -> 4000L
        SnackbarDuration.Long -> 10000L
        SnackbarDuration.Indefinite -> Long.MAX_VALUE
    }
    if (durationMillis == Long.MAX_VALUE) return durationMillis
    return accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = durationMillis,
        containsIcons = true,
        containsText = true,
        containsControls = visuals.actionLabel != null
    ) ?: durationMillis
}
