package com.newoether.agora.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.newoether.agora.ui.ds.AgoraElevation
import com.newoether.agora.ui.ds.AgoraSpacing

/**
 * Section title matching SettingsGroup's label style.
 * [firstInPage] = true for the first section on the page (no extra top gap);
 * subsequent sections get a 24dp gap above to match SettingsGroup's bottom padding.
 */
@Composable
internal fun SectionLabel(text: String, firstInPage: Boolean) {
    val topPadding = if (firstInPage) AgoraSpacing.Md else AgoraSpacing.Xxxl
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = AgoraSpacing.Xxxl, end = AgoraSpacing.Lg, top = topPadding, bottom = AgoraSpacing.Md)
    )
}

/**
 * A single Surface card matching SettingsGroup's style.
 * [addTopGap] adds a 2dp gap above when true (for items after the first in a group).
 */
@Composable
internal fun CardSurface(
    shape: Shape,
    addTopGap: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = AgoraElevation.CardTonal,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AgoraSpacing.Lg)
            .then(if (addTopGap) Modifier.padding(top = AgoraSpacing.Xxs) else Modifier)
    ) {
        content()
    }
}
