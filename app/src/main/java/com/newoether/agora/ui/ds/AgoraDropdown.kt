package com.newoether.agora.ui.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Dropdown canonique : wrapper M3 [DropdownMenu] avec tokens DS appliqués
 * (shape `AgoraRadii.Sm`, container `surfaceContainer`, tonal `AgoraSpacing.Sm`).
 *
 * Remplace les ~48 `DropdownMenu(...)` ad-hoc qui répètent manuellement
 * `containerColor = surfaceContainer` + `shape = RoundedCornerShape(12.dp)` +
 * `tonalElevation = 16.dp` à travers settings, chat, tasks, studios.
 *
 * Usage :
 * ```
 * AgoraDropdown(expanded = show, onDismiss = { show = false }) {
 *     AgoraDropdownItem(text = "Option", onClick = { ... })
 * }
 * ```
 */
@Composable
fun AgoraDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = AgoraSpacing.Sm,
        shape = AgoraRadii.Sm,
    ) {
        content()
    }
}

/**
 * Item canonique pour [AgoraDropdown] : wrapper [DropdownMenuItem] sans tokens
 * supplémentaires (M3 gère déjà le bon contraste). Sert de slot stable pour
 * leadingIcon/trailingIcon/onClick.
 */
@Composable
fun AgoraDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    DropdownMenuItem(
        text = { Text(text) },
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        onClick = onClick,
    )
}
