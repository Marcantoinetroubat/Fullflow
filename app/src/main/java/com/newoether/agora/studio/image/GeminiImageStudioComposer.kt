package com.newoether.agora.studio.image

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.theme.OutfitFamily

@Composable
fun StudioFloatingComposer(
    promptText: String,
    onPromptChange: (String) -> Unit,
    attachedBitmap: Bitmap?,
    onRemoveAttached: () -> Unit,
    onPickImage: () -> Unit,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    selectedRatio: StudioAspectRatio,
    onRatioChange: (StudioAspectRatio) -> Unit,
    selectedResolution: StudioResolution,
    onResolutionChange: (StudioResolution) -> Unit,
    burstCount: Int,
    onBurstCountChange: (Int) -> Unit,
    showConfigRow: Boolean,
    onToggleConfigRow: () -> Unit,
    activeModelDisplayName: String = "Nano Banana 2",
    onOpenModelPicker: () -> Unit = {},
    onSavePromptClick: () -> Unit = {},
    onVoiceInput: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
    ) {
        // Multi-provider LLM & Model selection chip directly on the input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AgoraSpacing.Xs),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onOpenModelPicker,
                shape = RoundedCornerShape(36.dp)__,
                color = Color(0xFF192230),
                border = androidx.compose.foundation.BorderStroke(1.dp__, Color(0xFFFFD54F).copy(alpha = 0.5f)),
                shadowElevation = 4.dp__
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = activeModelDisplayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Changer de modèle",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(AgoraSpacing.Lg)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = attachedBitmap != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            attachedBitmap?.let { bmp ->
                Surface(
                    shape = RoundedCornerShape(36.dp)__,
                    color = Color(0xFF141A23),
                    border = androidx.compose.foundation.BorderStroke(1.dp__, Color(0xFFFFD54F).copy(alpha = 0.5f)),
                    modifier = Modifier.padding(bottom = AgoraSpacing.Xxs)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Image de référence",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(36.dp)__)
                        )
                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                        Column {
                            Text(
                                text = stringResource(R.string.image_studio_edit_image_badge),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                            Text(
                                text = "Décrivez les retouches à apporter",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                        IconButton(
                            onClick = onRemoveAttached,
                            modifier = Modifier.size(AgoraSpacing.Xxl)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Retirer",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(AgoraSpacing.Lg)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showConfigRow,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(36.dp)__,
                color = Color(0xFF111720),
                border = androidx.compose.foundation.BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Divider)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioAspectRatio.entries.forEach { ratio ->
                            val isSelected = ratio == selectedRatio
                            Surface(
                                onClick = { onRatioChange(ratio) },
                                shape = RoundedCornerShape(36.dp)__,
                                color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = AgoraAlpha.Subtle),
                            ) {
                                Text(
                                    text = ratio.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioResolution.entries.forEach { res ->
                            val isSelected = res == selectedResolution
                            Surface(
                                onClick = { onResolutionChange(res) },
                                shape = RoundedCornerShape(36.dp)__,
                                color = if (isSelected) Color(0xFF60A5FA) else Color.White.copy(alpha = AgoraAlpha.Subtle),
                            ) {
                                Text(
                                    text = res.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            val nextCount = when (burstCount) {
                                1 -> 2
                                2 -> 4
                                else -> 1
                            }
                            onBurstCountChange(nextCount)
                        },
                        shape = RoundedCornerShape(36.dp)__,
                        color = Color.White.copy(alpha = AgoraAlpha.Subtle)
                    ) {
                        Text(
                            text = "${burstCount}×",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F),
                            modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs)
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(36.dp)__,
            color = Color(0xFF131822),
            border = androidx.compose.foundation.BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Divider)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222B38))
                        .clickable(onClick = onPickImage),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.image_studio_pick_image),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                BasicTextField(
                    value = promptText,
                    onValueChange = onPromptChange,
                    textStyle = TextStyle(
                        fontFamily = OutfitFamily,
                        fontSize = 15.sp,
                        color = Color.White,
                    ),
                    cursorBrush = SolidColor(Color(0xFFFFD54F)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onGenerate() }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = AgoraSpacing.Md),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (promptText.isEmpty()) {
                                Text(
                                    text = if (attachedBitmap != null) {
                                        "Décrivez la retouche souhaitée…"
                                    } else {
                                        stringResource(R.string.image_studio_prompt_hint)
                                    },
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                if (promptText.isNotBlank()) {
                    IconButton(
                        onClick = onSavePromptClick,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(R.string.image_studio_save_prompt_action),
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleConfigRow,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Options de format et résolution",
                        tint = if (showConfigRow) Color(0xFFFFD54F) else Color.White.copy(alpha = AgoraAlpha.Hint),
                        modifier = Modifier.size(AgoraSpacing.Xl)
                    )
                }

                IconButton(
                    onClick = { onVoiceInput?.invoke() },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Saisie vocale",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(AgoraSpacing.Xl)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGenerating) Color(0xFF334155)
                            else if (promptText.isNotBlank()) Color(0xFF2E3B4E)
                            else Color(0xFF202734)
                        )
                        .clickable(enabled = !isGenerating, onClick = onGenerate),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFD54F),
                            strokeWidth = 2.dp__,
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Générer",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
