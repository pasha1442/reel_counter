package com.reelcounter.core.model

/** Kept for stored settings from earlier builds; the look now decides light or dark. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** The two looks a user can pick. Same screens and features, drawn two ways. */
enum class AppLook(val displayName: String, val tagline: String) {
    /** Dark and calm; the brain leads. */
    MIDNIGHT("Midnight", "Dark, calm, easy on the eyes at night"),

    /** Bright, bold outlines, game-like. */
    STICKER("Sticker", "Bright, bold and playful"),
}
