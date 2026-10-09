package com.reelcounter.core.design.components

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.CounterHealth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Counter health at a glance. When all is well it is just a small dot and a quiet word; it only
 * gets louder (a filled pill in the warning colour) when something needs fixing.
 */
@Composable
fun HealthPill(
    health: CounterHealth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    val healthy = health == CounterHealth.HEALTHY
    val (label, color) = when (health) {
        CounterHealth.HEALTHY -> "Counting" to extended.successText
        CounterHealth.NOT_ENABLED -> "Counter off" to extended.warningText
        CounterHealth.ENABLED_NOT_RUNNING -> "Tap to fix" to extended.warningText
    }
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .then(
                if (healthy) {
                    Modifier
                } else {
                    Modifier.lookSurface(CircleShape, if (RcTheme.tokens.isSticker) extended.warning else extended.warning.copy(alpha = 0.16f), thin = true)
                },
            )
            .clickable(role = Role.Button, onClickLabel = "Counter status", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(color = color, pulsing = healthy)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                healthy -> scheme.onSurfaceVariant
                RcTheme.tokens.isSticker -> extended.onWarning
                else -> color
            },
        )
    }
}

@Composable
private fun PulseDot(color: Color, pulsing: Boolean) {
    val reducedMotion = LocalReducedMotion.current
    val pulse = if (pulsing && !reducedMotion) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
            label = "pulseValue",
        )
    } else {
        null
    }
    Canvas(Modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        pulse?.value?.let { p ->
            drawCircle(color.copy(alpha = 0.45f * (1f - p)), radius = r * (1f + p * 1.2f))
        }
        drawCircle(color, radius = r * 0.7f)
    }
}

/** A speech bubble that pops in above the brain. Keeps the last text while animating out. */
@Composable
fun SpeechBubble(text: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(text.orEmpty()) }
    if (text != null) shown = text
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 1f)),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val sticker = RcTheme.tokens.isSticker
            val bubbleColor = if (sticker) Color.White else MaterialTheme.colorScheme.inverseSurface
            Box(
                Modifier
                    .lookSurface(RoundedCornerShape(18.dp), bubbleColor, raised = sticker, shadowSize = 3.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (sticker) Color(0xFF111111) else MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Box(
                Modifier
                    .size(width = 18.dp, height = 9.dp)
                    .padding(0.dp),
            ) {
                Surface(
                    shape = GenericShape { size, _ ->
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    },
                    color = if (RcTheme.tokens.isSticker) Color(0xFF111111) else MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.size(width = 18.dp, height = 9.dp),
                ) {}
            }
        }
    }
}

private data class Particle(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val color: Color,
    val width: Float,
    val height: Float,
)

/** A one-shot confetti burst from the top centre. Change [trigger] to fire. */
@Composable
fun ConfettiBurst(trigger: Any?, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(1f) }
    val initial = remember { trigger }
    val particles = remember(trigger) {
        val colors = listOf(Color(0xFFE5B574), Color(0xFFFFD43B), Color(0xFF8FBF9F), Color(0xFF8CCBFF), Color(0xFFFFB27A))
        List(70) {
            Particle(
                angle = (PI / 2 + (Random.nextFloat() - 0.5f) * 2.4f).toFloat(),
                speed = 0.55f + Random.nextFloat() * 0.75f,
                spin = (Random.nextFloat() - 0.5f) * 1_080f,
                color = colors[it % colors.size],
                width = 6f + Random.nextFloat() * 6f,
                height = 10f + Random.nextFloat() * 8f,
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger == initial || reducedMotion) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1_800, easing = LinearEasing))
    }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.3f)
        val scale = size.height
        particles.forEach { p ->
            val vx = cos(p.angle) * p.speed * scale
            val vy = -sin(p.angle) * p.speed * scale
            val x = origin.x + vx * t
            val y = origin.y + vy * t + 0.9f * scale * t * t
            val alpha = (1f - t).coerceIn(0f, 1f)
            val w = p.width.dp.toPx() / 2f
            val h = p.height.dp.toPx() / 2f
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - w / 2f, y - h / 2f),
                    size = Size(w, h),
                )
            }
        }
    }
}
