package com.reelcounter.core.detection

import com.reelcounter.core.detection.DetectionOutcome.LeftSupportedApps
import com.reelcounter.core.detection.DetectionOutcome.ReelCounted
import com.reelcounter.core.detection.DetectionOutcome.StatusChanged
import com.reelcounter.core.detection.DetectionOutcome.SurfaceEntered
import com.reelcounter.core.detection.DetectionOutcome.SurfaceLeft
import com.reelcounter.core.model.CountingStatus
import com.reelcounter.core.model.ReelApp
import kotlin.math.abs
import kotlin.math.roundToInt

data class DetectionConfig(
    /** A reel must stay on screen this long after the previous count to count itself (fast flicks). */
    val debounceMillis: Long = 500,
    /**
     * A sitting that starts this soon after the service (re)connects joined mid-scroll: the reel on
     * screen was already there before we connected, so it is not counted again.
     */
    val connectGraceMillis: Long = 3_000,
    /** How long the player may vanish (sheets, re-layout) before the sitting ends. */
    val surfaceGraceMillis: Long = 2_000,
    /** Coming back to the same app this soon resumes the sitting instead of starting a new one. */
    val resumeWindowMillis: Long = 10_000,
    /** Minimum gap between screen-tree checks triggered by content or scroll events. */
    val treeCheckIntervalMillis: Long = 250,
    /** Without index data, a swipe has settled once no pager scroll arrived for this long. */
    val settleMillis: Long = 350,
    /**
     * Without index data, a settled swipe counts as N pages when its distance is within this share of
     * N pages (plus a little per extra page). Scroll events under-report distance (measured on
     * YouTube: about 94% of a page per swipe), so each swipe snaps to whole pages and nothing drifts.
     */
    val pageSnapTolerance: Float = 0.15f,
    /** A swipe that never lands near a page boundary is rounded anyway once idle this long. */
    val forceSettleMillis: Long = 2_000,
    /** Generic fallback: the pager must cover this share of the screen. */
    val genericCoverage: Float = 0.85f,
    /** How long a reel's fingerprint blocks a recount when the user re-enters the player. */
    val reentryMemoryMillis: Long = 10 * 60_000,
    val genericSearchNodeBudget: Int = 400,
)

/**
 * Turns a stream of accessibility events into exactly one [ReelCounted] per reel.
 *
 * Steps per event: filter by app, check the player is on screen, detect a settled advance of the
 * vertical pager, de-duplicate, and debounce fast flicks. Reels are told apart by the pager's item
 * index when the app reports one, otherwise by a position tracked from scroll distance in whole
 * pages, plus an in-memory fingerprint when the rules name author/caption views.
 * Not thread-safe: the service feeds it from one background thread.
 */
class DetectionEngine(
    rulesPack: RulesPack,
    private val versionCodeOf: (packageName: String) -> Long?,
    private var screen: ScreenSize,
    private val config: DetectionConfig = DetectionConfig(),
) {
    private var rulesPack: RulesPack = rulesPack
    private val states = HashMap<ReelApp, AppState>()
    private var nextSessionId = 1L
    private var connectedAt = Long.MIN_VALUE / 2

    /**
     * The accessibility service (re)connected. Android can restart the service at any time (some
     * phones do it every few seconds); the reel on screen at that moment was probably counted
     * already, so a sitting that starts right after this does not count it.
     */
    fun onServiceConnected(now: Long) {
        connectedAt = now
    }

    fun updateRules(pack: RulesPack) {
        rulesPack = pack
        states.values.forEach { it.resolved = null }
    }

    fun updateScreen(size: ScreenSize) {
        screen = size
    }

    /** True while the user is in a Reels / Shorts sitting in [app]. */
    fun hasActiveSurface(app: ReelApp): Boolean = states[app]?.session != null

    /** Current status per app, for apps seen since the service started. */
    fun statuses(): Map<ReelApp, CountingStatus> =
        states.mapNotNull { (app, state) -> state.status?.let { app to it } }.toMap()

    fun process(event: DetectorEvent, root: () -> UiNode?): List<DetectionOutcome> {
        val app = ReelApp.fromPackage(event.packageName)
        if (app == null) {
            // Only the home screen reaches us outside the supported apps. Its content changes
            // (widgets, launch animations) mean nothing; a window change means the user left.
            // We never read its screen content.
            if (event.kind != EventKind.WINDOW_STATE_CHANGED) return emptyList()
            val out = ArrayList<DetectionOutcome>(3)
            states.values.forEach { endSession(it, event.timeMillis, out) }
            out += LeftSupportedApps
            return out
        }

        val out = ArrayList<DetectionOutcome>(2)
        val now = event.timeMillis
        states.values.forEach { if (it.app != app) endSession(it, now, out) }

        val state = states.getOrPut(app) { AppState(app) }
        val resolved = resolve(state)
        val status = when (resolved) {
            is ResolvedRules.Specific -> CountingStatus.ACTIVE
            ResolvedRules.Generic -> CountingStatus.ESTIMATED
            ResolvedRules.Paused -> CountingStatus.PAUSED
        }
        if (status != state.status) {
            state.status = status
            out += StatusChanged(app, status)
        }
        if (resolved == ResolvedRules.Paused) {
            endSession(state, now, out)
            return out
        }

        val pagerEvent = event.kind == EventKind.VIEW_SCROLLED && isPagerEvent(event, resolved, state)
        val onSurface: Boolean? = if (pagerEvent) true else checkSurface(event, state, resolved, root)

        when (onSurface) {
            true -> {
                state.lastSurfaceSeenAt = now
                if (state.session == null) startSession(state, resolved, now, root, out)
            }
            false -> if (state.session != null && now - state.lastSurfaceSeenAt > config.surfaceGraceMillis) {
                endSession(state, now, out)
            }
            null -> Unit
        }

        val session = state.session ?: return out
        settleSwipe(session, now)
        // A reel that settled before this event counts before the event can replace it.
        flushPending(state, session, resolved, now, root, out)
        if (pagerEvent) {
            onPagerScrolled(event, session)
            flushPending(state, session, resolved, now, root, out)
        }
        return out
    }

    /**
     * When the engine next needs [tick] even if no event arrives: a swipe waiting to settle or a
     * reel waiting out the debounce. Null when nothing is waiting.
     */
    fun nextTickAt(now: Long): Long? = states.values.mapNotNull { state ->
        val session = state.session ?: return@mapNotNull null
        val times = ArrayList<Long>(2)
        if (session.swipeDeltaY != 0L) {
            val settleAt = session.lastScrollAt + config.settleMillis
            times += if (now < settleAt) settleAt else session.lastScrollAt + config.forceSettleMillis
        }
        session.pending?.let { pending ->
            times += if (pending.isEntry) {
                session.lastCountAt + config.debounceMillis
            } else {
                maxOf(session.lastCountAt + config.debounceMillis, session.lastScrollAt + config.settleMillis)
            }
        }
        times.filter { it > now }.minOrNull() ?: times.firstOrNull()?.let { now }
    }.minOrNull()

    /** Lets waiting swipes settle and debounced reels count when the app sends no further events. */
    fun tick(now: Long, root: (ReelApp) -> UiNode?): List<DetectionOutcome> {
        val out = ArrayList<DetectionOutcome>(1)
        for (state in states.values) {
            val session = state.session ?: continue
            val resolved = state.resolved ?: continue
            settleSwipe(session, now)
            flushPending(state, session, resolved, now, { root(state.app) }, out)
        }
        return out
    }

    /** Screen off, service restart: end every open sitting. */
    fun reset(): List<DetectionOutcome> {
        val out = ArrayList<DetectionOutcome>()
        states.values.forEach {
            endSession(it, Long.MIN_VALUE / 2, out)
            it.lastEnded = null
        }
        return out
    }

    private fun resolve(state: AppState): ResolvedRules {
        val version = versionCodeOf(state.app.packageName)
        val cached = state.resolved
        if (cached != null && state.resolvedForVersion == version) return cached
        return rulesPack.resolve(state.app.packageName, version).also {
            state.resolved = it
            state.resolvedForVersion = version
        }
    }

    private fun isPagerEvent(event: DetectorEvent, resolved: ResolvedRules, state: AppState): Boolean =
        when (resolved) {
            is ResolvedRules.Specific -> event.sourceViewId != null && event.sourceViewId in resolved.rules.pagerViewIds
            ResolvedRules.Generic -> state.session != null && isPagerClass(event.sourceClassName)
            ResolvedRules.Paused -> false
        }

    /** Returns null when the check was skipped to stay inside the per-event budget. */
    private fun checkSurface(
        event: DetectorEvent,
        state: AppState,
        resolved: ResolvedRules,
        root: () -> UiNode?,
    ): Boolean? {
        val now = event.timeMillis
        if (event.kind != EventKind.WINDOW_STATE_CHANGED &&
            now - state.lastTreeCheckAt < config.treeCheckIntervalMillis
        ) {
            return null
        }
        state.lastTreeCheckAt = now
        val tree = root() ?: return null
        return when (resolved) {
            is ResolvedRules.Specific -> resolved.rules.surfaceViewIds.any { id ->
                tree.findByViewId(id).any {
                    it.isVisibleToUser && screen.coverage(it.bounds) >= resolved.rules.minSurfaceCoverage
                }
            }
            ResolvedRules.Generic -> findGenericPager(tree) != null
            ResolvedRules.Paused -> false
        }
    }

    private fun startSession(
        state: AppState,
        resolved: ResolvedRules,
        now: Long,
        root: () -> UiNode?,
        out: MutableList<DetectionOutcome>,
    ) {
        val tree = root()
        val index = tree?.let { currentIndexFromTree(it, resolved) } ?: -1

        val previous = state.lastEnded
        if (previous != null && now - state.lastEndedAt <= config.resumeWindowMillis) {
            // Back within seconds (notification shade, quick app switch): same sitting.
            state.session = previous
            state.lastEnded = null
            out += SurfaceEntered(state.app, previous.id)
            if (index >= 0 && index !in previous.countedIndexes) {
                previous.lastIndex = index
                previous.pending = Pending(index = index, virtual = false, isEntry = true)
            }
            return
        }

        val session = Session(id = nextSessionId++)
        session.pageHeight = tree?.let { pagerHeight(it, resolved) } ?: screen.height
        session.entryIndexUnknown = index < 0
        state.session = session
        state.lastEnded = null
        out += SurfaceEntered(state.app, session.id)
        session.lastIndex = index
        if (now - connectedAt <= config.connectGraceMillis) {
            // Joined mid-sitting (the service just restarted): this reel was on screen before we were.
            if (index >= 0) session.countedIndexes += index else session.countedVirtual += 0
            return
        }
        // The reel showing on entry is a watched reel too. Without an index it is page 0.
        session.pending = if (index >= 0) {
            Pending(index = index, virtual = false, isEntry = true)
        } else {
            Pending(index = 0, virtual = true, isEntry = true)
        }
    }

    private fun endSession(state: AppState, now: Long, out: MutableList<DetectionOutcome>) {
        val session = state.session ?: return
        state.session = null
        state.lastEnded = session
        state.lastEndedAt = now
        session.pending = null
        out += SurfaceLeft(state.app, session.id)
    }

    private fun onPagerScrolled(event: DetectorEvent, session: Session) {
        // A feed refresh replaces the items: indexes restart, so forget the old ones.
        if (event.itemCount >= 0 && session.lastItemCount > 4 && event.itemCount < session.lastItemCount / 2) {
            session.countedIndexes.clear()
            session.lastIndex = -1
        }
        if (event.itemCount >= 0) session.lastItemCount = event.itemCount

        if (event.fromIndex >= 0 && event.toIndex >= 0) {
            session.lastScrollAt = event.timeMillis
            if (session.entryIndexUnknown) {
                session.entryIndexUnknown = false
                // Instagram reports where the pager already is right after the player opens (no
                // movement). That is the entry reel, which was already handled: remember its index.
                if (event.scrollDeltaY == 0 && event.fromIndex == event.toIndex) {
                    session.lastIndex = event.fromIndex
                    session.countedIndexes += event.fromIndex
                    return
                }
            }
            // Two pages visible means the swipe is still moving; wait until one page fills the pager.
            if (event.fromIndex != event.toIndex) return
            val index = event.fromIndex
            if (index == session.lastIndex) return
            // Some pagers report the next index halfway through a swipe; flushPending waits for the
            // pager to settle, so a swipe that snaps back never counts.
            session.lastIndex = index
            session.pending = Pending(index = index, virtual = false, isEntry = false)
            return
        }

        // No index data (YouTube Shorts, for one): add up this swipe's distance; settleSwipe turns it
        // into whole pages once the pager comes to rest.
        session.swipeDeltaY += event.scrollDeltaY
        session.lastScrollAt = event.timeMillis
    }

    private fun settleSwipe(session: Session, now: Long) {
        if (session.swipeDeltaY == 0L) return
        val idle = now - session.lastScrollAt
        if (idle < config.settleMillis) return
        val pages = session.swipeDeltaY.toDouble() / session.pageHeight.coerceAtLeast(1)
        val whole = pages.roundToInt()
        val tolerance = maxOf(config.pageSnapTolerance.toDouble(), 0.1 * abs(whole))
        // Far from a page boundary: the finger paused mid-drag or events are still arriving. Keep adding up.
        if (abs(pages - whole) > tolerance && idle < config.forceSettleMillis) return
        session.swipeDeltaY = 0
        if (whole == 0) return // snapped back
        session.virtualIndex += whole
        session.pending = Pending(index = session.virtualIndex, virtual = true, isEntry = false)
    }

    private fun flushPending(
        state: AppState,
        session: Session,
        resolved: ResolvedRules,
        now: Long,
        root: () -> UiNode?,
        out: MutableList<DetectionOutcome>,
    ) {
        val pending = session.pending ?: return
        if (now - session.lastCountAt < config.debounceMillis) return
        if (!pending.isEntry && now - session.lastScrollAt < config.settleMillis) return
        session.pending = null

        val counted = if (pending.virtual) session.countedVirtual else session.countedIndexes
        if (pending.index in counted) return

        val fingerprintIds = (resolved as? ResolvedRules.Specific)?.rules?.fingerprintViewIds.orEmpty()
        val fingerprint = if (fingerprintIds.isEmpty()) null else root()?.let { Fingerprint.of(it, fingerprintIds, screen) }

        if (fingerprint != null) {
            val duplicate = (pending.virtual && fingerprint in session.virtualFingerprints) ||
                (pending.isEntry && state.recentlyCounted(fingerprint, now, config.reentryMemoryMillis))
            if (duplicate) {
                counted += pending.index
                return
            }
        }

        session.lastCountAt = now
        counted += pending.index
        if (fingerprint != null) {
            if (pending.virtual) session.virtualFingerprints += fingerprint
            state.rememberCounted(fingerprint, now)
        }

        out += ReelCounted(
            app = state.app,
            estimated = resolved == ResolvedRules.Generic,
            surfaceSessionId = session.id,
            timeMillis = now,
        )
    }

    private fun currentIndexFromTree(root: UiNode, resolved: ResolvedRules): Int {
        val pager = findPager(root, resolved) ?: return -1
        var best: UiNode? = null
        var bestArea = 0L
        for (i in 0 until pager.childCount) {
            val child = pager.child(i) ?: continue
            val area = screen.visibleArea(child.bounds)
            if (area > bestArea) {
                best = child
                bestArea = area
            }
        }
        return best?.collectionRowIndex ?: -1
    }

    /** One page of the pager, for turning scroll distance into pages. */
    private fun pagerHeight(root: UiNode, resolved: ResolvedRules): Int =
        findPager(root, resolved)?.bounds?.height?.takeIf { it > 0 } ?: screen.height

    private fun findPager(root: UiNode, resolved: ResolvedRules): UiNode? = when (resolved) {
        is ResolvedRules.Specific -> resolved.rules.pagerViewIds.firstNotNullOfOrNull { id ->
            root.findByViewId(id).firstOrNull { it.isVisibleToUser }
        }
        ResolvedRules.Generic -> findGenericPager(root)
        ResolvedRules.Paused -> null
    }

    private fun findGenericPager(root: UiNode): UiNode? {
        val queue = ArrayDeque<UiNode>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < config.genericSearchNodeBudget) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable && node.isVisibleToUser && isPagerClass(node.className) &&
                screen.coverage(node.bounds) >= config.genericCoverage
            ) {
                return node
            }
            for (i in 0 until node.childCount) node.child(i)?.let(queue::addLast)
        }
        return null
    }

    private fun isPagerClass(className: String?): Boolean =
        className != null && (className.contains("RecyclerView") || className.contains("ViewPager"))

    private class AppState(val app: ReelApp) {
        var resolved: ResolvedRules? = null
        var resolvedForVersion: Long? = null
        var status: CountingStatus? = null
        var session: Session? = null
        var lastEnded: Session? = null
        var lastEndedAt: Long = Long.MIN_VALUE / 2
        var lastTreeCheckAt: Long = Long.MIN_VALUE / 2
        var lastSurfaceSeenAt: Long = Long.MIN_VALUE / 2

        /** Fingerprint -> time counted, for re-entry de-duplication across sittings. */
        private val recent = LinkedHashMap<Long, Long>()

        fun recentlyCounted(fingerprint: Long, now: Long, memoryMillis: Long): Boolean {
            val at = recent[fingerprint] ?: return false
            return now - at <= memoryMillis
        }

        fun rememberCounted(fingerprint: Long, now: Long) {
            recent.remove(fingerprint)
            recent[fingerprint] = now
            while (recent.size > RECENT_CAPACITY) recent.remove(recent.keys.first())
        }
    }

    private class Session(val id: Long) {
        val countedIndexes = HashSet<Int>()
        val countedVirtual = HashSet<Int>()
        val virtualFingerprints = HashSet<Long>()
        var lastIndex = -1
        var lastItemCount = -1
        var lastCountAt = Long.MIN_VALUE / 2
        var pageHeight = 1
        /** The sitting started without knowing the pager index (Instagram's pages carry none). */
        var entryIndexUnknown = false
        var swipeDeltaY = 0L
        var lastScrollAt = Long.MIN_VALUE / 2
        var virtualIndex = 0
        var pending: Pending? = null
    }

    /** A reel waiting to count. [virtual] positions come from scroll distance, not the app's index. */
    private data class Pending(val index: Int, val virtual: Boolean, val isEntry: Boolean)

    private companion object {
        const val RECENT_CAPACITY = 64
    }
}
