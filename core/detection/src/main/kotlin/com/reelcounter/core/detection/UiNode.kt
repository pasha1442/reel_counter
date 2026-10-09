package com.reelcounter.core.detection

import kotlin.math.max
import kotlin.math.min

/**
 * A read-only view of one on-screen node. The Android adapter wraps AccessibilityNodeInfo;
 * tests and replays use in-memory nodes. [text] and [contentDescription] may only be read to
 * build an in-memory fingerprint and must never be stored.
 */
interface UiNode {
    val viewId: String?
    val className: String?
    val bounds: ScreenRect
    val isVisibleToUser: Boolean
    val isScrollable: Boolean
    val text: String?
    val contentDescription: String?

    /** Position of this node inside a list or pager (CollectionItemInfo), or -1. */
    val collectionRowIndex: Int
    val childCount: Int

    fun child(index: Int): UiNode?

    /** Full resource name, for example "com.instagram.android:id/clips_viewer_view_pager". */
    fun findByViewId(fullViewId: String): List<UiNode>
}

data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = max(0, right - left)
    val height: Int get() = max(0, bottom - top)
    val area: Long get() = width.toLong() * height

    fun intersect(other: ScreenRect): ScreenRect = ScreenRect(
        left = max(left, other.left),
        top = max(top, other.top),
        right = min(right, other.right),
        bottom = min(bottom, other.bottom),
    )

    companion object {
        val Empty = ScreenRect(0, 0, 0, 0)
    }
}

data class ScreenSize(val width: Int, val height: Int) {
    val rect: ScreenRect get() = ScreenRect(0, 0, width, height)
    val area: Long get() = width.toLong() * height

    /** Share of the screen covered by [bounds], 0..1. */
    fun coverage(bounds: ScreenRect): Float =
        if (area == 0L) 0f else bounds.intersect(rect).area.toFloat() / area

    fun visibleArea(bounds: ScreenRect): Long = bounds.intersect(rect).area
}
