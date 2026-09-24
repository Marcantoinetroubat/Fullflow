package com.newoether.agora.studio.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraElevation
import com.newoether.agora.ui.ds.AgoraRadii
import com.newoether.agora.ui.ds.AgoraSpacing

/**
 * Barre supérieure canonique des studios créatifs (image, vidéo).
 *
 * Fusion de `StudioTopBar` (image) et `StudioVideoTopBar` (vidéo), qui ne
 * différaient que par un bit de couleur de pastille, le libellé du bouton
 * "nouveau" et un badge "FF" côté vidéo (slot [trailingContent]).
 */
@Composable
fun StudioTopBarCommon(
    modelDisplayName: String,
    onBackClick: () -> Unit,
    onModelClick: () -> Unit,
    onNewClick: () -> Unit,
    modifier: Modifier = Modifier,
    newContentDescription: String = "Nouvelle création",
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AgoraSpacing.Lg, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Fermer",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Surface(
            onClick = onModelClick,
            shape = AgoraRadii.Pill,
            color = Color(0xFF161D26),
            border = BorderStroke(
                AgoraElevation.CardTonal,
                Color.White.copy(alpha = AgoraAlpha.Divider),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelDisplayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = AgoraAlpha.Hint),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onNewClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = newContentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            trailingContent?.invoke(this)
        }
    }
}
