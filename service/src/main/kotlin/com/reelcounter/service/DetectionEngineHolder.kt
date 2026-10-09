package com.reelcounter.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.reelcounter.core.detection.DetectionEngine
import com.reelcounter.core.detection.RulesPack
import com.reelcounter.core.detection.ScreenSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one [DetectionEngine] for the whole app process, so the sitting in progress (reels already
 * counted, pager position) survives Android restarting the accessibility service. Some phones do
 * that every few seconds: on a OnePlus with an oversized secure setting, every failed settings write
 * rebinds all accessibility services. A fresh engine per service instance recounted the reel on
 * screen after each restart.
 *
 * Only the service's worker thread uses the engine; [lock] covers the hand-over between an old
 * service instance shutting down and a new one starting.
 */
@Singleton
class DetectionEngineHolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val lock = Any()

    private var engine: DetectionEngine? = null
    private var detachedAt: Long? = null
    private val versionCache = HashMap<String, Pair<Long?, Long>>()

    /** The engine for a newly connected service. Returns it with whether it was reused. */
    fun attach(pack: RulesPack, screen: ScreenSize, now: Long): Pair<DetectionEngine, Boolean> = synchronized(lock) {
        val existing = engine
        val gap = detachedAt?.let { now - it }
        detachedAt = null
        if (existing != null) {
            existing.updateScreen(screen)
            if (gap != null && gap > RESUME_AFTER_RESTART_MS) {
                // Away too long to trust the old sitting: start clean, and don't count the reel
                // that happens to be on screen as the service comes back.
                existing.reset()
                existing.onServiceConnected(now)
            }
            return existing to true
        }
        val fresh = DetectionEngine(pack, ::versionCode, screen)
        // A new process: if the user is already in Reels, that reel was likely counted before.
        fresh.onServiceConnected(now)
        engine = fresh
        fresh to false
    }

    fun detach(now: Long) = synchronized(lock) {
        detachedAt = now
    }

    private fun versionCode(packageName: String): Long? {
        val now = SystemClock.elapsedRealtime()
        versionCache[packageName]?.let { (code, at) -> if (now - at < VERSION_TTL_MS) return code }
        val code = try {
            context.packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        versionCache[packageName] = code to now
        return code
    }

    private companion object {
        /** A restart shorter than this continues the same sitting. */
        const val RESUME_AFTER_RESTART_MS = 30_000L
        const val VERSION_TTL_MS = 60_000L
    }
}
