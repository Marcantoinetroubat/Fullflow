package com.newoether.agora.ui.chat.message

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.studio.social.SocialExportEngine
import com.newoether.agora.studio.social.SocialPlatform
import kotlinx.coroutines.launch

/**
 * Floating action bar at the bottom of editorial messages.
 * Shows social export buttons (LinkedIn, Instagram, TikTok, WhatsApp, Brain).
 */
@Composable
fun EditorialActionsBar(
    articleText: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportEngine = remember { SocialExportEngine(context) }

    var isExporting by remember { mutableStateOf<SocialPlatform?>(null) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF161B22).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LinkedIn
            SocialActionButton(
                icon = Icons.Default.Work,
                label = "LinkedIn",
                color = Color(0xFF0A66C2),
                isLoading = isExporting == SocialPlatform.LINKEDIN,
                onClick = {
                    isExporting = SocialPlatform.LINKEDIN
                    scope.launch {
                        val result = exportEngine.transcode(articleText, SocialPlatform.LINKEDIN)
                        exportEngine.share(result)
                        isExporting = null
                    }
                }
            )

            // Instagram
            SocialActionButton(
                icon = Icons.Default.PhotoCamera,
                label = "Instagram",
                color = Color(0xFFE4405F),
                isLoading = isExporting == SocialPlatform.INSTAGRAM,
                onClick = {
                    isExporting = SocialPlatform.INSTAGRAM
                    scope.launch {
                        val result = exportEngine.transcode(articleText, SocialPlatform.INSTAGRAM)
                        exportEngine.share(result)
                        isExporting = null
                    }
                }
            )

            // TikTok
            SocialActionButton(
                icon = Icons.Default.MusicNote,
                label = "TikTok",
                color = Color(0xFF000000),
                isLoading = isExporting == SocialPlatform.TIKTOK,
                onClick = {
                    isExporting = SocialPlatform.TIKTOK
                    scope.launch {
                        val result = exportEngine.transcode(articleText, SocialPlatform.TIKTOK)
                        exportEngine.share(result)
                        isExporting = null
                    }
                }
            )

            // WhatsApp
            SocialActionButton(
                icon = Icons.Default.Chat,
                label = "WhatsApp",
                color = Color(0xFF25D366),
                isLoading = isExporting == SocialPlatform.WHATSAPP,
                onClick = {
                    isExporting = SocialPlatform.WHATSAPP
                    scope.launch {
                        val result = exportEngine.transcode(articleText, SocialPlatform.WHATSAPP)
                        exportEngine.share(result)
                        isExporting = null
                    }
                }
            )

            // Second Brain
            SocialActionButton(
                icon = Icons.Default.Psychology,
                label = "Brain",
                color = Color(0xFF38BDF8),
                isLoading = isExporting == SocialPlatform.BRAIN,
                onClick = {
                    isExporting = SocialPlatform.BRAIN
                    scope.launch {
                        val result = exportEngine.transcode(articleText, SocialPlatform.BRAIN)
                        exportEngine.saveToBrain(result)
                        isExporting = null
                        android.widget.Toast.makeText(context, "Sauvegardé dans le Second Cerveau", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun SocialActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (isLoading) color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick, enabled = !isLoading)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = color,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
