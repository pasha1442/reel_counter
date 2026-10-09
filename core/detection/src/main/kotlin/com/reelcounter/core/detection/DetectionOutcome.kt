package com.reelcounter.core.detection

import com.reelcounter.core.model.CountingStatus
import com.reelcounter.core.model.ReelApp

sealed interface DetectionOutcome {
    /** The full-screen Reels or Shorts player appeared. */
    data class SurfaceEntered(val app: ReelApp, val surfaceSessionId: Long) : DetectionOutcome

    /** The player has been gone for longer than the grace period, or the user left the app. */
    data class SurfaceLeft(val app: ReelApp, val surfaceSessionId: Long) : DetectionOutcome

    /** A new reel is on screen and should count once. */
    data class ReelCounted(
        val app: ReelApp,
        val estimated: Boolean,
        val surfaceSessionId: Long,
        val timeMillis: Long,
    ) : DetectionOutcome

    data class StatusChanged(val app: ReelApp, val status: CountingStatus) : DetectionOutcome

    /** An event arrived from outside the supported apps (the home screen). */
    data object LeftSupportedApps : DetectionOutcome
}
