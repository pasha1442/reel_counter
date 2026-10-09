package com.reelcounter.core.design.brain

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.model.BrainStage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BrainMood {
    /** Face follows the rot stage. */
    AUTO,

    /** Late at night: heavy lids and a yawn. */
    SLEEPY,

    /** Big happy face, for wins like finishing setup. */
    CELEBRATING,
}

/** How the brain is drawn: soft 3D (Midnight) or a 3D sticker with an ink outline (Sticker). */
enum class BrainStyle { SOFT, STICKER }

private val Ink = Color(0xFF2A1714)
private val Tongue = Color(0xFFFF7AA8)
private val MouthDark = Color(0xFF4A1034)

/**
 * The mascot. One number drives it: [rot], 0 (fresh) to 100 (rotten).
 *
 * Change [reactionKey] (for example today's count) to make it wince, change [celebrateKey]
 * for a sparkle burst, and pass [onPoke] to make it tappable. The signature matches a Rive
 * state machine with one number input, so a Rive file can replace this drawing later.
 */
@Composable
fun Brain(
    rot: Float,
    modifier: Modifier = Modifier,
    reactionKey: Any? = null,
    celebrateKey: Any? = null,
    glow: Boolean = false,
    mood: BrainMood = BrainMood.AUTO,
    onPoke: (() -> Unit)? = null,
    contentDescription: String? = null,
    style: BrainStyle = if (RcTheme.tokens.isSticker) BrainStyle.STICKER else BrainStyle.SOFT,
) {
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val rotState = animateFloatAsState(
        targetValue = rot.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 900, easing = FastOutSlowInEasing),
        label = "rot",
    )

    val squash = remember { Animatable(0f) }
    val wince = remember { Animatable(0f) }
    val initialReaction = remember { reactionKey }
    LaunchedEffect(reactionKey) {
        if (reactionKey == initialReaction) return@LaunchedEffect
        launch {
            wince.snapTo(1f)
            delay(if (reducedMotion) 250 else 420)
            wince.animateTo(0f, tween(120))
        }
        if (!reducedMotion) {
            squash.snapTo(1f)
            squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 260f))
        }
    }

    val burst = remember { Animatable(1f) }
    val initialCelebrate = remember { celebrateKey }
    LaunchedEffect(celebrateKey) {
        if (celebrateKey == initialCelebrate) return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(1f, tween(1_100, easing = LinearOutSlowInEasing))
    }

    val blink = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2_400, 5_200))
            blink.animateTo(1f, tween(90))
            blink.animateTo(0f, tween(110))
            if (Random.nextFloat() < 0.2f) {
                delay(120)
                blink.animateTo(1f, tween(80))
                blink.animateTo(0f, tween(100))
            }
        }
    }

    val jiggle = remember { Animatable(0f) }
    // Extra Y-axis turn on a poke (degrees), on top of the slow idle sway.
    val spin = remember { Animatable(0f) }

    val breathe: State<Float>
    val loop: State<Float>
    if (reducedMotion) {
        breathe = remember { mutableFloatStateOf(0.5f) }
        loop = remember { mutableFloatStateOf(0.3f) }
    } else {
        val transition = rememberInfiniteTransition(label = "brain")
        breathe = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe",
        )
        loop = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing)),
            label = "loop",
        )
    }

    val pokeModifier = if (onPoke != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClickLabel = "Poke your brain",
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            scope.launch {
                if (!reducedMotion) {
                    spin.snapTo(0f)
                    spin.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 900
                            28f at 160
                            -20f at 380
                            10f at 600
                            -4f at 780
                        },
                    )
                }
            }
            scope.launch {
                jiggle.snapTo(0f)
                jiggle.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = if (reducedMotion) 1 else 560
                        -10f at 80
                        8f at 190
                        -5f at 300
                        2.5f at 410
                    },
                )
            }
            onPoke()
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .then(pokeModifier)
            .graphicsLayer {
                // A slow turn left and right sells the 3D; a poke adds a quick twist.
                rotationY = (if (reducedMotion) 0f else 9f * sin(loop.value * 2f * PI.toFloat())) + spin.value
                rotationX = if (reducedMotion) 0f else 3f * cos(loop.value * 2f * PI.toFloat() * 2f)
                cameraDistance = 14f * density
            }
            .drawWithCache {
                val crackMeasure = PathMeasure()
                val crackSegment = Path()
                val scratch = Path()
                onDrawBehind {
                    drawBrain(
                        rot = rotState.value,
                        squash = squash.value,
                        wince = wince.value > 0.5f,
                        blink = blink.value,
                        jiggleDegrees = jiggle.value,
                        breathe = breathe.value,
                        loop = loop.value,
                        burst = burst.value,
                        glow = glow,
                        mood = mood,
                        crackMeasure = crackMeasure,
                        crackSegment = crackSegment,
                        scratch = scratch,
                        style = style,
                        turn = (if (reducedMotion) 0f else sin(loop.value * 2f * PI.toFloat())) + spin.value / 30f,
                    )
                }
            },
    )
}

private fun DrawScope.drawBrain(
    rot: Float,
    squash: Float,
    wince: Boolean,
    blink: Float,
    jiggleDegrees: Float,
    breathe: Float,
    loop: Float,
    burst: Float,
    glow: Boolean,
    mood: BrainMood,
    crackMeasure: PathMeasure,
    crackSegment: Path,
    scratch: Path,
    style: BrainStyle,
    turn: Float,
) {
    val unit = min(size.width, size.height) / 2f / 1.45f
    val stage = BrainStage.fromRot(rot)
    val body = BrainArt.bodyColor(rot)
    val bodyLight = lerp(body, Color(0xFFFFF4EE), 0.42f)
    val bodyShade = lerp(body, Color(0xFF3B1A16), 0.42f)
    val outlineColor = if (style == BrainStyle.STICKER) Color(0xFF111111) else lerp(body, Color(0xFF2A1210), 0.55f)
    val grooveColor = lerp(body, Color(0xFF4A1F1A), 0.5f)
    val ridgeColor = lerp(body, Color.White, 0.55f)
    // Face and folds slide a little with the turn, so the brain reads as round, not flat.
    val parallax = 0.07f * turn

    val breatheScale = 1f + 0.022f * (breathe - 0.5f)
    val scaleX = breatheScale * (1f + 0.10f * squash)
    val scaleY = breatheScale * (1f - 0.12f * squash)

    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        if (glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFE27A).copy(alpha = 0.5f), Color.Transparent),
                    center = Offset.Zero,
                    radius = 1.5f,
                ),
                radius = 1.5f,
                center = Offset.Zero,
            )
        }

        // Ground shadow stays put while the brain squashes and jiggles (stickers have their own).
        if (style == BrainStyle.SOFT) {
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(0f, 1.06f),
                    radius = 0.8f * scaleX,
                ),
                topLeft = Offset(-0.85f * scaleX, 0.96f),
                size = Size(1.7f * scaleX, 0.2f),
            )
        }

        rotate(jiggleDegrees, pivot = Offset(0f, 0.7f)) {
            scale(scaleX, scaleY, pivot = Offset(0f, 0.7f)) {
                drawDrips(rot, body, outlineColor, loop)

                if (style == BrainStyle.STICKER) {
                    // Hard ink shadow, then a white die-cut border, like a real sticker.
                    translate(0.1f, 0.12f) {
                        drawPath(BrainArt.outline, Color(0xFF111111))
                        drawPath(BrainArt.outline, Color(0xFF111111), style = Stroke(width = 0.22f, join = StrokeJoin.Round))
                    }
                    drawPath(BrainArt.outline, Color.White, style = Stroke(width = 0.22f, join = StrokeJoin.Round))
                }
                // Lit from the top left: a radial base, a soft occlusion along the bottom, a rim light.
                drawPath(
                    path = BrainArt.outline,
                    brush = Brush.radialGradient(
                        colors = listOf(bodyLight, body, bodyShade),
                        center = Offset(-0.38f + parallax, -0.52f),
                        radius = 1.9f,
                    ),
                )
                clipPath(BrainArt.outline) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, bodyShade.copy(alpha = 0.55f)),
                            startY = 0.05f,
                            endY = 0.8f,
                        ),
                        topLeft = Offset(-1.3f, -1f),
                        size = Size(2.6f, 2f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(1.02f + parallax, -0.12f),
                            radius = 0.55f,
                        ),
                        radius = 0.55f,
                        center = Offset(1.02f + parallax, -0.12f),
                    )
                    // Folds: a dark groove with a lit ridge just above it gives each fold depth.
                    translate(parallax * 0.6f, 0f) {
                        val groove = Stroke(width = 0.052f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val ridge = Stroke(width = 0.024f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        BrainArt.folds.forEach { fold ->
                            drawPath(fold, grooveColor.copy(alpha = 0.85f), style = groove)
                            translate(-0.014f, -0.022f) { drawPath(fold, ridgeColor.copy(alpha = 0.5f), style = ridge) }
                        }
                        drawPath(BrainArt.fissure, grooveColor, style = Stroke(width = 0.06f, cap = StrokeCap.Round))
                        translate(-0.016f, 0f) { drawPath(BrainArt.fissure, ridgeColor.copy(alpha = 0.45f), style = ridge) }
                    }
                    // Wet shine.
                    drawOval(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(-0.5f + parallax, -0.58f),
                            radius = 0.32f,
                        ),
                        topLeft = Offset(-0.82f + parallax, -0.76f),
                        size = Size(0.64f, 0.36f),
                    )
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = 0.035f, center = Offset(-0.58f + parallax, -0.62f))
                    drawCracks(rot, crackMeasure, crackSegment)
                }

                drawPath(
                    BrainArt.outline,
                    outlineColor,
                    style = Stroke(width = if (style == BrainStyle.STICKER) 0.075f else 0.03f, join = StrokeJoin.Round),
                )

                translate(parallax, 0f) {
                    drawCheeks(rot)
                    drawEyes(stage, mood, blink, wince, body, loop, scratch)
                    drawMouth(stage, mood, wince, scratch)
                }
                drawSweat(rot, loop)
            }
        }

        drawFog(rot, loop)
        drawStink(rot, loop, scratch)
        drawSparkles(burst, glow, loop)
    }
}

private fun DrawScope.drawDrips(rot: Float, body: Color, outline: Color, loop: Float) {
    val amount = ((rot - 61f) / 39f).coerceIn(0f, 1f)
    if (amount <= 0f) return
    val anchors = floatArrayOf(-0.44f, 0.02f, 0.46f)
    val maxLength = floatArrayOf(0.26f, 0.36f, 0.22f)
    val offsets = floatArrayOf(0f, 0.37f, 0.71f)
    for (i in anchors.indices) {
        val x = anchors[i]
        val top = BrainArt.bottomAt(x)
        val wave = 0.85f + 0.15f * sin(2f * PI.toFloat() * (loop * 2f + offsets[i]))
        val length = maxLength[i] * amount * wave
        drawRoundRect(
            color = body,
            topLeft = Offset(x - 0.045f, top - 0.1f),
            size = Size(0.09f, length + 0.1f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.045f),
        )
        val phase = (loop * 1.6f + offsets[i]) % 1f
        if (phase < 0.75f) {
            val r = 0.055f * (0.6f + 0.4f * phase / 0.75f)
            drawCircle(body, radius = r, center = Offset(x, top + length))
            drawCircle(outline.copy(alpha = 0.5f), radius = r, center = Offset(x, top + length), style = Stroke(0.02f))
        } else {
            val t = (phase - 0.75f) / 0.25f
            drawCircle(body.copy(alpha = 1f - t), radius = 0.055f, center = Offset(x, top + length + t * 0.55f))
        }
    }
}

private fun DrawScope.drawCracks(rot: Float, measure: PathMeasure, segment: Path) {
    if (rot < 41f) return
    val stroke = Stroke(width = 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Miter)
    BrainArt.cracks.forEachIndexed { i, crack ->
        val progress = ((rot - (41f + i * 9f)) / 22f).coerceIn(0f, 1f)
        if (progress <= 0f) return@forEachIndexed
        measure.setPath(crack, false)
        segment.reset()
        measure.getSegment(0f, measure.length * progress, segment, true)
        drawPath(segment, Ink.copy(alpha = 0.75f), style = stroke)
    }
}

private fun DrawScope.drawCheeks(rot: Float) {
    val health = 1f - (rot / 100f)
    val color = lerp(Color(0xFFB5CF62), Color(0xFFFF6FA5), health)
    val alpha = 0.18f + 0.35f * health
    for (side in intArrayOf(-1, 1)) {
        drawOval(
            color = color.copy(alpha = alpha),
            topLeft = Offset(side * 0.56f - 0.09f, 0.27f),
            size = Size(0.18f, 0.1f),
        )
    }
}

private fun DrawScope.drawEyes(
    stage: BrainStage,
    mood: BrainMood,
    blink: Float,
    wince: Boolean,
    body: Color,
    loop: Float,
    scratch: Path,
) {
    val eyes = listOf(BrainArt.leftEye to BrainArt.leftEyePath, BrainArt.rightEye to BrainArt.rightEyePath)
    val chevron = Stroke(width = 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    if (wince) {
        eyes.forEachIndexed { index, (rect, _) ->
            val side = if (index == 0) -1f else 1f
            val c = rect.center
            scratch.reset()
            scratch.moveTo(c.x + side * 0.08f, c.y - 0.09f)
            scratch.lineTo(c.x - side * 0.07f, c.y)
            scratch.lineTo(c.x + side * 0.08f, c.y + 0.09f)
            drawPath(scratch, Ink, style = chevron)
        }
        return
    }

    if (mood == BrainMood.CELEBRATING) {
        eyes.forEach { (rect, _) ->
            val c = rect.center
            scratch.reset()
            scratch.moveTo(c.x - 0.1f, c.y + 0.04f)
            scratch.quadraticTo(c.x, c.y - 0.12f, c.x + 0.1f, c.y + 0.04f)
            drawPath(scratch, Ink, style = chevron)
        }
        return
    }

    if (stage == BrainStage.ROTTEN && mood == BrainMood.AUTO) {
        eyes.forEachIndexed { index, (rect, _) ->
            drawOval(Color.White, rect.topLeft, rect.size)
            val c = rect.center
            val spin = loop * 2f * PI.toFloat() * 3f * (if (index == 0) 1f else -1f)
            scratch.reset()
            for (step in 0..40) {
                val t = step / 40f
                val angle = t * 2.5f * 2f * PI.toFloat() + spin
                val r = 0.12f * t
                val p = Offset(c.x + cos(angle) * r, c.y + sin(angle) * r)
                if (step == 0) scratch.moveTo(p.x, p.y) else scratch.lineTo(p.x, p.y)
            }
            drawPath(scratch, Ink, style = Stroke(width = 0.028f, cap = StrokeCap.Round))
            drawOval(Ink.copy(alpha = 0.6f), rect.topLeft, rect.size, style = Stroke(0.025f))
        }
        drawWorriedBrows(scratch, slant = 0.08f)
        return
    }

    val stageLid = when {
        mood == BrainMood.SLEEPY -> 0.6f
        stage == BrainStage.FRESH -> 0f
        stage == BrainStage.FOGGY -> 0.32f
        stage == BrainStage.CRACKING -> 0.14f
        else -> 0.44f
    }
    val pupilRadius = when (stage) {
        BrainStage.FRESH -> 0.085f
        BrainStage.FOGGY -> 0.08f
        BrainStage.CRACKING -> 0.062f
        else -> 0.075f
    }
    val pupilDrop = when (stage) {
        BrainStage.FRESH -> 0.02f
        BrainStage.FOGGY -> 0.035f
        BrainStage.CRACKING -> 0f
        else -> 0.055f
    }
    val lid = maxOf(stageLid, blink)

    eyes.forEach { (rect, path) ->
        drawOval(Color.White, rect.topLeft, rect.size)
        val pupil = Offset(rect.center.x + 0.01f, rect.center.y + pupilDrop)
        drawCircle(Ink, radius = pupilRadius, center = pupil)
        drawCircle(Color.White, radius = 0.028f, center = pupil + Offset(-0.03f, -0.035f))
        if (stage == BrainStage.FRESH) drawCircle(Color.White, radius = 0.014f, center = pupil + Offset(0.035f, 0.03f))
        if (lid > 0f) {
            clipPath(path) {
                drawRect(body, topLeft = Offset(rect.left - 0.01f, rect.top - 0.01f), size = Size(rect.width + 0.02f, rect.height * lid + 0.01f))
            }
            val lidY = rect.top + rect.height * lid
            if (lid < 0.98f) {
                drawLine(Ink.copy(alpha = 0.8f), Offset(rect.left + 0.02f, lidY), Offset(rect.right - 0.02f, lidY), strokeWidth = 0.025f, cap = StrokeCap.Round)
            }
        }
        drawOval(Ink.copy(alpha = 0.6f), rect.topLeft, rect.size, style = Stroke(0.025f))
    }

    when (stage) {
        BrainStage.CRACKING -> drawWorriedBrows(scratch, slant = 0.06f)
        BrainStage.MELTING -> drawWorriedBrows(scratch, slant = 0.1f)
        else -> Unit
    }
}

private fun DrawScope.drawWorriedBrows(scratch: Path, slant: Float) {
    for (side in intArrayOf(-1, 1)) {
        scratch.reset()
        scratch.moveTo(side * 0.48f, -0.12f)
        scratch.lineTo(side * 0.2f, -0.12f - slant)
        drawPath(scratch, Ink, style = Stroke(width = 0.045f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMouth(stage: BrainStage, mood: BrainMood, wince: Boolean, scratch: Path) {
    val line = Stroke(width = 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when {
        wince -> {
            drawRoundRect(Color.White, topLeft = Offset(-0.14f, 0.38f), size = Size(0.28f, 0.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.04f))
            for (x in floatArrayOf(-0.05f, 0.05f)) drawLine(Ink, Offset(x, 0.38f), Offset(x, 0.48f), strokeWidth = 0.02f)
            drawLine(Ink, Offset(-0.14f, 0.43f), Offset(0.14f, 0.43f), strokeWidth = 0.02f)
            drawRoundRect(Ink, topLeft = Offset(-0.14f, 0.38f), size = Size(0.28f, 0.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.04f), style = Stroke(0.03f))
        }
        mood == BrainMood.SLEEPY -> {
            drawOval(MouthDark, topLeft = Offset(-0.1f, 0.35f), size = Size(0.2f, 0.24f))
            drawOval(Tongue, topLeft = Offset(-0.06f, 0.48f), size = Size(0.12f, 0.08f))
        }
        mood == BrainMood.CELEBRATING || stage == BrainStage.FRESH -> {
            drawPath(BrainArt.happyMouth, MouthDark)
            clipPath(BrainArt.happyMouth) { drawCircle(Tongue, radius = 0.09f, center = Offset(0f, 0.58f)) }
            drawPath(BrainArt.happyMouth, Ink, style = Stroke(width = 0.03f, join = StrokeJoin.Round))
        }
        stage == BrainStage.FOGGY -> {
            scratch.reset()
            scratch.moveTo(-0.15f, 0.41f)
            scratch.quadraticTo(0f, 0.52f, 0.15f, 0.41f)
            drawPath(scratch, Ink, style = line)
        }
        stage == BrainStage.CRACKING -> {
            scratch.reset()
            scratch.moveTo(-0.18f, 0.45f)
            scratch.quadraticTo(-0.09f, 0.39f, 0f, 0.45f)
            scratch.quadraticTo(0.09f, 0.51f, 0.18f, 0.45f)
            drawPath(scratch, Ink, style = line)
        }
        stage == BrainStage.MELTING -> {
            drawOval(MouthDark, topLeft = Offset(-0.07f, 0.38f), size = Size(0.14f, 0.17f))
            drawOval(Ink, topLeft = Offset(-0.07f, 0.38f), size = Size(0.14f, 0.17f), style = Stroke(0.03f))
        }
        else -> {
            scratch.reset()
            scratch.moveTo(-0.18f, 0.5f)
            scratch.quadraticTo(0f, 0.36f, 0.18f, 0.5f)
            drawPath(scratch, Ink, style = line)
            drawRoundRect(Tongue, topLeft = Offset(0.01f, 0.44f), size = Size(0.1f, 0.16f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.05f))
            drawLine(Color(0xFFD9467A), Offset(0.06f, 0.47f), Offset(0.06f, 0.56f), strokeWidth = 0.015f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawSweat(rot: Float, loop: Float) {
    if (rot < 45f || rot > 82f) return
    val bob = 0.02f * sin(loop * 2f * PI.toFloat() * 4f)
    translate(0.86f, -0.26f + bob) {
        drawPath(BrainArt.sweatDrop, Color(0xFF7DD3FC))
        drawCircle(Color.White.copy(alpha = 0.7f), radius = 0.018f, center = Offset(-0.02f, -0.01f))
    }
}

/**
 * Brain fog: a soft, slowly drifting mist over the top of the head (no cartoon cloud puffs, which
 * read as smoke). It thickens through Foggy and clears again once cracks take over.
 */
private fun DrawScope.drawFog(rot: Float, loop: Float) {
    val amount = smoothstep(15f, 28f, rot) * (1f - smoothstep(45f, 62f, rot))
    if (amount <= 0.01f) return
    val mist = Color(0xFFE6E2DA)
    val rows = floatArrayOf(-0.62f, -0.36f)
    val phases = floatArrayOf(0f, 0.5f)
    for (i in rows.indices) {
        val x = 0.45f * sin(2f * PI.toFloat() * (loop + phases[i]))
        val center = Offset(x, rows[i])
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(mist.copy(alpha = 0.34f * amount), mist.copy(alpha = 0.12f * amount), Color.Transparent),
                center = center,
                radius = 0.85f,
            ),
            topLeft = Offset(center.x - 1.1f, center.y - 0.42f),
            size = Size(2.2f, 0.84f),
        )
    }
}

private fun DrawScope.drawStink(rot: Float, loop: Float, scratch: Path) {
    val amount = ((rot - 81f) / 19f).coerceIn(0f, 1f)
    if (amount <= 0f) return
    val stink = Color(0xFFA3B86A)
    floatArrayOf(-0.24f, 0.24f).forEachIndexed { i, x ->
        val phase = (loop * 3f + i * 0.5f) % 1f
        val baseY = -0.92f - phase * 0.35f
        scratch.reset()
        for (step in 0..12) {
            val t = step / 12f
            val px = x + 0.05f * sin(t * 3f * PI.toFloat())
            val py = baseY - t * 0.35f
            if (step == 0) scratch.moveTo(px, py) else scratch.lineTo(px, py)
        }
        val alpha = sin(PI.toFloat() * phase) * amount * 0.7f
        drawPath(scratch, stink.copy(alpha = alpha), style = Stroke(width = 0.03f, cap = StrokeCap.Round))
    }
    // One fly orbiting the head.
    for (i in 0..0) {
        val angle = 2f * PI.toFloat() * (loop * (5f + i * 2f)) + i * 2.1f
        val center = Offset(cos(angle) * 1.12f, -0.35f + sin(angle) * 0.32f)
        val flap = 0.5f + 0.5f * sin(loop * 2f * PI.toFloat() * 120f)
        drawOval(Color.White.copy(alpha = 0.55f * amount), topLeft = center + Offset(-0.07f, -0.06f - 0.02f * flap), size = Size(0.07f, 0.045f))
        drawOval(Color.White.copy(alpha = 0.55f * amount), topLeft = center + Offset(0f, -0.06f - 0.02f * flap), size = Size(0.07f, 0.045f))
        drawCircle(Color(0xFF3F3F46).copy(alpha = amount), radius = 0.035f, center = center)
    }
}

private fun DrawScope.drawSparkles(burst: Float, glow: Boolean, loop: Float) {
    val colors = listOf(Color(0xFFFDE047), Color(0xFFE5B574), Color(0xFFFFF1C2))
    if (burst < 1f) {
        for (i in 0 until 10) {
            val angle = i * (2f * PI.toFloat() / 10f) + 0.3f
            val r = 0.9f + burst * 0.8f
            val center = Offset(cos(angle) * r, sin(angle) * r * 0.85f)
            val s = 0.1f * (1f - burst * 0.5f)
            withTransform({
                translate(center.x, center.y)
                scale(s, s, pivot = Offset.Zero)
            }) {
                drawPath(BrainArt.sparkle, colors[i % colors.size].copy(alpha = 1f - burst))
            }
        }
    }
    if (glow) {
        val spots = listOf(Offset(-1.05f, -0.75f), Offset(1.1f, -0.55f), Offset(0.95f, 0.65f))
        spots.forEachIndexed { i, spot ->
            val twinkle = 0.5f + 0.5f * sin(2f * PI.toFloat() * (loop * 4f + i * 0.3f))
            val s = 0.05f + 0.04f * twinkle
            withTransform({
                translate(spot.x, spot.y)
                scale(s, s, pivot = Offset.Zero)
            }) {
                drawPath(BrainArt.sparkle, colors[0].copy(alpha = 0.4f + 0.6f * twinkle))
            }
        }
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
