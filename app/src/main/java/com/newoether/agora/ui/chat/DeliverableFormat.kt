package com.newoether.agora.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * « Faire briller l'intelligence sous toutes les formes »: deliverable formats a
 * prompt can be steered towards. Each format carries a self-sufficient instruction
 * suffix appended to the base prompt, so the LLM knows exactly which rich output
 * FullFlow can render (```mermaid / ```chart fences, image/podcast/video tools).
 */
enum class DeliverableFormat(
    val label: String,
    val icon: ImageVector,
    val promptSuffix: String,
    /** Whether the format appears as a quick-action chip (user feedback: mindmap/dashboard
     *  prompts confused models into raw JSON dumps — the passive ```mermaid/```chart
     *  renderers stay active for organic model output). */
    val chipEnabled: Boolean = true,
) {
    MINDMAP(
        label = "Mindmap",
        icon = Icons.Default.Hub,
        promptSuffix = "\n\nFormat attendu : présente le résultat sous forme de mindmap/" +
            "diagramme hiérarchisé en Mermaid, dans un bloc de code ```mermaid.",
        chipEnabled = false,
    ),
    DASHBOARD(
        label = "Dashboard",
        icon = Icons.Default.BarChart,
        promptSuffix = "\n\nFormat attendu : produis un tableau de bord visuel dans un bloc " +
            "de code ```chart contenant un JSON au format " +
            "{\"type\":\"bar|line|pie\",\"title\":\"...\",\"labels\":[...]," +
            "\"series\":[{\"name\":\"...\",\"values\":[...]}]}, avec des indicateurs chiffrés réalistes.",
        chipEnabled = false,
    ),
    IMAGE(
        label = "Image",
        icon = Icons.Default.Image,
        promptSuffix = "\n\nAction attendue : génère une image d'illustration de haute qualité " +
            "avec l'outil generate_image.",
    ),
    PODCAST(
        label = "Podcast",
        icon = Icons.Default.Podcasts,
        promptSuffix = "\n\nAction attendue : produis un épisode podcast audio sur ce sujet " +
            "avec l'outil generate_podcast.",
    ),
    VIDEO(
        label = "Vidéo",
        icon = Icons.Default.Videocam,
        promptSuffix = "\n\nAction attendue : produis une courte vidéo démonstrative " +
            "avec l'outil generate_video.",
    ),
    ;

    /** [base] prompt steered towards this deliverable format. */
    fun steeredPrompt(base: String): String = base.trim() + promptSuffix

    /** Prompt converting the assistant's last answer into this deliverable. */
    fun conversionPrompt(): String =
        "En t'appuyant sur ta dernière réponse, transforme-la en livrable directement exploitable." +
            promptSuffix
}

/**
 * Row of deliverable-format chips. [selected] highlights the active format;
 * [onSelect] receives the tapped format (the caller decides toggle vs direct action).
 */
@Composable
fun DeliverableFormatChips(
    selected: DeliverableFormat?,
    onSelect: (DeliverableFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeliverableFormat.entries.filter { it.chipEnabled }.forEach { format ->
            val isSelected = format == selected
            Surface(
                onClick = { onSelect(format) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = format.icon,
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                text = format.label,
                fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                    )
                }
            }
        }
    }
}
