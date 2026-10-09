package com.reelcounter.core.detection

import com.reelcounter.core.detection.DetectionOutcome.LeftSupportedApps
import com.reelcounter.core.detection.DetectionOutcome.ReelCounted
import com.reelcounter.core.detection.DetectionOutcome.StatusChanged
import com.reelcounter.core.detection.DetectionOutcome.SurfaceEntered
import com.reelcounter.core.detection.DetectionOutcome.SurfaceLeft
import com.reelcounter.core.model.CountingStatus
import com.reelcounter.core.model.ReelApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetectionEngineTest {

    private class Harness(pack: RulesPack = testPack, version: Long? = 1_000) {
        private var screen: FakeNode? = null
        val engine = DetectionEngine(pack, { version }, SCREEN)
        val outcomes = mutableListOf<DetectionOutcome>()

        fun send(event: DetectorEvent, onScreen: FakeNode? = screen): List<DetectionOutcome> {
            screen = onScreen
            return engine.process(event) { screen }.also { outcomes += it }
        }

        /** Time passes with no new events (the service's timer tick). */
        fun settle(t: Long) {
            outcomes += engine.tick(t) { screen }
        }

        val counted: Int get() = outcomes.count { it is ReelCounted }
    }

    @Test
    fun `entering the reels player counts the reel on screen`() {
        val h = Harness()
        val out = h.send(windowChanged(0), reelsScreen(0))

        assertTrue(out.contains(StatusChanged(ReelApp.INSTAGRAM, CountingStatus.ACTIVE)))
        assertTrue(out.any { it is SurfaceEntered })
        assertEquals(1, h.counted)
    }

    @Test
    fun `swiping to the next reel counts once after it settles`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0))

        h.send(pagerScrolled(1_000, from = 0, to = 1), reelsScreen(0))
        assertEquals(1, h.counted, "mid-swipe must not count")

        h.send(pagerScrolled(1_100, from = 1), reelsScreen(1))
        h.send(pagerScrolled(1_200, from = 1), reelsScreen(1))
        h.send(contentChanged(1_300), reelsScreen(1))
        assertEquals(1, h.counted, "the pager is still moving")
        h.send(contentChanged(1_600), reelsScreen(1))
        assertEquals(2, h.counted)
    }

    @Test
    fun `scrolling back to a reel already seen does not count again`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0))
        h.send(pagerScrolled(1_000, from = 1), reelsScreen(1))
        h.send(pagerScrolled(2_000, from = 2), reelsScreen(2))
        h.send(pagerScrolled(3_000, from = 1), reelsScreen(1))
        h.send(pagerScrolled(4_000, from = 0), reelsScreen(0))
        assertEquals(3, h.counted)

        h.send(pagerScrolled(5_000, from = 3), reelsScreen(3))
        h.settle(5_500)
        assertEquals(4, h.counted)
    }

    @Test
    fun `fast flicks only count the reel the user lands on`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0))
        h.send(pagerScrolled(1_000, from = 1), reelsScreen(1))
        h.send(pagerScrolled(1_200, from = 2), reelsScreen(2))
        h.send(pagerScrolled(1_350, from = 3), reelsScreen(3))
        assertEquals(1, h.counted, "reels 1 and 2 were flicked past, never settled on")

        h.send(contentChanged(1_800), reelsScreen(3))
        assertEquals(2, h.counted, "the reel the user stopped on counts once the pager settles")
    }

    @Test
    fun `the home feed is not counted`() {
        val h = Harness()
        h.send(windowChanged(0), feedScreen())
        h.send(contentChanged(500), feedScreen())
        h.send(contentChanged(1_000), feedScreen())
        assertEquals(0, h.counted)
        assertTrue(h.outcomes.none { it is SurfaceEntered })
    }

    @Test
    fun `going to the home screen ends the sitting`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0))
        val out = h.send(windowChanged(5_000, pkg = LAUNCHER), null)

        assertTrue(out.any { it is SurfaceLeft })
        assertTrue(out.contains(LeftSupportedApps))
    }

    @Test
    fun `re-entering on the same reel does not count it twice`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(3))
        h.send(windowChanged(2_000, pkg = LAUNCHER), null)
        h.send(windowChanged(4_000), reelsScreen(3))
        assertEquals(1, h.counted)

        h.send(windowChanged(6_000, pkg = LAUNCHER), null)
        h.send(windowChanged(8_000), reelsScreen(7))
        assertEquals(2, h.counted)
    }

    @Test
    fun `player hidden for a moment keeps the sitting open`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0))
        h.send(contentChanged(500), feedScreen())
        h.send(contentChanged(1_000), reelsScreen(0))
        h.send(contentChanged(1_500), feedScreen())
        assertTrue(h.outcomes.none { it is SurfaceLeft })

        h.send(contentChanged(3_100), feedScreen())
        assertTrue(h.outcomes.any { it is SurfaceLeft })
        assertEquals(1, h.counted)
    }

    @Test
    fun `a broken app version pauses counting instead of guessing`() {
        val pack = RulesPack(1, listOf(testPack.apps.first { it.packageName == IG }.copy(broken = true)))
        val h = Harness(pack)
        val out = h.send(windowChanged(0), reelsScreen(0))

        assertTrue(out.contains(StatusChanged(ReelApp.INSTAGRAM, CountingStatus.PAUSED)))
        assertEquals(0, h.counted)
    }

    @Test
    fun `an unknown app version falls back to estimated counting`() {
        val pack = RulesPack(1, listOf(testPack.apps.first { it.packageName == IG }.copy(maxVersionCode = 500)))
        val h = Harness(pack, version = 1_000)
        val out = h.send(windowChanged(0), reelsScreen(0))
        h.send(pagerScrolled(1_000, from = 1), reelsScreen(1))
        h.settle(1_500)

        assertTrue(out.contains(StatusChanged(ReelApp.INSTAGRAM, CountingStatus.ESTIMATED)))
        val counts = h.outcomes.filterIsInstance<ReelCounted>()
        assertEquals(2, counts.size)
        assertTrue(counts.all { it.estimated })
    }

    @Test
    fun `scroll distance advances when the pager reports no index`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0, rowIndex = false))
        // Half a page, then the finger lifts and the pager snaps back.
        h.send(pagerScrolled(1_000, from = -1, to = -1, count = -1, deltaY = 1_200), reelsScreen(0, rowIndex = false))
        h.send(pagerScrolled(1_100, from = -1, to = -1, count = -1, deltaY = -1_200), reelsScreen(0, rowIndex = false))
        h.send(contentChanged(1_600), reelsScreen(0, rowIndex = false))
        assertEquals(1, h.counted, "a swipe that snaps back moves nothing")

        h.send(pagerScrolled(2_000, from = -1, to = -1, count = -1, deltaY = 1_200), reelsScreen(1, rowIndex = false))
        h.send(pagerScrolled(2_100, from = -1, to = -1, count = -1, deltaY = 1_050), reelsScreen(1, rowIndex = false))
        h.send(contentChanged(2_600), reelsScreen(1, rowIndex = false))
        assertEquals(2, h.counted)

        h.send(pagerScrolled(3_000, from = -1, to = -1, count = -1, deltaY = -2_250), reelsScreen(0, rowIndex = false))
        h.send(contentChanged(3_600), reelsScreen(0, rowIndex = false))
        assertEquals(2, h.counted, "back on a reel that was already counted")
    }

    @Test
    fun `a feed refresh resets pager indexes`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0))
        h.send(pagerScrolled(1_000, from = 1), reelsScreen(1))
        h.send(pagerScrolled(2_000, from = 2), reelsScreen(2))
        h.send(pagerScrolled(3_000, from = 0, count = 10), reelsScreen(0, author = "fresh"))
        h.settle(3_500)
        assertEquals(4, h.counted)
    }

    @Test
    fun `home screen content changes do not end the sitting`() {
        val h = Harness()
        h.send(windowChanged(0), reelsScreen(0))
        val out = h.send(contentChanged(300, pkg = LAUNCHER), null)
        assertTrue(out.isEmpty())
        assertTrue(h.engine.hasActiveSurface(ReelApp.INSTAGRAM))
    }

    @Test
    fun `a quick trip away and back resumes the same sitting`() {
        val h = Harness()
        h.send(windowChanged(0, pkg = YT), shortsScreen())
        h.send(windowChanged(1_000, pkg = LAUNCHER), null)
        h.send(windowChanged(1_500, pkg = YT), shortsScreen())
        assertEquals(1, h.counted, "the Short on screen was already counted")
    }

    @Test
    fun `shorts without indexes count by whole pages of scroll`() {
        val h = Harness()
        val screen = shortsScreen()
        var t = 0L
        /** Deltas 50 ms apart, then the Short keeps playing (content changes) until the pager settles. */
        fun swipe(vararg deltas: Int) {
            deltas.forEach { dy -> h.send(shortsScrolled(t, dy), screen).also { t += 50 } }
            t += 600
            h.send(contentChanged(t, pkg = YT), screen)
            t += 400
        }

        h.send(windowChanged(t, pkg = YT), screen)
        t += 1_000
        assertEquals(1, h.counted, "the Short showing on entry")

        swipe(860, -668, -191, -1) // recorded snap-back
        assertEquals(1, h.counted)

        // Recorded on YouTube 20.10: a full swipe reports ~2075 px against a 2211 px page.
        swipe(529, 392, 929, 225)
        assertEquals(2, h.counted)
        swipe(560, 768, 747)
        assertEquals(3, h.counted)

        swipe(-485, -650, -767, -173)
        swipe(529, 392, 929, 225)
        assertEquals(3, h.counted, "back and forth over Shorts already seen")

        // Twenty more swipes: under-reported distance must not drift into missed counts.
        repeat(20) { swipe(529, 392, 929, 225) }
        assertEquals(23, h.counted)
    }

    @Test
    fun `a settled swipe counts on a timer tick when the app sends nothing else`() {
        val h = Harness()
        val screen = shortsScreen()
        h.send(windowChanged(0, pkg = YT), screen)
        listOf(529, 392, 929, 225).forEachIndexed { i, dy -> h.send(shortsScrolled(1_000L + i * 50, dy), screen) }
        val wake = requireNotNull(h.engine.nextTickAt(1_150))
        assertTrue(wake in 1_151..1_600, "wake=$wake")
        h.outcomes += h.engine.tick(wake) { screen }
        assertEquals(2, h.counted)
        assertEquals(null, h.engine.nextTickAt(wake + 1))
    }

    @Test
    fun `a slow drag that pauses mid-page waits until it lands`() {
        val h = Harness()
        val screen = shortsScreen()
        h.send(windowChanged(0, pkg = YT), screen)
        // 65% of a page, then an 800 ms pause (seen on a slow device), then the rest of the swipe.
        h.send(shortsScrolled(1_000, 1_443), screen)
        h.outcomes += h.engine.tick(1_800) { screen }
        assertEquals(1, h.counted, "not a page yet")
        h.send(shortsScrolled(1_850, 632), screen)
        h.outcomes += h.engine.tick(2_300) { screen }
        assertEquals(2, h.counted)

        // The same pause followed by letting go: the pager snaps back.
        h.send(shortsScrolled(4_000, 1_443), screen)
        h.outcomes += h.engine.tick(4_800) { screen }
        h.send(shortsScrolled(4_850, -1_443), screen)
        h.outcomes += h.engine.tick(5_300) { screen }
        assertEquals(2, h.counted)
    }

    // Recorded on a OnePlus (Android 13) with Instagram 444: the pager's pages carry no row index,
    // Instagram reports the current index right after the player opens, and it switches to the
    // next index halfway through a swipe.
    @Test
    fun `instagram's opening index report is the entry reel, not a new one`() {
        val h = Harness()
        val screen = reelsScreen(18, rowIndex = false)
        h.send(windowChanged(0), screen)
        h.send(pagerScrolled(130, from = 18, count = 24, deltaY = 0), screen)
        h.settle(1_000)
        assertEquals(1, h.counted)
    }

    @Test
    fun `instagram counts a swipe once the pager settles`() {
        val h = Harness()
        val screen = reelsScreen(18, rowIndex = false)
        h.send(windowChanged(0), screen)
        h.send(pagerScrolled(130, from = 18, count = 24, deltaY = 0), screen)

        h.send(pagerScrolled(5_000, from = 19, count = 24, deltaY = 1_009), screen)
        h.send(pagerScrolled(5_095, from = 19, count = 24, deltaY = 1_080), screen)
        h.send(pagerScrolled(5_196, from = 19, count = 24, deltaY = 12), screen)
        assertEquals(1, h.counted, "still moving")
        h.settle(5_600)
        assertEquals(2, h.counted)
    }

    @Test
    fun `a swipe past halfway that snaps back does not count`() {
        val h = Harness()
        val screen = reelsScreen(18, rowIndex = false)
        h.send(windowChanged(0), screen)
        h.send(pagerScrolled(130, from = 18, count = 24, deltaY = 0), screen)

        h.send(pagerScrolled(5_000, from = 19, count = 24, deltaY = 1_200), screen)
        h.send(pagerScrolled(5_150, from = 18, count = 24, deltaY = -1_200), screen)
        h.settle(6_000)
        assertEquals(1, h.counted)
    }

    @Test
    fun `a service restart mid-sitting does not recount the reel on screen`() {
        val h = Harness()
        h.engine.onServiceConnected(10_000)
        h.send(windowChanged(10_400), reelsScreen(5))
        assertEquals(0, h.counted, "this reel was on screen before the service came back")

        h.send(pagerScrolled(12_000, from = 6), reelsScreen(6))
        h.settle(12_500)
        assertEquals(1, h.counted, "the next reel counts normally")
    }

    @Test
    fun `a sitting that starts well after the service connects counts its first reel`() {
        val h = Harness()
        h.engine.onServiceConnected(0)
        h.send(windowChanged(60_000), reelsScreen(0))
        assertEquals(1, h.counted)
    }
}
