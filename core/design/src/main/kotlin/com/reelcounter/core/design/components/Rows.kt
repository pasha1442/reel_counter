package com.reelcounter.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme
import kotlinx.coroutines.delay

/**
 * A full-width row with a switch. The whole row is the touch target. The icon is a plain glyph,
 * not a coloured tile: in a list, colour on every row is noise. [iconTint] is for meaning only.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = RcSpacing.card, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        RowText(title, subtitle, badge, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A full-width tappable row that opens something. */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingText: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    badge: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = RcSpacing.card, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        RowText(title, subtitle, badge, Modifier.weight(1f), titleColor)
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.bodyMedium, color = trailingColor)
            Spacer(Modifier.width(4.dp))
        }
        Icon(RcIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowText(
    title: String,
    subtitle: String?,
    badge: (@Composable () -> Unit)?,
    modifier: Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = titleColor)
            badge?.invoke()
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Big minus / value / plus control for limits. Tap to step once; press and hold to keep stepping,
 * faster the longer you hold (with a haptic tick per step). TalkBack users get the same single
 * step per activation.
 */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int,
    modifier: Modifier = Modifier,
    unit: String = "reels",
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    fun change(delta: Int): Boolean {
        val next = (current + delta).coerceIn(range)
        if (next == current) return false
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onChange(next)
        return true
    }
    Row(
        modifier.semantics(mergeDescendants = true) { stateDescription = "$value $unit" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        StepButton(RcIcons.Minus, "Decrease", enabled = value > range.first) { change(-step) }
        Column(Modifier.widthIn(min = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedCounter(value = value, style = MaterialTheme.typography.displaySmall)
            Text(
                unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        StepButton(RcIcons.Add, "Increase", enabled = value < range.last) { change(step) }
    }
}

/** [onStep] returns false once the end of the range is reached, which stops a hold. */
@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onStep: () -> Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var held by remember { mutableStateOf(false) }
    val step by rememberUpdatedState(onStep)
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        // A hold that ran into the end of the range never got its release click: start clean.
        held = false
        delay(HOLD_START_MS)
        held = true
        var interval = HOLD_FIRST_INTERVAL_MS
        while (step()) {
            delay(interval)
            interval = (interval * 0.85f).toLong().coerceAtLeast(HOLD_MIN_INTERVAL_MS)
        }
    }
    val sticker = RcTheme.tokens.isSticker
    val shape = if (sticker) RoundedCornerShape(14.dp) else CircleShape
    Box(
        Modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .lookSurface(shape, if (sticker) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
            ) {
                // A hold already stepped; the release that ends it is not another tap.
                if (held) held = false else step()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

private const val HOLD_START_MS = 400L
private const val HOLD_FIRST_INTERVAL_MS = 160L
private const val HOLD_MIN_INTERVAL_MS = 45L
