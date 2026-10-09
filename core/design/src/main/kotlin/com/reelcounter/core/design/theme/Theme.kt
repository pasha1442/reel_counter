package com.reelcounter.core.design.theme

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.model.AppLook

/** What makes a look feel like itself beyond colour: outlines, hard shadows, corner sizes. */
@Immutable
data class LookTokens(
    val look: AppLook,
    /** Outline around cards, buttons and chips. Zero for Midnight. */
    val border: Dp,
    val borderColor: Color,
    /** Thinner outline for small things (pills, chips, small tiles) so they don't shout. */
    val thinBorder: Dp,
    /** Hard offset shadow under raised things (Sticker only): main buttons and the selected choice. */
    val shadow: Dp,
    val shadowColor: Color,
    /** Uppercase labels. Off in both looks: sentence case reads calmer and faster. */
    val shoutLabels: Boolean,
    val dark: Boolean,
) {
    val isSticker: Boolean get() = look == AppLook.STICKER
}

private val MidnightTokens = LookTokens(
    look = AppLook.MIDNIGHT,
    border = 0.dp,
    borderColor = Color.Transparent,
    thinBorder = 0.dp,
    shadow = 0.dp,
    shadowColor = Color.Transparent,
    shoutLabels = false,
    dark = true,
)

private val StickerTokens = LookTokens(
    look = AppLook.STICKER,
    border = 2.dp,
    borderColor = StickerPalette.Ink,
    thinBorder = 1.5.dp,
    shadow = 3.dp,
    shadowColor = StickerPalette.Ink,
    shoutLabels = false,
    dark = false,
)

/** One spacing rhythm for every screen (8dp grid). */
object RcSpacing {
    /** Left and right screen edge. */
    val gutter = 24.dp

    /** Between sections of a screen. */
    val section = 32.dp

    /** From a section title to its content, and between cards in one section. */
    val item = 12.dp

    /** Inside cards. */
    val card = 20.dp
}

fun tokensFor(look: AppLook): LookTokens = when (look) {
    AppLook.MIDNIGHT -> MidnightTokens
    AppLook.STICKER -> StickerTokens
}

private val MidnightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val StickerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** True when the user turned animations off in system settings. Skip loops and big motion then. */
val LocalReducedMotion = staticCompositionLocalOf { false }

val LocalLookTokens = staticCompositionLocalOf { MidnightTokens }

@Composable
fun RcTheme(
    look: AppLook = AppLook.MIDNIGHT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalExtendedColors provides extendedFor(look),
        LocalLookTokens provides tokensFor(look),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorsFor(look),
            typography = typographyFor(look),
            shapes = if (look == AppLook.STICKER) StickerShapes else MidnightShapes,
            content = content,
        )
    }
}

object RcTheme {
    val extended: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val tokens: LookTokens
        @Composable @ReadOnlyComposable
        get() = LocalLookTokens.current
}
