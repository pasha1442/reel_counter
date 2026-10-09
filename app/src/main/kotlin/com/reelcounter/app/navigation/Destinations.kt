package com.reelcounter.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.reelcounter.core.design.components.RcIcons
import kotlinx.serialization.Serializable

sealed interface Dest {
    /** [startAtDisclosure]: re-entry from Today after the user chose "Not now". */
    @Serializable
    data class Onboarding(val startAtDisclosure: Boolean = false) : Dest

    @Serializable
    data object Home : Dest

    @Serializable
    data object Progress : Dest

    @Serializable
    data object Limits : Dest

    @Serializable
    data object Friends : Dest

    @Serializable
    data object Settings : Dest

    @Serializable
    data object Brain : Dest

    @Serializable
    data class DayDetail(val epochDay: Long) : Dest

    @Serializable
    data class ShareWeek(val weekStartEpochDay: Long) : Dest

    @Serializable
    data class Paywall(val fromOnboarding: Boolean = false) : Dest

    @Serializable
    data object SignIn : Dest

    @Serializable
    data class EmailAuth(val creating: Boolean = false) : Dest

    /** Finishing a new account: a name, and the 18+ check. */
    @Serializable
    data object ProfileSetup : Dest

    @Serializable
    data object Account : Dest

    /** [page]: one of LegalPages. */
    @Serializable
    data class Legal(val page: String) : Dest
}

enum class TopLevel(val dest: Dest, val label: String, val icon: ImageVector) {
    HOME(Dest.Home, "Today", RcIcons.Home),
    PROGRESS(Dest.Progress, "Progress", RcIcons.History),
    LIMITS(Dest.Limits, "Limits", RcIcons.Sliders),
    FRIENDS(Dest.Friends, "Friends", RcIcons.Friends),
}
