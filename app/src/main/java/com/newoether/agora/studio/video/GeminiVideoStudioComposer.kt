package com.newoether.agora.studio.video

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.theme.OutfitFamily

@Composable
fun StudioVideoFloatingComposer(
    promptText: String,
    onPromptChange: (String) -> Unit,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    selectedRatio: StudioVideoAspectRatio,
    onRatioChange: (StudioVideoAspectRatio) -> Unit,
    selectedDuration: StudioVideoDuration,
    onDurationChange: (StudioVideoDuration) -> Unit,
    activeModelDisplayName: String = "Omni",
    onOpenModelPicker: () -> Unit = {},
    onVoiceInput: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF131822),
        border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Divider)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
        ) {
            // Top chip indicator: [Clapperboard] Vidéos [X] and [Model Selector Pill]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF222B38),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(AgoraSpacing.Lg)
                        )
                        Text(
                            text = "Vidéos",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer le mode vidéo",
                            tint = Color.White.copy(alpha = AgoraAlpha.Hint),
                            modifier = Modifier.size(AgoraSpacing.Md)
                        )
                    }
                }

                // Multi-provider LLM & Video model selector pill
                Surface(
                    onClick = onOpenModelPicker,
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF222B38),
                    border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
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
                            contentDescription = "Changer de modèle vidéo",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(AgoraSpacing.Lg)
                        )
                    }
                }
            }

            // Middle input line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = promptText,
                    onValueChange = onPromptChange,
                    textStyle = TextStyle(
                        fontFamily = OutfitFamily,
                        fontSize = 15.sp,
                        color = Color.White,
                    ),
                    cursorBrush = SolidColor(Color(0xFFA78BFA)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onGenerate() }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = AgoraSpacing.Xs),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (promptText.isEmpty()) {
                                Text(
                                    text = "Décrivez votre vidéo",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 15.sp,
                                    fontFamily = OutfitFamily
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222B38))
                            .clickable { /* Attach image/asset */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter un élément",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }

                    IconButton(
                        onClick = onVoiceInput,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
                                else if (promptText.isNotBlank()) Color(0xFF8B5CF6)
                                else Color(0xFF242C3A)
                            )
                            .clickable(enabled = !isGenerating, onClick = onGenerate),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(AgoraSpacing.Xl)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Créer la vidéo",
                                tint = if (promptText.isNotBlank()) Color.White else Color.White.copy(alpha = AgoraAlpha.Hint),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Bottom pills row: [Model Pill] [Aspect Ratio Pill] [Duration Pill]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Model pill in bottom row
                Surface(
                    onClick = onOpenModelPicker,
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2634),
                    border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = AgoraAlpha.Disabled)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = activeModelDisplayName,
                            fontSize = 11.sp,
                            color = Color(0xFFA78BFA),
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(AgoraSpacing.Lg)
                        )
                    }
                }

                // Ratio pill
                Surface(
                    onClick = {
                        val next = when (selectedRatio) {
                            StudioVideoAspectRatio.LANDSCAPE -> StudioVideoAspectRatio.PORTRAIT
                            StudioVideoAspectRatio.PORTRAIT -> StudioVideoAspectRatio.SQUARE
                            StudioVideoAspectRatio.SQUARE -> StudioVideoAspectRatio.LANDSCAPE
                        }
                        onRatioChange(next)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2634),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(AgoraSpacing.Lg)
                        )
                        Text(
                            text = selectedRatio.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                // Duration pill
                Surface(
                    onClick = {
                        val next = when (selectedDuration) {
                            StudioVideoDuration.SEC_5 -> StudioVideoDuration.SEC_10
                            StudioVideoDuration.SEC_10 -> StudioVideoDuration.SEC_5
                        }
                        onDurationChange(next)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2634),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(AgoraSpacing.Lg)
                        )
                        Text(
                            text = selectedDuration.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
