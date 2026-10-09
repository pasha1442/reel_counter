package com.reelcounter.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.brain.nameRes
import com.reelcounter.core.design.theme.RcMotion
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.BrainStage
import com.reelcounter.core.model.ReelApp

/**
 * A rounded block for content that belongs together. Midnight draws a flat tinted card, Sticker an
 * outlined white card. [raised] adds Sticker's hard shadow: keep it for the one thing that should pop.
 */
@Composable
fun RcCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    raised: Boolean = false,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(RcSpacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, RcMotion.press(), label = "cardPress")
    val click = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .lookSurface(shape, color, raised = raised)
            .then(click)
            .padding(contentPadding),
        content = content,
    )
}

/** A hairline between rows inside a card, inset to line up with the row text. */
@Composable
fun ListDivider(modifier: Modifier = Modifier, inset: Dp = RcSpacing.card) {
    val sticker = RcTheme.tokens.isSticker
    HorizontalDivider(
        modifier.padding(horizontal = inset),
        thickness = if (sticker) 1.5.dp else 1.dp,
        color = if (sticker) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun StageChip(stage: BrainStage, modifier: Modifier = Modifier) {
    val extended = RcTheme.extended
    val color by animateColorAsState(extended.stage(stage), label = "stage")
    Box(modifier.lookSurface(CircleShape, color, thin = true)) {
        Text(
            text = stringResource(stage.nameRes()),
            style = MaterialTheme.typography.labelMedium,
            color = extended.onStage(stage),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    Box(
        modifier.lookSurface(
            CircleShape,
            if (sticker) extended.cta else extended.cta.copy(alpha = 0.16f),
            thin = true,
        ),
    ) {
        Text(
            "Pro",
            style = MaterialTheme.typography.labelSmall,
            color = if (sticker) extended.onCta else extended.cta,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * A soft rounded square holding an icon. Neutral by default; pass [tint] only when the colour
 * means something (a warning, a success).
 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color? = null,
) {
    val sticker = RcTheme.tokens.isSticker
    val neutral = tint == MaterialTheme.colorScheme.onSurface
    val fill = background ?: when {
        sticker -> MaterialTheme.colorScheme.surfaceContainerHighest
        neutral -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> tint.copy(alpha = 0.14f)
    }
    val iconColor = if (sticker && background == null) MaterialTheme.colorScheme.onSurface else tint
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(size * 0.5f))
    }
}

/** The app's real launcher icon (falls back to a drawn badge when the app isn't installed). */
@Composable
fun AppTile(app: ReelApp, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    AppIcon(app, modifier, size)
}

/** A quiet section title. Sections are separated by space, not by boxes. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = RcSpacing.section, bottom = RcSpacing.item),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** 44dp icon button: a soft circle (Midnight) or an outlined square (Sticker). */
@Composable
fun RcIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val sticker = RcTheme.tokens.isSticker
    val shape = if (sticker) RoundedCornerShape(12.dp) else CircleShape
    Box(
        modifier
            .size(44.dp)
            .lookSurface(shape, color)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/**
 * The large title at the top of a tab, with room for one or two actions on the right. Every tab
 * uses it, so titles sit in the same place on every screen.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 72.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

/** The bar on screens opened on top of a tab: back, title, optional actions. */
@Composable
fun RcTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RcIconButton(
                RcIcons.Back,
                "Back",
                onBack,
                color = if (RcTheme.tokens.isSticker) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** A small rounded label: "7-day badge in 4", "+7 over". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
    dot: Color? = null,
) {
    Row(
        modifier
            .lookSurface(CircleShape, color, thin = true)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

/** A "number + small label" pair for stat rows. No box of its own: put a row of them in one card. */
@Composable
fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The segmented "Day | Week" style toggle. [equalWidths] splits a full-width toggle evenly. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equalWidths: Boolean = false,
) {
    val sticker = RcTheme.tokens.isSticker
    val scheme = MaterialTheme.colorScheme
    val outer = if (sticker) RoundedCornerShape(12.dp) else CircleShape
    val inner = if (sticker) RoundedCornerShape(9.dp) else CircleShape
    Row(
        modifier
            .lookSurface(outer, scheme.surfaceContainer, thin = true)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            // Midnight marks the choice with a lifted neutral surface; Sticker with the yellow.
            val selectedFill = if (sticker) scheme.primaryContainer else scheme.surfaceContainerHighest
            val bg by animateColorAsState(if (on) selectedFill else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .weight(1f, fill = equalWidths)
                    .height(36.dp)
                    .then(if (on) Modifier.lookSurface(inner, bg, outlined = sticker, thin = true) else Modifier)
                    .clip(inner)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        on && sticker -> scheme.onPrimaryContainer
                        on -> scheme.onSurface
                        else -> scheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
