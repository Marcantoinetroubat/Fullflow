package com.newoether.agora.ui.ds

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog canonique. Remplace les ~50 fun *Dialog (PromptEdit, Citation,
 * ChatRename/Delete, Task pickers, Config, Persona, Mesh, Wand, VideoSlice...).
 *
 * Modèle à 2 actions (confirmer/annuler) : [confirmEnabled]/[dismissEnabled] et
 * [properties] couvrent les états pending, [confirmContent] un confirm custom
 * (ex. loader), [text] tout contenu (dont [AgoraTextField]).
 * Les dialogs à 3+ actions (ex. sélecteur system-prompt Créer/Annuler/Sauver)
 * restent ad-hoc : ne pas les forcer dans ce modèle.
 */
@Composable
fun AgoraDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    dismissEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    confirmContent: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    destructive: Boolean = false,
) {
    AlertDialog(
        modifier = modifier.defaultMinSize(minWidth = 280.dp),
        onDismissRequest = onDismissRequest,
        properties = properties,
        shape = AgoraRadii.Dialog,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = text,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                confirmContent?.invoke() ?: Text(
                    confirmText,
                    color = if (!confirmEnabled) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = AgoraAlpha.Disabled)
                    } else if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismissRequest, enabled = dismissEnabled) { Text(it) }
            }
        },
    )
}
