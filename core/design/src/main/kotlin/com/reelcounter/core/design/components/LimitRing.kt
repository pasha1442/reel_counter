package com.reelcounter.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.design.theme.RcMotion
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.BrainStage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Today's share of the limit as a ring around [content] (the brain). It fills clockwise from the
 * top in the colour of [stage] (the brain's own stage, so ring, brain and label always agree), has dots where the 50% and 80% nudges fire, and flashes a soft
 * halo each time the count goes up. Past the limit the ring stays full.
 */
@Composable
fun LimitRing(
    count: Int,
    limit: Int,
    stage: BrainStage,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val fraction = if (limit <= 0) 1f else (count / limit.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (reducedMotion) RcMotion.change(0) else RcMotion.enter(RcMotion.LONG),
        label = "ring",
    )
    val color by animateColorAsState(RcTheme.extended.stage(stage), RcMotion.change(RcMotion.MEDIUM), label = "ringColor")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickIdle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val tickPassed = MaterialTheme.colorScheme.background

    // A halo that swells and fades when a reel is added.
    val pulse = remember { Animatable(0f) }
    val initialCount = remember { count }
    LaunchedEffect(count) {
        if (count == initialCount || reducedMotion) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, RcMotion.exit(RcMotion.LONG + 300))
    }

    Box(
        modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                stateDescription = if (count > limit) "$count of $limit reels, ${count - limit} over" else "$count of $limit reels"
            }
            .drawBehind {
                val stroke = strokeWidth.toPx()
                val diameter = min(size.width, size.height) - stroke - 16.dp.toPx()
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                if (pulse.value > 0f) {
                    drawArc(
                        color = color.copy(alpha = 0.35f * pulse.value),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke * (1f + 1.4f * pulse.value)),
                    )
                }
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                if (progress > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                val radius = diameter / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                for (mark in floatArrayOf(0.5f, 0.8f)) {
                    val angle = (-90f + 360f * mark) * (PI / 180f).toFloat()
                    drawCircle(
                        color = if (progress >= mark) tickPassed.copy(alpha = 0.6f) else tickIdle,
                        radius = stroke * 0.17f,
                        center = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle)),
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
