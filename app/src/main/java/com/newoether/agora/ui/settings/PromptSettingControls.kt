package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraDialog
import com.newoether.agora.ui.ds.AgoraTextField

@Composable
fun PromptSettingItem(
    title: String,
    description: String,
    prompt: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(description)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prompt,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        },
        leadingContent = { Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = modifier.clickable { onClick() }
    )
}

@Composable
fun PromptEditDialog(
    title: String,
    initialPrompt: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    AgoraDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.provider_save),
        onConfirm = {
            onSave(draft)
            onDismiss()
        },
        dismissText = stringResource(R.string.provider_cancel),
        text = {
            AgoraTextField(
                value = draft,
                onValueChange = { draft = it },
                label = stringResource(R.string.prompt_content),
                singleLine = false,
                minLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
            )
        },
    )
}
