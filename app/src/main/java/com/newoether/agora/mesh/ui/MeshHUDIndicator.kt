package com.newoether.agora.mesh.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.mesh.MeshController
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraElevation
import com.newoether.agora.ui.ds.AgoraRadii
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.FullLiveBridge
import com.newoether.agora.ui.theme.OutfitFamily

/**
 * Visual team activity indicator (HUD) shown in top bar / header.
 * Displays live counts of Working (🔴), Idle (⚪), and Stuck (❌) agents.
 */
@Composable
fun MeshHUDIndicator(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { MeshController.openStudio() }
) {
    val workingCount by MeshController.workingAgentsCount.collectAsState()
    val idleCount by MeshController.idleAgentsCount.collectAsState()
    val stuckCount by MeshController.stuckAgentsCount.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val workingPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val shape = AgoraRadii.Md

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF13171D).copy(alpha = 0.85f))
            .border(
                width = AgoraElevation.CardTonal,
                color = when {
                    stuckCount > 0 -> Color(0xFFEF4444).copy(alpha = AgoraAlpha.Hint)
                    workingCount > 0 -> FullLiveBridge.Cyan.copy(alpha = 0.5f)
                    else -> Color.White.copy(alpha = AgoraAlpha.Pressed)
                },
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = "Ouvrir le studio Mesh",
                onClick = onClick
            )
            .padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Label "MESH"
            Text(
                text = "MESH",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.width(AgoraSpacing.Xs))

            // Working indicator (🔴)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (workingCount > 0) Color(0xFFEF4444).copy(alpha = workingPulseAlpha)
                            else Color(0xFFEF4444).copy(alpha = 0.25f)
                        )
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Xxs))
                Text(
                    text = "$workingCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (workingCount > 0) Color(0xFFFCA5A5) else Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.width(AgoraSpacing.Xs))

            // Idle indicator (⚪)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Xxs))
                Text(
                    text = "$idleCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8)
                )
            }

            // Stuck indicator (❌) if any
            if (stuckCount > 0) {
                Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "❌",
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(AgoraSpacing.Xxs))
                    Text(
                        text = "$stuckCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}
