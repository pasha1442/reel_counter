package com.reelcounter.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.reelcounter.core.detection.DetectorEvent
import com.reelcounter.core.detection.EventKind
import com.reelcounter.core.detection.ScreenRect
import com.reelcounter.core.detection.UiNode

/** Wraps a platform node. Values are read lazily so the engine only pays for what it asks. */
internal class AndroidUiNode(private val info: AccessibilityNodeInfo) : UiNode {
    override val viewId: String? get() = info.viewIdResourceName
    override val className: String? get() = info.className?.toString()
    override val bounds: ScreenRect by lazy {
        val rect = Rect()
        info.getBoundsInScreen(rect)
        ScreenRect(rect.left, rect.top, rect.right, rect.bottom)
    }
    override val isVisibleToUser: Boolean get() = info.isVisibleToUser
    override val isScrollable: Boolean get() = info.isScrollable
    override val text: String? get() = info.text?.toString()
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val collectionRowIndex: Int get() = info.collectionItemInfo?.rowIndex ?: -1
    override val childCount: Int get() = info.childCount

    override fun child(index: Int): UiNode? = info.getChild(index)?.let(::AndroidUiNode)

    override fun findByViewId(fullViewId: String): List<UiNode> =
        info.findAccessibilityNodeInfosByViewId(fullViewId).orEmpty().map(::AndroidUiNode)
}

/**
 * Copies what the engine needs out of the event. Must run inside onAccessibilityEvent:
 * on older Android versions the event is recycled as soon as the callback returns.
 */
internal fun AccessibilityEvent.toDetectorEvent(nowMillis: Long): DetectorEvent? {
    val kind = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> EventKind.WINDOW_STATE_CHANGED
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> EventKind.WINDOW_CONTENT_CHANGED
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> EventKind.VIEW_SCROLLED
        else -> return null
    }
    val pkg = packageName?.toString() ?: return null
    // Only scroll events need the source's view ID (to know it is the reels pager).
    val sourceId = if (kind == EventKind.VIEW_SCROLLED) source?.viewIdResourceName else null
    return DetectorEvent(
        kind = kind,
        packageName = pkg,
        timeMillis = nowMillis,
        sourceViewId = sourceId,
        sourceClassName = className?.toString(),
        fromIndex = fromIndex,
        toIndex = toIndex,
        itemCount = itemCount,
        scrollDeltaY = if (scrollDeltaY == UNDEFINED_DELTA) 0 else scrollDeltaY,
    )
}

private const val UNDEFINED_DELTA = -1
