package com.reelcounter.core.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.design.theme.RcMotion
import kotlinx.coroutines.delay

/**
 * Rises and fades in [index] steps after the screen first appears, so a screen's cards arrive
 * one after another instead of all at once. Plays once per screen visit (not again when the
 * user comes back to a tab), and not at all with reduced motion. Transform and alpha only, so
 * nothing around it reflows.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int, key: String = "entrance"): Modifier {
    val reducedMotion = LocalReducedMotion.current
    var played by rememberSaveable(key) { mutableStateOf(reducedMotion) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        delay(index * RcMotion.STAGGER.toLong())
        progress.animateTo(1f, RcMotion.enter(RcMotion.MEDIUM + 80))
        played = true
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 20.dp.toPx()
    }
}
