package com.reelcounter.core.design.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * One set of motion tokens for the whole app, so every animation shares the same rhythm.
 * Arriving elements decelerate, leaving ones accelerate, and exits run at about two-thirds of
 * the matching enter. Callers check [LocalReducedMotion] and skip or shorten motion themselves.
 */
object RcMotion {
    /** Press feedback, toggles, colour changes. */
    const val QUICK = 120

    /** Small elements appearing or changing state. */
    const val SHORT = 200

    /** Cards, sheets, screen content. */
    const val MEDIUM = 320

    /** Large or expressive moves (the limit ring filling, chart bars growing). */
    const val LONG = 600

    /** Delay between items when a group enters one after another. */
    const val STAGGER = 45

    /** Material 3 "emphasized decelerate": for things arriving. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3 "emphasized accelerate": for things leaving. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Material 3 "standard": for things changing in place. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> enter(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, easing = Decelerate)

    fun <T> exit(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration * 2 / 3, easing = Accelerate)

    fun <T> change(duration: Int = SHORT): FiniteAnimationSpec<T> = tween(duration, easing = Standard)

    /** Press and release of tappable surfaces: quick, no overshoot. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

    /** Playful settle for celebratory or count changes. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}
