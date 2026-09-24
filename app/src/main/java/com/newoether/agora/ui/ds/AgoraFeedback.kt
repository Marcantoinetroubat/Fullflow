package com.newoether.agora.ui.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.motion.MotionAwareCircularProgressIndicator
import com.newoether.agora.ui.motion.MotionAwareLinearProgressIndicator

/**
 * Feedback canonique : Snackbar/Loader/Empty/Error.
 * Remplace MainNavigationSnackbarHost inline, usages M3 directs,
 * StreamingTailIndicator isolé (conservé pour le stream) et les
 * empty/error inline (TasksScreen, ResearchScreen, AttachmentThumbnail...).
 */
@Composable
fun AgoraSnackbarCard(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = AgoraRadii.Sm,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = AgoraElevation.SnackbarShadow,
        shadowElevation = AgoraElevation.SnackbarShadow,
    ) {
        Column(Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun AgoraLoader(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { liveRegion() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MotionAwareCircularProgressIndicator()
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AgoraSpacing.Sm),
            )
        }
    }
}

@Composable
fun AgoraLinearLoader(modifier: Modifier = Modifier) {
    MotionAwareLinearProgressIndicator(modifier = modifier.fillMaxWidth())
}

@Composable
fun AgoraEmptyState(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AgoraSpacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AgoraAlpha.Hint),
            modifier = Modifier.size(40.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = AgoraSpacing.Lg)
                .semantics { heading() },
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AgoraSpacing.Sm),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = AgoraSpacing.Lg),
                shape = AgoraRadii.Pill,
            ) { Text(actionLabel) }
        }
    }
}

@Composable
fun AgoraErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AgoraSpacing.Xxxl)
            .semantics { liveRegion() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AgoraSpacing.Sm),
        )
        if (retryLabel != null && onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = AgoraSpacing.Lg),
                shape = AgoraRadii.Pill,
            ) { Text(retryLabel) }
        }
    }
}
