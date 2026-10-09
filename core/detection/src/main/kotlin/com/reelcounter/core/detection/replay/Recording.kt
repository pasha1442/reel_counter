package com.reelcounter.core.detection.replay

import com.reelcounter.core.detection.DetectionEngine
import com.reelcounter.core.detection.DetectionOutcome
import com.reelcounter.core.detection.DetectorEvent
import com.reelcounter.core.detection.RulesPack
import com.reelcounter.core.detection.ScreenRect
import com.reelcounter.core.detection.ScreenSize
import com.reelcounter.core.detection.UiNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A recorded accessibility session with a hand-counted reel total. QA records these on real
 * phones (debug builds only) and every rules change must replay all of them correctly.
 *
 * File format (JSON Lines): the first line is a [RecordingHeader], every other line a [RecordedFrame].
 * Node text is stored only as a hash, so recordings contain no captions or usernames.
 */
@Serializable
data class RecordingHeader(
    val packageName: String,
    val versionCode: Long? = null,
    val screenWidth: Int,
    val screenHeight: Int,
    /** Reels a person counted by hand while recording. */
    val handCount: Int,
    val note: String? = null,
)

@Serializable
data class RecordedNode(
    val viewId: String? = null,
    val className: String? = null,
    /** left, top, right, bottom */
    val bounds: List<Int>,
    val visible: Boolean = true,
    val scrollable: Boolean = false,
    val textHash: String? = null,
    val row: Int = -1,
    val children: List<RecordedNode> = emptyList(),
)

@Serializable
data class RecordedFrame(
    val event: DetectorEvent,
    /** The nodes the rules can ask for at this moment; null when no screen tree was available. */
    val nodes: List<RecordedNode>? = null,
)

data class Recording(val header: RecordingHeader, val frames: List<RecordedFrame>)

data class ReplayResult(val counted: Int, val estimated: Int, val outcomes: List<DetectionOutcome>)

object Replay {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    fun parse(lines: Sequence<String>): Recording {
        val nonEmpty = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }.iterator()
        require(nonEmpty.hasNext()) { "Empty recording" }
        val header = json.decodeFromString(RecordingHeader.serializer(), nonEmpty.next())
        val frames = buildList {
            while (nonEmpty.hasNext()) add(json.decodeFromString(RecordedFrame.serializer(), nonEmpty.next()))
        }
        return Recording(header, frames)
    }

    fun run(recording: Recording, pack: RulesPack): ReplayResult {
        val engine = DetectionEngine(
            rulesPack = pack,
            versionCodeOf = { recording.header.versionCode },
            screen = ScreenSize(recording.header.screenWidth, recording.header.screenHeight),
        )
        val outcomes = ArrayList<DetectionOutcome>()
        var lastRoot: UiNode? = null
        var lastTime: Long? = null

        // The service's timer: wake-ups due before the next event fire with the screen unchanged.
        fun tickUntil(limit: Long) {
            var now = lastTime ?: return
            repeat(MAX_TICKS_BETWEEN_EVENTS) {
                val due = engine.nextTickAt(now) ?: return
                if (due >= limit || due < now) return
                outcomes += engine.tick(due) { lastRoot }
                if (due == now) return
                now = due
            }
        }

        for (frame in recording.frames) {
            tickUntil(frame.event.timeMillis)
            val root = frame.nodes?.let { ReplayRoot(it) }
            outcomes += engine.process(frame.event) { root }
            lastRoot = root
            lastTime = frame.event.timeMillis
        }
        lastTime?.let { tickUntil(it + TAIL_MILLIS) }
        val counted = outcomes.filterIsInstance<DetectionOutcome.ReelCounted>()
        return ReplayResult(counted = counted.size, estimated = counted.count { it.estimated }, outcomes = outcomes)
    }

    private const val MAX_TICKS_BETWEEN_EVENTS = 8
    private const val TAIL_MILLIS = 5_000L
}

/** A fake root that answers view-ID lookups from the recorded nodes. */
class ReplayRoot(private val nodes: List<RecordedNode>) : UiNode {
    override val viewId: String? = null
    override val className: String? = null
    override val bounds: ScreenRect = ScreenRect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE)
    override val isVisibleToUser: Boolean = true
    override val isScrollable: Boolean = false
    override val text: String? = null
    override val contentDescription: String? = null
    override val collectionRowIndex: Int = -1
    override val childCount: Int get() = nodes.size

    override fun child(index: Int): UiNode? = nodes.getOrNull(index)?.let(::ReplayNode)

    override fun findByViewId(fullViewId: String): List<UiNode> {
        val result = ArrayList<UiNode>()
        fun visit(node: RecordedNode) {
            if (node.viewId == fullViewId) result += ReplayNode(node)
            node.children.forEach(::visit)
        }
        nodes.forEach(::visit)
        return result
    }
}

private class ReplayNode(private val node: RecordedNode) : UiNode {
    override val viewId: String? get() = node.viewId
    override val className: String? get() = node.className
    override val bounds: ScreenRect
        get() = ScreenRect(node.bounds[0], node.bounds[1], node.bounds[2], node.bounds[3])
    override val isVisibleToUser: Boolean get() = node.visible
    override val isScrollable: Boolean get() = node.scrollable
    override val text: String? get() = node.textHash
    override val contentDescription: String? get() = null
    override val collectionRowIndex: Int get() = node.row
    override val childCount: Int get() = node.children.size

    override fun child(index: Int): UiNode? = node.children.getOrNull(index)?.let(::ReplayNode)

    override fun findByViewId(fullViewId: String): List<UiNode> {
        val result = ArrayList<UiNode>()
        fun visit(n: RecordedNode) {
            if (n.viewId == fullViewId) result += ReplayNode(n)
            n.children.forEach(::visit)
        }
        node.children.forEach(::visit)
        return result
    }
}
