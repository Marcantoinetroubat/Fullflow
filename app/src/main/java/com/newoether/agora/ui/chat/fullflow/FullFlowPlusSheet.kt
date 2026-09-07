package com.newoether.agora.ui.chat.fullflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.chat.audio.FullFlowAudioController
import com.newoether.agora.ui.theme.OutfitFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullFlowPlusSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onLaunchGeminiLive: () -> Unit,
    onNewChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAudioStudio: (() -> Unit)? = null,
) {
    if (!visible) return

    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = modalBottomSheetState,
        containerColor = Color(0xFF0F1318),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "FullFlow Hub",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
            Text(
                text = "Outils avancés et extensions intelligentes",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            FullFlowPlusItemWithPainter(
                painter = painterResource(id = R.drawable.ic_nano_banana),
                iconTint = Color.Unspecified,
                title = stringResource(R.string.image_studio_title),
                subtitle = stringResource(R.string.image_studio_desc),
                onClick = {
                    onDismissRequest()
                    com.newoether.agora.studio.image.GeminiImageStudioController.openStudio()
                }
            )

            FullFlowPlusItemWithPainter(
                painter = painterResource(id = R.drawable.ic_google_drive),
                iconTint = Color.Unspecified,
                title = stringResource(R.string.workspace_google_drive),
                subtitle = stringResource(R.string.workspace_google_drive_desc),
                onClick = {
                    onDismissRequest()
                    com.newoether.agora.workspace.drive.GoogleDriveWorkspaceController.openDriveDashboard()
                }
            )

            FullFlowPlusItem(
                icon = Icons.Default.GraphicEq,
                iconTint = Color(0xFF38BDF8),
                title = stringResource(R.string.audio_studio_hub_title),
                subtitle = stringResource(R.string.audio_studio_hub_subtitle),
                onClick = {
                    onDismissRequest()
                    if (onOpenAudioStudio != null) {
                        onOpenAudioStudio()
                    } else {
                        FullFlowAudioController.openLiveTranscription()
                    }
                }
            )

            FullFlowPlusItem(
                icon = Icons.Default.AutoAwesome,
                iconTint = Color(0xFF90CAF9),
                title = stringResource(R.string.gemini_live_title),
                subtitle = "Conversation vocale en direct ultra-fluide",
                onClick = {
                    onDismissRequest()
                    onLaunchGeminiLive()
                }
            )

            FullFlowPlusItem(
                icon = Icons.Default.Add,
                iconTint = Color(0xFF81C784),
                title = "Nouvelle conversation",
                subtitle = "Démarrer un échange avec les modèles LLM",
                onClick = {
                    onDismissRequest()
                    onNewChat()
                }
            )

            FullFlowPlusItem(
                icon = Icons.Default.CheckCircle,
                iconTint = Color(0xFFFFB74D),
                title = "Tâches & Projets",
                subtitle = "Gestionnaire de tâches et automatisations",
                onClick = {
                    onDismissRequest()
                    onOpenTasks()
                }
            )

            FullFlowPlusItem(
                icon = Icons.Default.Settings,
                iconTint = Color(0xFFB0BEC5),
                title = "Configuration & Clés API",
                subtitle = "Modèles, fournisseurs et personnalisation",
                onClick = {
                    onDismissRequest()
                    onOpenSettings()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FullFlowPlusItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161C23),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun FullFlowPlusItemWithPainter(
    painter: androidx.compose.ui.graphics.painter.Painter,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161C23),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF4285F4).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}

