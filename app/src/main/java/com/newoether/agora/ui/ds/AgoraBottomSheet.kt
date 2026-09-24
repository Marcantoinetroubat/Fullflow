package com.newoether.agora.ui.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy

/**
 * BottomSheet canonique pour les sheets à dismiss simple (sans hide()
 * programmatique) : visuel Smooth (handle, scrim 0.32, shape Md) délégué
 * au M3 ModalBottomSheet, skipPartiallyExpanded si reduced motion.
 * [com.newoether.agora.ui.motion.MotionAwareModalBottomSheet] reste réservé
 * aux sheets à hide() programmatique (ImageActions, ChatBottomSheet hôtes,
 * pickers Prompts/Skills) ; [com.newoether.agora.ui.components.SmoothBottomSheet]
 * au seul SegmentDetailSheet (back-stack interne).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgoraBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val policy = LocalAgoraMotionPolicy.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = !policy.allowSpatialTransitions)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = state,
        shape = AgoraRadii.Sheet,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = AgoraAlpha.Scrim),
        dragHandle = {
            Box(
                modifier = Modifier.padding(
                    top = AgoraSpacing.Md,
                    bottom = AgoraSpacing.Sm,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = AgoraSheetDefaults.HandleWidth, height = AgoraSheetDefaults.HandleHeight)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = AgoraAlpha.Handle),
                            AgoraRadii.Full,
                        ),
                )
            }
        },
        content = content,
    )
}

object AgoraSheetDefaults {
    val HandleWidth = 36.dp
    val HandleHeight = 5.dp
}
