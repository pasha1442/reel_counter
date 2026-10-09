package com.reelcounter.core.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.design.theme.RcMotion
import com.reelcounter.core.design.theme.RcTheme
import kotlin.math.max

/** How much of today's limit is used: the "on track" colour, then the "over" colour past the limit. */
@Composable
fun LimitBar(
    count: Int,
    limit: Int,
    modifier: Modifier = Modifier,
    height: Dp = if (RcTheme.tokens.isSticker) 12.dp else 8.dp,
) {
    val fraction = if (limit <= 0) 1f else count / limit.toFloat()
    val progress = animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "limit")
    val extended = RcTheme.extended
    val tokens = RcTheme.tokens
    val color by animateColorAsState(if (count > limit) extended.chartOver else extended.chartUnder, label = "limitColor")
    val track = if (tokens.isSticker) Color.White else extended.track
    val border = tokens.thinBorder

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                contentDescription = "$count of $limit reels"
            },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = radius)
        val p = progress.value
        if (p > 0f) {
            drawRoundRect(color, size = Size(max(size.height, size.width * p), size.height), cornerRadius = radius)
        }
        if (border > 0.dp) {
            val b = border.toPx()
            drawRoundRect(
                tokens.borderColor,
                topLeft = Offset(b / 2f, b / 2f),
                size = Size(size.width - b, size.height - b),
                cornerRadius = CornerRadius((size.height - b) / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(b),
            )
        }
    }
}

/**
 * A number whose digits roll up when it grows and down when it shrinks. Digits use tabular figures
 * where the font has them, so they share one width and the number never shifts sideways. Lexend
 * Mega has none: there each digit keeps its own width and the change in width is animated. (Fixed
 * slots as wide as the widest digit left visible gaps in that very wide font.)
 */
@Composable
fun AnimatedCounter(
    value: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val reducedMotion = LocalReducedMotion.current
    val previous = remember { mutableIntStateOf(value) }
    val increasing = value >= previous.intValue
    SideEffect { previous.intValue = value }
    val digitStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }

    val digits = value.toString()
    Row(modifier.clearAndSetSemantics { contentDescription = digits }) {
        digits.forEachIndexed { index, digit ->
            key(digits.length - index) {
                AnimatedContent(
                    targetState = digit,
                    transitionSpec = {
                        if (reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val direction = if (increasing) 1 else -1
                            (slideInVertically(RcMotion.enter(RcMotion.SHORT + 60)) { it * direction } + fadeIn(RcMotion.enter(RcMotion.SHORT))) togetherWith
                                (slideOutVertically(RcMotion.exit(RcMotion.SHORT + 60)) { -it * direction } + fadeOut(RcMotion.exit(RcMotion.SHORT))) using
                                SizeTransform(clip = true)
                        }
                    },
                    contentAlignment = Alignment.Center,
                    label = "digit",
                ) { d ->
                    Text(d.toString(), style = digitStyle, color = color)
                }
            }
        }
    }
}
