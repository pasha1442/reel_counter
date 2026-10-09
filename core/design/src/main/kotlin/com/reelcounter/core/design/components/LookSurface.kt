package com.reelcounter.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.RcTheme

/** A hard, offset, unblurred shadow in the shape of the element: the Sticker look's signature. */
fun Modifier.hardShadow(shape: Shape, offset: Dp, color: Color): Modifier =
    if (offset <= 0.dp) {
        this
    } else {
        drawBehind {
            val o = offset.toPx()
            translate(o, o) { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
        }
    }

/**
 * Fill, outline and (when [raised]) the hard shadow for the current look. Midnight gets a flat fill;
 * Sticker gets an ink outline ([thin] for small things like pills) and, for raised things, a hard
 * shadow. Keep [raised] for the few things that should pop: the main button, the selected choice.
 */
@Composable
fun Modifier.lookSurface(
    shape: Shape,
    color: Color,
    raised: Boolean = false,
    outlined: Boolean = true,
    shadowSize: Dp? = null,
    thin: Boolean = false,
): Modifier {
    val tokens = RcTheme.tokens
    val shadow = if (raised && tokens.isSticker) shadowSize ?: tokens.shadow else 0.dp
    val width = if (thin) tokens.thinBorder else tokens.border
    val withBorder = if (outlined && width > 0.dp) Modifier.border(width, tokens.borderColor, shape) else Modifier
    return this
        .hardShadow(shape, shadow, tokens.shadowColor)
        .clip(shape)
        .background(color)
        .then(withBorder)
}

/** Uppercases a label in looks that shout. Both looks now use sentence case; kept for one switch. */
@Composable
fun lookLabel(text: String): String = if (RcTheme.tokens.shoutLabels) text.uppercase() else text
