package com.newoether.agora.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.newoether.agora.R
import com.newoether.agora.model.OpenAiServiceTiers
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraSpacing
import kotlin.math.roundToInt

@Composable
fun OpenAiServiceTierControlPanel(
    enabled: Boolean,
    tier: String,
    onEnabledChange: (Boolean) -> Unit,
    onTierChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val normalizedTier = OpenAiServiceTiers.normalize(tier)
    val tierGate = remember {
        PersistedSliderFeedbackGate(
            initialPersisted = normalizedTier,
            toDisplay = { persisted ->
                OpenAiServiceTiers.indexForTier(persisted).toFloat()
            },
        )
    }
    LaunchedEffect(normalizedTier) { tierGate.reconcile(normalizedTier) }
    val sliderPosition = tierGate.displayed

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = stringResource(R.string.openai_service_tier_title),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.openai_service_tier_title),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.openai_service_tier_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AgoraSpacing.Xxs),
                    )
                }
                Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            Spacer(modifier = Modifier.height(AgoraSpacing.Xxxl))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else AgoraAlpha.Disabled),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = stringResource(R.string.openai_service_tier_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = AgoraSpacing.Xxs),
            )
            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.openai_service_tier_title),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = serviceTierLabel(
                            OpenAiServiceTiers.tierForIndex(
                                sliderPosition.roundToInt(),
                            )
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.openai_service_tier_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AgoraSpacing.Xs),
                )
                Slider(
                    value = sliderPosition,
                    onValueChange = { if (enabled) tierGate.updateFromGesture(it) },
                    onValueChangeFinished = {
                        if (enabled) {
                            val index = sliderPosition
                                .roundToInt()
                                .coerceIn(OpenAiServiceTiers.values.indices)
                            val selectedTier = OpenAiServiceTiers.tierForIndex(index)
                            if (selectedTier == normalizedTier) {
                                tierGate.settleWithoutWrite(normalizedTier, index.toFloat())
                            } else {
                                tierGate.expectPersisted(selectedTier, index.toFloat())
                            }
                            onEnabledChange(true)
                            onTierChange(selectedTier)
                        }
                    },
                    valueRange = 0f..OpenAiServiceTiers.values.lastIndex.toFloat(),
                    steps = OpenAiServiceTiers.values.size - 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AgoraSpacing.Sm),
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
fun openAiServiceTierShortLabel(enabled: Boolean, tier: String): String =
    if (enabled) {
        serviceTierLabel(OpenAiServiceTiers.normalize(tier))
    } else {
        stringResource(R.string.openai_service_tier_off)
    }

@Composable
private fun serviceTierLabel(tier: String): String = when (tier) {
    OpenAiServiceTiers.DEFAULT -> stringResource(R.string.openai_service_tier_default)
    OpenAiServiceTiers.FLEX -> stringResource(R.string.openai_service_tier_flex)
    OpenAiServiceTiers.SCALE -> stringResource(R.string.openai_service_tier_scale)
    OpenAiServiceTiers.PRIORITY -> stringResource(R.string.openai_service_tier_priority)
    OpenAiServiceTiers.FAST -> stringResource(R.string.openai_service_tier_fast)
    OpenAiServiceTiers.ULTRAFAST -> stringResource(R.string.openai_service_tier_ultrafast)
    else -> stringResource(R.string.openai_service_tier_auto)
}
