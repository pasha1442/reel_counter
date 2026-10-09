package com.reelcounter.core.model

import kotlin.math.roundToInt

/** Five stages of the brain, keyed by rot level (0 = fresh, 100 = rotten). */
enum class BrainStage(val range: IntRange) {
    FRESH(0..20),
    FOGGY(21..40),
    CRACKING(41..60),
    MELTING(61..80),
    ROTTEN(81..100),
    ;

    companion object {
        fun fromRot(rot: Float): BrainStage {
            val level = rot.roundToInt().coerceIn(0, 100)
            return entries.first { level in it.range }
        }
    }
}

/** A snapshot of the brain as the UI should draw it. */
data class BrainState(
    val rot: Float,
    val morningResidual: Float,
) {
    val stage: BrainStage get() = BrainStage.fromRot(rot)

    companion object {
        val Fresh = BrainState(rot = 0f, morningResidual = 0f)
    }
}
