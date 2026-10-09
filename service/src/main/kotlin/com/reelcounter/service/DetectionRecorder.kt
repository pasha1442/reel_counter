package com.reelcounter.service

import com.reelcounter.core.detection.DetectorEvent
import com.reelcounter.core.detection.RulesPack
import com.reelcounter.core.detection.ScreenSize
import com.reelcounter.core.detection.UiNode
import com.reelcounter.core.detection.replay.RecordedFrame
import com.reelcounter.core.detection.replay.RecordedNode
import com.reelcounter.core.detection.replay.RecordingHeader
import com.reelcounter.core.detection.replay.Replay
import com.reelcounter.core.model.ReelApp
import java.io.BufferedWriter
import java.io.File

/**
 * Debug builds only. Saves detection sessions in the replay format so QA can hand-count a
 * session and add it to the test suite. Text is stored only as a hash.
 *
 * Files land in Android/data/<package>/files/recordings; pull them with adb and set
 * "handCount" in the first line before adding them to core/detection/src/test/resources/recordings.
 */
internal class DetectionRecorder(private val directory: File) {
    private var writer: BufferedWriter? = null
    private var currentPackage: String? = null

    fun record(event: DetectorEvent, pack: RulesPack, root: UiNode?, screen: ScreenSize, versionCode: Long?) {
        val supported = ReelApp.fromPackage(event.packageName) != null
        if (supported && event.packageName != currentPackage) open(event.packageName, screen, versionCode)
        val out = writer ?: return
        val nodes = if (supported && root != null) capture(root, pack, event.packageName) else null
        out.write(Replay.json.encodeToString(RecordedFrame.serializer(), RecordedFrame(event, nodes)))
        out.newLine()
        out.flush()
    }

    fun close() {
        runCatching { writer?.close() }
        writer = null
        currentPackage = null
    }

    private fun open(packageName: String, screen: ScreenSize, versionCode: Long?) {
        close()
        directory.mkdirs()
        val file = File(directory, "${packageName.substringAfterLast('.')}_${System.currentTimeMillis()}.jsonl")
        writer = file.bufferedWriter().also {
            val header = RecordingHeader(
                packageName = packageName,
                versionCode = versionCode,
                screenWidth = screen.width,
                screenHeight = screen.height,
                handCount = 0,
                note = "Set handCount to the reels you counted by hand.",
            )
            it.write(Replay.json.encodeToString(RecordingHeader.serializer(), header))
            it.newLine()
        }
        currentPackage = packageName
    }

    private fun capture(root: UiNode, pack: RulesPack, packageName: String): List<RecordedNode> {
        val rules = pack.apps.filter { it.packageName == packageName }
        val pagerIds = rules.flatMap { it.pagerViewIds }.toSet()
        val ids = rules.flatMap { it.surfaceViewIds + it.pagerViewIds + it.fingerprintViewIds }.distinct()
        return ids.flatMap { id ->
            root.findByViewId(id).map { node ->
                val children = if (id in pagerIds) {
                    (0 until node.childCount).mapNotNull { i -> node.child(i)?.let { snapshot(it, emptyList()) } }
                } else {
                    emptyList()
                }
                snapshot(node, children)
            }
        }
    }

    private fun snapshot(node: UiNode, children: List<RecordedNode>): RecordedNode {
        val b = node.bounds
        val text = node.text ?: node.contentDescription
        return RecordedNode(
            viewId = node.viewId,
            className = node.className,
            bounds = listOf(b.left, b.top, b.right, b.bottom),
            visible = node.isVisibleToUser,
            scrollable = node.isScrollable,
            textHash = text?.takeIf { it.isNotBlank() }?.let { "h" + it.hashCode().toUInt().toString(16) },
            row = node.collectionRowIndex,
            children = children,
        )
    }
}
