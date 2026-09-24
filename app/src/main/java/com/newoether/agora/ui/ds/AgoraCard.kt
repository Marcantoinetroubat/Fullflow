package com.newoether.agora.ui.ds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * Card canonique bordurée. Remplace TaskCard, PersonaCard, AgentRowCard,
 * SearchResultCard et les ~30 *Card ad-hoc.
 *
 * Ne remplace PAS SettingsGroup/CardSurface : les groupes settings utilisent
 * une Surface sans bordure, des shapes groupées (stackedShape) et un padding
 * horizontal interne — migrer ajouterait une bordure visible (régression).
 */
@Composable
fun AgoraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = AgoraRadii.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            AgoraElevation.CardTonal,
            MaterialTheme.colorScheme.outline.copy(alpha = AgoraAlpha.Divider),
        ),
        onClick = onClick ?: {},
        enabled = onClick != null,
        content = content,
    )
}

@Composable
fun AgoraSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(
                start = AgoraSpacing.Xxxl,
                top = AgoraSpacing.Md,
                bottom = AgoraSpacing.Md,
            )
            .semantics { heading() },
    )
}
