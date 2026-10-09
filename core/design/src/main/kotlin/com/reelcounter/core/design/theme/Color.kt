package com.reelcounter.core.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.model.BrainStage

/**
 * Midnight: a warm near-black for night use. One soft amber accent, used sparingly; sage for "on
 * track" and terracotta for "over". Nothing neon and no blue light glare: the brain brings the colour.
 */
internal object MidnightPalette {
    val Bg = Color(0xFF111110)
    val Low = Color(0xFF161514)
    val Card = Color(0xFF1B1A18)
    val CardHigh = Color(0xFF23211F)
    val CardHighest = Color(0xFF2C2A27)
    val Line = Color(0xFF2A2826)
    val LineStrong = Color(0xFF3B3835)
    val Text = Color(0xFFEDE8E1)
    val Muted = Color(0xFFA39D94)
    val Amber = Color(0xFFE5B574)
    val OnAmber = Color(0xFF2A1D0C)
    val Sage = Color(0xFF8FBF9F)
    val Terracotta = Color(0xFFE0876A)
    val Mist = Color(0xFFA8C3DC)
}

/** Sticker: cream paper, black ink outlines, sunny yellow. */
internal object StickerPalette {
    val Paper = Color(0xFFFFF4E0)
    val PaperDeep = Color(0xFFF3E7CF)
    val White = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111111)
    val InkSoft = Color(0xFF5C5548)
    val Yellow = Color(0xFFFFD43B)
    val Mint = Color(0xFF7CE0B5)
    val Sky = Color(0xFF8CCBFF)
    val Coral = Color(0xFFFF6B5B)
    val Peach = Color(0xFFFFB27A)
}

internal val MidnightColors: ColorScheme = darkColorScheme(
    primary = MidnightPalette.Amber,
    onPrimary = MidnightPalette.OnAmber,
    primaryContainer = MidnightPalette.Amber,
    onPrimaryContainer = MidnightPalette.OnAmber,
    secondary = MidnightPalette.Mist,
    onSecondary = Color(0xFF13202B),
    secondaryContainer = Color(0xFF1B2530),
    onSecondaryContainer = MidnightPalette.Mist,
    tertiary = MidnightPalette.Terracotta,
    onTertiary = Color(0xFF2A120B),
    tertiaryContainer = Color(0xFF2E1D16),
    onTertiaryContainer = Color(0xFFF0B19C),
    background = MidnightPalette.Bg,
    onBackground = MidnightPalette.Text,
    surface = MidnightPalette.Bg,
    onSurface = MidnightPalette.Text,
    surfaceVariant = MidnightPalette.CardHigh,
    onSurfaceVariant = MidnightPalette.Muted,
    surfaceContainerLowest = Color(0xFF0B0B0A),
    surfaceContainerLow = MidnightPalette.Low,
    surfaceContainer = MidnightPalette.Card,
    surfaceContainerHigh = MidnightPalette.CardHigh,
    surfaceContainerHighest = MidnightPalette.CardHighest,
    surfaceBright = MidnightPalette.CardHighest,
    surfaceDim = MidnightPalette.Bg,
    outline = MidnightPalette.LineStrong,
    outlineVariant = MidnightPalette.Line,
    error = Color(0xFFEE9584),
    onError = Color(0xFF3A0B05),
    inverseSurface = MidnightPalette.Text,
    inverseOnSurface = MidnightPalette.Bg,
    inversePrimary = Color(0xFF7A5A2A),
    scrim = Color.Black,
)

internal val StickerColors: ColorScheme = lightColorScheme(
    primary = StickerPalette.Ink,
    onPrimary = StickerPalette.White,
    primaryContainer = StickerPalette.Yellow,
    onPrimaryContainer = StickerPalette.Ink,
    secondary = Color(0xFF1F5F99),
    onSecondary = StickerPalette.White,
    secondaryContainer = StickerPalette.Sky,
    onSecondaryContainer = StickerPalette.Ink,
    tertiary = Color(0xFFB33A2C),
    onTertiary = StickerPalette.White,
    tertiaryContainer = StickerPalette.Coral,
    onTertiaryContainer = StickerPalette.Ink,
    background = StickerPalette.Paper,
    onBackground = StickerPalette.Ink,
    surface = StickerPalette.Paper,
    onSurface = StickerPalette.Ink,
    surfaceVariant = StickerPalette.PaperDeep,
    onSurfaceVariant = StickerPalette.InkSoft,
    surfaceContainerLowest = StickerPalette.White,
    surfaceContainerLow = Color(0xFFFFF9EE),
    surfaceContainer = StickerPalette.White,
    surfaceContainerHigh = StickerPalette.White,
    surfaceContainerHighest = StickerPalette.PaperDeep,
    surfaceBright = StickerPalette.White,
    surfaceDim = StickerPalette.PaperDeep,
    outline = StickerPalette.Ink,
    outlineVariant = StickerPalette.Ink,
    error = Color(0xFFB42318),
    onError = StickerPalette.White,
    inverseSurface = StickerPalette.Ink,
    inverseOnSurface = StickerPalette.Paper,
    inversePrimary = StickerPalette.Yellow,
    scrim = Color.Black,
)

/** Colours Material 3 has no slot for. */
@Immutable
data class ExtendedColors(
    /** The high-energy action colour (start trial, let's go). */
    val cta: Color,
    val onCta: Color,
    /** Under the limit / on track. */
    val success: Color,
    val onSuccess: Color,
    /** Over the limit. Always paired with a word or icon, never colour alone. */
    val warning: Color,
    val onWarning: Color,
    /** "On track" as text or an icon on the page or a card (Sticker's mint fill is too light for that). */
    val successText: Color,
    /** "Over" as text or an icon on the page or a card. */
    val warningText: Color,
    val stageFresh: Color,
    val stageFoggy: Color,
    val stageCracking: Color,
    val stageMelting: Color,
    val stageRotten: Color,
    val instagram: Color,
    val youtube: Color,
    /** Chart marks: days at or under the limit / over it. */
    val chartUnder: Color,
    val chartOver: Color,
    /** Empty chart cells and tracks. */
    val track: Color,
    /** Soft fills for tinted cards. */
    val tintMint: Color,
    val tintSky: Color,
    val tintWarm: Color,
    val tintRose: Color,
    /** Text on those tints. */
    val onTintMint: Color,
    val onTintSky: Color,
    val onTintWarm: Color,
    val onTintRose: Color,
) {
    fun stage(stage: BrainStage): Color = when (stage) {
        BrainStage.FRESH -> stageFresh
        BrainStage.FOGGY -> stageFoggy
        BrainStage.CRACKING -> stageCracking
        BrainStage.MELTING -> stageMelting
        BrainStage.ROTTEN -> stageRotten
    }

    /** Text or icon colour to put on top of a [stage] fill. */
    fun onStage(stage: BrainStage): Color = when (stage) {
        BrainStage.FRESH, BrainStage.FOGGY, BrainStage.CRACKING -> Color(0xFF111111)
        BrainStage.MELTING, BrainStage.ROTTEN -> Color.White
    }
}

internal val MidnightExtended = ExtendedColors(
    cta = MidnightPalette.Amber,
    onCta = MidnightPalette.OnAmber,
    success = MidnightPalette.Sage,
    onSuccess = Color(0xFF10201A),
    warning = MidnightPalette.Terracotta,
    onWarning = Color(0xFF2A120B),
    successText = MidnightPalette.Sage,
    warningText = MidnightPalette.Terracotta,
    stageFresh = MidnightPalette.Sage,
    stageFoggy = Color(0xFFC9BF8C),
    stageCracking = MidnightPalette.Amber,
    stageMelting = Color(0xFFE0976E),
    stageRotten = Color(0xFFD4705F),
    instagram = Color(0xFFE1306C),
    youtube = Color(0xFFFF3B30),
    chartUnder = MidnightPalette.Sage,
    chartOver = MidnightPalette.Terracotta,
    track = MidnightPalette.CardHighest,
    tintMint = Color(0xFF1C2A22),
    tintSky = Color(0xFF1B2530),
    tintWarm = Color(0xFF2E2416),
    tintRose = Color(0xFF2E2416),
    onTintMint = Color(0xFFA9D4B6),
    onTintSky = MidnightPalette.Mist,
    onTintWarm = Color(0xFFEBC58E),
    onTintRose = Color(0xFFEBC58E),
)

internal val StickerExtended = ExtendedColors(
    cta = StickerPalette.Yellow,
    onCta = StickerPalette.Ink,
    success = StickerPalette.Mint,
    onSuccess = StickerPalette.Ink,
    warning = StickerPalette.Coral,
    onWarning = StickerPalette.Ink,
    successText = Color(0xFF1E7A4F),
    warningText = Color(0xFFB33A2C),
    stageFresh = StickerPalette.Mint,
    stageFoggy = StickerPalette.Yellow,
    stageCracking = StickerPalette.Peach,
    stageMelting = StickerPalette.Coral,
    stageRotten = Color(0xFFB33A2C),
    instagram = Color(0xFFE1306C),
    youtube = Color(0xFFFF3B30),
    chartUnder = StickerPalette.Mint,
    chartOver = StickerPalette.Coral,
    track = StickerPalette.PaperDeep,
    tintMint = StickerPalette.Mint,
    tintSky = StickerPalette.Sky,
    tintWarm = StickerPalette.Peach,
    tintRose = StickerPalette.Peach,
    onTintMint = StickerPalette.Ink,
    onTintSky = StickerPalette.Ink,
    onTintWarm = StickerPalette.Ink,
    onTintRose = StickerPalette.Ink,
)

fun colorsFor(look: AppLook): ColorScheme = when (look) {
    AppLook.MIDNIGHT -> MidnightColors
    AppLook.STICKER -> StickerColors
}

fun extendedFor(look: AppLook): ExtendedColors = when (look) {
    AppLook.MIDNIGHT -> MidnightExtended
    AppLook.STICKER -> StickerExtended
}

val LocalExtendedColors = staticCompositionLocalOf { MidnightExtended }
