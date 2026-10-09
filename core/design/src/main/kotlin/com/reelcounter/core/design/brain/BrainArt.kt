package com.reelcounter.core.design.brain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Brain geometry in unit space: the body spans x in -1..1 and y in about -0.8..0.7,
 * centred on the origin. Brain.kt scales it to the canvas.
 */
internal object BrainArt {
    private const val RX = 1.0f
    private const val RY = 0.78f
    private const val BOTTOM_FLATTEN = 0.82f

    val outline: Path by lazy {
        val bumps = 12
        val start = -PI / 2
        fun point(theta: Double, k: Float): Offset {
            val x = (cos(theta) * RX * k).toFloat()
            var y = (sin(theta) * RY * k).toFloat()
            if (y > 0f) y *= BOTTOM_FLATTEN
            return Offset(x, y)
        }
        Path().apply {
            val first = point(start, 1f)
            moveTo(first.x, first.y)
            for (i in 0 until bumps) {
                val a0 = start + 2 * PI * i / bumps
                val a1 = start + 2 * PI * (i + 1) / bumps
                val control = point((a0 + a1) / 2, 1.17f)
                val end = point(a1, 1f)
                quadraticTo(control.x, control.y, end.x, end.y)
            }
            close()
        }
    }

    /** The groove between the two hemispheres. */
    val fissure: Path by lazy {
        Path().apply {
            moveTo(0f, -0.8f)
            cubicTo(0.07f, -0.62f, -0.07f, -0.42f, 0.02f, -0.24f)
        }
    }

    private val leftGyri = listOf(
        listOf(Offset(-0.80f, -0.26f), Offset(-0.64f, -0.46f), Offset(-0.45f, -0.35f), Offset(-0.27f, -0.53f)),
        listOf(Offset(-0.58f, -0.70f), Offset(-0.38f, -0.63f), Offset(-0.21f, -0.72f)),
        listOf(Offset(-0.95f, 0.04f), Offset(-0.82f, -0.11f), Offset(-0.66f, -0.05f), Offset(-0.53f, -0.19f)),
        listOf(Offset(-0.90f, 0.36f), Offset(-0.78f, 0.25f), Offset(-0.70f, 0.33f)),
    )

    val gyri: List<Path> by lazy {
        leftGyri.map(::smoothPath) + leftGyri.map { line -> smoothPath(line.map { Offset(-it.x, it.y) }) }
    }

    /**
     * The folds (gyri), drawn procedurally: wavy rows across each hemisphere, and short side rows
     * that stay clear of the face. Deterministic, so the brain looks the same every launch.
     */
    val folds: List<Path> by lazy {
        val result = mutableListOf<Path>()
        val top = listOf(-0.7f to 0.0f, -0.52f to 1.3f, -0.34f to 2.4f, -0.17f to 3.1f)
        for (side in intArrayOf(-1, 1)) {
            top.forEachIndexed { row, (y0, phase) ->
                val p = side * 0.7f + phase
                result += wavy(xFrom = 0.1f, xTo = 1.02f - row * 0.03f, side = side, y0 = y0, phase = p, amp = 0.05f)
            }
            // Sides, beside and below the eyes.
            listOf(0.03f to 0.4f, 0.2f to 1.7f, 0.37f to 2.9f).forEach { (y0, phase) ->
                result += wavy(xFrom = 0.62f, xTo = 1.04f, side = side, y0 = y0, phase = phase + side, amp = 0.04f)
            }
            // A few short vertical loops make it read as folds, not stripes.
            listOf(0.32f to -0.56f, 0.6f to -0.42f, 0.82f to -0.26f, 0.45f to -0.78f).forEach { (x, y) ->
                result += Path().apply {
                    moveTo(side * x, y - 0.1f)
                    cubicTo(side * (x + 0.07f), y - 0.03f, side * (x - 0.06f), y + 0.03f, side * (x + 0.02f), y + 0.1f)
                }
            }
        }
        result
    }

    private fun wavy(xFrom: Float, xTo: Float, side: Int, y0: Float, phase: Float, amp: Float): Path = Path().apply {
        val steps = 22
        for (i in 0..steps) {
            val x = xFrom + (xTo - xFrom) * i / steps
            val y = y0 + amp * sin(7.5f * x + phase) + amp * 0.5f * sin(15f * x + 2f * phase)
            if (i == 0) moveTo(side * x, y) else lineTo(side * x, y)
        }
    }

    /** Cracks start at the edge and run inward; drawn partially as rot grows. */
    val cracks: List<Path> by lazy {
        listOf(
            zigzag(listOf(Offset(-0.66f, -0.66f), Offset(-0.52f, -0.50f), Offset(-0.58f, -0.38f), Offset(-0.40f, -0.24f), Offset(-0.44f, -0.10f))),
            zigzag(listOf(Offset(0.95f, -0.30f), Offset(0.80f, -0.24f), Offset(0.84f, -0.12f), Offset(0.68f, -0.06f))),
            zigzag(listOf(Offset(0.28f, -0.82f), Offset(0.22f, -0.66f), Offset(0.32f, -0.56f), Offset(0.20f, -0.40f))),
            zigzag(listOf(Offset(-0.98f, 0.18f), Offset(-0.84f, 0.16f), Offset(-0.86f, 0.28f))),
        )
    }

    val leftEye: Rect = Rect(left = -0.47f, top = -0.07f, right = -0.17f, bottom = 0.27f)
    val rightEye: Rect = Rect(left = 0.17f, top = -0.07f, right = 0.47f, bottom = 0.27f)

    val leftEyePath: Path by lazy { Path().apply { addOval(leftEye) } }
    val rightEyePath: Path by lazy { Path().apply { addOval(rightEye) } }

    /** Mouth outline for the happy open smile; also used to clip the tongue. */
    val happyMouth: Path by lazy {
        Path().apply {
            moveTo(-0.20f, 0.37f)
            quadraticTo(0f, 0.70f, 0.20f, 0.37f)
            quadraticTo(0f, 0.44f, -0.20f, 0.37f)
            close()
        }
    }

    /** A four-point sparkle of radius 1. */
    val sparkle: Path by lazy {
        Path().apply {
            moveTo(0f, -1f)
            quadraticTo(0f, 0f, 1f, 0f)
            quadraticTo(0f, 0f, 0f, 1f)
            quadraticTo(0f, 0f, -1f, 0f)
            quadraticTo(0f, 0f, 0f, -1f)
            close()
        }
    }

    val sweatDrop: Path by lazy {
        Path().apply {
            moveTo(0f, -0.12f)
            quadraticTo(0.09f, 0.02f, 0f, 0.06f)
            quadraticTo(-0.09f, 0.02f, 0f, -0.12f)
            close()
        }
    }

    /** Where the bottom edge sits at [x], for hanging drips. */
    fun bottomAt(x: Float): Float = RY * BOTTOM_FLATTEN * kotlin.math.sqrt((1f - x * x).coerceAtLeast(0f)) - 0.02f

    // A real brain's colour: a warm, slightly grey flesh tone. It dulls toward grey-olive as it rots.
    private val bodyStops = listOf(
        10f to Color(0xFFE9B9A8),
        30f to Color(0xFFDDAE9F),
        50f to Color(0xFFC9A596),
        70f to Color(0xFFB09C82),
        90f to Color(0xFF8E9160),
    )

    fun bodyColor(rot: Float): Color {
        if (rot <= bodyStops.first().first) return bodyStops.first().second
        if (rot >= bodyStops.last().first) return bodyStops.last().second
        val upper = bodyStops.indexOfFirst { it.first >= rot }
        val (r0, c0) = bodyStops[upper - 1]
        val (r1, c1) = bodyStops[upper]
        return lerp(c0, c1, (rot - r0) / (r1 - r0))
    }

    private fun smoothPath(points: List<Offset>): Path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size - 1) {
            val mid = (points[i] + points[i + 1]) / 2f
            quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
        }
        lineTo(points.last().x, points.last().y)
    }

    private fun zigzag(points: List<Offset>): Path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
}
