package com.newoether.agora.ui.chat.fullflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.theme.OutfitFamily

import androidx.compose.ui.res.painterResource

enum class FullFlowTab {
    HOME,
    WORKSPACE,
    GEN_MAIL,
    PLUS,
    PROFILE,
}

@Composable
fun FullFlowBottomBar(
    currentTab: FullFlowTab,
    onTabSelected: (FullFlowTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0D10).copy(alpha = 0.96f))
            .navigationBarsPadding()
    ) {
        // Subtle top hairline divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FullFlowBottomBarItem(
                icon = Icons.Default.Home,
                label = stringResource(R.string.tab_home),
                selected = currentTab == FullFlowTab.HOME,
                onClick = { onTabSelected(FullFlowTab.HOME) }
            )
            FullFlowBottomBarItemWithPainter(
                painter = painterResource(R.drawable.ic_google_drive),
                label = "Drive",
                selected = currentTab == FullFlowTab.WORKSPACE,
                onClick = { onTabSelected(FullFlowTab.WORKSPACE) }
            )
            FullFlowBottomBarItem(
                icon = Icons.Default.Email,
                label = stringResource(R.string.tab_genmail),
                selected = currentTab == FullFlowTab.GEN_MAIL,
                onClick = { onTabSelected(FullFlowTab.GEN_MAIL) }
            )
            FullFlowBottomBarItem(
                icon = Icons.Default.MoreHoriz,
                label = stringResource(R.string.tab_plus),
                selected = currentTab == FullFlowTab.PLUS,
                onClick = { onTabSelected(FullFlowTab.PLUS) }
            )
            FullFlowBottomBarItem(
                icon = Icons.Default.AccountCircle,
                label = stringResource(R.string.tab_profile),
                selected = currentTab == FullFlowTab.PROFILE,
                onClick = { onTabSelected(FullFlowTab.PROFILE) }
            )
        }
    }
}

@Composable
private fun FullFlowBottomBarItemWithPainter(
    painter: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.55f)
    val color = if (selected) activeColor else inactiveColor

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .defaultMinSize(minWidth = 52.dp, minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = Color.Unspecified,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontFamily = OutfitFamily,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun FullFlowBottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.55f)
    val color = if (selected) activeColor else inactiveColor

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontFamily = OutfitFamily,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}
