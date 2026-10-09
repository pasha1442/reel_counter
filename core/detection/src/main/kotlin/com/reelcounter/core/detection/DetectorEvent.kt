package com.reelcounter.core.detection

import kotlinx.serialization.Serializable

enum class EventKind { WINDOW_STATE_CHANGED, WINDOW_CONTENT_CHANGED, VIEW_SCROLLED }

/**
 * The parts of an AccessibilityEvent the engine needs, copied out on the main thread
 * (the platform recycles the event as soon as the callback returns).
 */
@Serializable
data class DetectorEvent(
    val kind: EventKind,
    val packageName: String,
    val timeMillis: Long,
    val sourceViewId: String? = null,
    val sourceClassName: String? = null,
    /** First and last visible item of the scrolled list or pager, or -1 when not reported. */
    val fromIndex: Int = -1,
    val toIndex: Int = -1,
    val itemCount: Int = -1,
    /** Vertical scroll distance in pixels since the previous scroll event (API 28+). */
    val scrollDeltaY: Int = 0,
)
