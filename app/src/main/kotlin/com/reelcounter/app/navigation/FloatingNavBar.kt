package com.reelcounter.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.RcTheme

val NavBarHeight = 68.dp

/**
 * Bottom tab bar with four tabs, Material 3 style: a small pill behind the selected icon. Midnight:
 * a soft amber pill on the dark bar. Sticker: a white bar with an ink top rule and an outlined
 * yellow pill.
 */
@Composable
fun AppNavBar(
    current: TopLevel?,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sticker = RcTheme.tokens.isSticker
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(if (sticker) Color.White else scheme.surfaceContainerLow),
    ) {
        HorizontalDivider(thickness = if (sticker) 2.dp else 1.dp, color = if (sticker) Color(0xFF111111) else scheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(NavBarHeight)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
        ) {
            TopLevel.entries.forEach { item ->
                NavItem(item, selected = item == current, onClick = { onSelect(item) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItem(item: TopLevel, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    val haptics = LocalHapticFeedback.current
    val pill by animateColorAsState(
        when {
            !selected -> Color.Transparent
            sticker -> scheme.primaryContainer
            else -> extended.cta.copy(alpha = 0.18f)
        },
        tween(200),
        label = "navPill",
    )
    val iconColor by animateColorAsState(
        when {
            sticker -> scheme.onSurface
            selected -> extended.cta
            else -> scheme.onSurfaceVariant
        },
        tween(200),
        label = "navIcon",
    )
    val pillShape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                indication = null,
                interactionSource = null,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(pillShape)
                .background(pill)
                .then(if (selected && sticker) Modifier.border(1.5.dp, Color(0xFF111111), pillShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}
