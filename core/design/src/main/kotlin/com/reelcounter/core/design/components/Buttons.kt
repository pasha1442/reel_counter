package com.reelcounter.core.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.RcTheme

enum class RcButtonStyle {
    /** Main action on a screen. */
    Primary,

    /** High-energy action: start the trial, turn counting on. */
    Cta,

    /** Equal-weight alternative ("Maybe later", "Not now"). */
    Secondary,

    /** Low-emphasis outline. */
    Ghost,
}

/**
 * 52dp button. Midnight: a pill. Sticker: an outlined block; only the main (Primary/Cta) button
 * gets the hard shadow, so one thing per screen pops. Press feedback is a scale transform (no
 * layout shift) plus a light haptic.
 */
@Composable
fun RcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RcButtonStyle = RcButtonStyle.Primary,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(150), label = "press")
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val extended = RcTheme.extended
    val tokens = RcTheme.tokens

    val (container, content) = when (style) {
        RcButtonStyle.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        RcButtonStyle.Cta -> extended.cta to extended.onCta
        RcButtonStyle.Secondary -> (if (tokens.isSticker) scheme.surfaceContainer else Color.Transparent) to scheme.onSurface
        RcButtonStyle.Ghost -> Color.Transparent to scheme.onSurface
    }
    val shape = if (tokens.isSticker) RoundedCornerShape(14.dp) else CircleShape
    val raised = tokens.isSticker && active(enabled, loading) && (style == RcButtonStyle.Primary || style == RcButtonStyle.Cta)
    val outlineForMidnight = !tokens.isSticker && style == RcButtonStyle.Secondary
    val active = enabled && !loading

    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (active || loading) 1f else 0.45f)
            .lookSurface(shape, container, raised = raised, outlined = style != RcButtonStyle.Ghost)
            .then(
                if (outlineForMidnight) {
                    Modifier.border(1.dp, scheme.outline, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = content)
            } else {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
                    Spacer(Modifier.width(10.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun active(enabled: Boolean, loading: Boolean) = enabled && !loading
