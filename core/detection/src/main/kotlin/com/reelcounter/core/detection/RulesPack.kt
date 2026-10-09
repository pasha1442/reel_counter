package com.reelcounter.core.detection

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Detection rules for every supported app and app-version range. Shipped in the APK and,
 * later, refreshed from the server so a broken counter is fixed without an app update.
 */
@Serializable
data class RulesPack(
    val version: Int,
    val apps: List<AppRules>,
)

@Serializable
data class AppRules(
    val packageName: String,
    val minVersionCode: Long = 0,
    val maxVersionCode: Long = Long.MAX_VALUE,
    /** Kill switch: this version is known to break detection, so we pause instead of miscounting. */
    val broken: Boolean = false,
    /** Any of these on screen, large enough, means the full-screen player is showing. */
    val surfaceViewIds: List<String> = emptyList(),
    /** The vertical pager whose scroll events mean "next reel". */
    val pagerViewIds: List<String> = emptyList(),
    /** Author and caption nodes, hashed in memory to tell reels apart. Never stored. */
    val fingerprintViewIds: List<String> = emptyList(),
    /** Share of the screen the surface must cover, 0..1. */
    val minSurfaceCoverage: Float = 0.75f,
)

sealed interface ResolvedRules {
    data class Specific(val rules: AppRules) : ResolvedRules

    /** No rule matched: full-screen vertical pager heuristic, counts marked estimated. */
    data object Generic : ResolvedRules

    data object Paused : ResolvedRules
}

fun RulesPack.resolve(packageName: String, versionCode: Long?): ResolvedRules {
    val candidates = apps.filter { it.packageName == packageName }
    val match = if (versionCode == null) {
        candidates.lastOrNull { !it.broken }
    } else {
        candidates.firstOrNull { versionCode in it.minVersionCode..it.maxVersionCode }
    }
    return when {
        match == null -> ResolvedRules.Generic
        match.broken -> ResolvedRules.Paused
        else -> ResolvedRules.Specific(match)
    }
}

object RulesPackParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(text: String): RulesPack = json.decodeFromString(RulesPack.serializer(), text)
}
