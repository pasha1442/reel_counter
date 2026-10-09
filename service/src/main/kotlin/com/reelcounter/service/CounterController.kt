package com.reelcounter.service

import android.content.Context
import android.util.Log
import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.LiveCounterState
import com.reelcounter.core.data.ReelCountListener
import com.reelcounter.core.data.repository.BrainRepository
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.repository.RulesRepository
import com.reelcounter.core.data.settings.AppSettings
import com.reelcounter.core.data.settings.DayState
import com.reelcounter.core.data.settings.RuntimeStateRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.design.brain.nameRes
import com.reelcounter.core.detection.DetectionEngine
import com.reelcounter.core.detection.DetectionOutcome
import com.reelcounter.core.detection.DetectorEvent
import com.reelcounter.core.detection.EventKind
import com.reelcounter.core.detection.ScreenSize
import com.reelcounter.core.detection.UiNode
import com.reelcounter.core.limits.BlockDecision
import com.reelcounter.core.limits.BlockReason
import com.reelcounter.core.limits.BrainMath
import com.reelcounter.core.limits.LimitEngine
import com.reelcounter.core.limits.Nudges
import com.reelcounter.core.limits.UnlockRules
import com.reelcounter.core.model.BrainState
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.ReelEvent
import com.reelcounter.service.notify.CounterNotifications
import com.reelcounter.service.overlay.BlockCallbacks
import com.reelcounter.service.overlay.BlockOverlay
import com.reelcounter.service.overlay.BlockUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the controller needs from the running AccessibilityService. */
interface CounterHost {
    val overlay: BlockOverlay

    fun root(packageName: String): UiNode?

    /** Package of the window that has focus, if any. */
    fun activeWindowPackage(): String?

    fun versionCode(packageName: String): Long?

    fun screen(): ScreenSize

    fun goBack()

    fun recordingsDirectory(): File?
}

/**
 * Everything the service does with an event, off the main thread and in order:
 * detection -> limit check -> record -> nudges, widget and live notification.
 */
class CounterController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val reels: ReelRepository,
    private val settingsRepository: SettingsRepository,
    private val runtime: RuntimeStateRepository,
    private val entitlements: EntitlementRepository,
    private val rules: RulesRepository,
    private val brainRepository: BrainRepository,
    private val limitEngine: LimitEngine,
    private val live: LiveCounterState,
    private val notifications: CounterNotifications,
    private val listeners: Set<@JvmSuppressWildcards ReelCountListener>,
    private val config: AppConfig,
    private val engines: DetectionEngineHolder,
) {
    private lateinit var host: CounterHost
    private lateinit var scope: CoroutineScope
    private lateinit var settings: StateFlow<AppSettings>
    private lateinit var isPro: StateFlow<Boolean>
    private lateinit var day: StateFlow<DayState>
    private lateinit var today: StateFlow<Map<ReelApp, Int>>
    private lateinit var brain: StateFlow<BrainState>

    private var engine: DetectionEngine? = null
    private var recorder: DetectionRecorder? = null
    private var publishJob: Job? = null
    private var lastPublishAt = 0L
    private val work = Channel<Work>(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var tickJob: Job? = null

    /** Events and timer ticks share one queue, so the engine always sees them in order. */
    private sealed interface Work {
        data class Event(val event: DetectorEvent) : Work

        data object Tick : Work
    }

    fun start(host: CounterHost, scope: CoroutineScope) {
        this.host = host
        this.scope = scope
        settings = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
        isPro = entitlements.isPro.stateIn(scope, SharingStarted.Eagerly, false)
        day = runtime.dayState.stateIn(scope, SharingStarted.Eagerly, DayState())
        today = reels.todayPerApp.stateIn(scope, SharingStarted.Eagerly, emptyMap())
        brain = brainRepository.brain.stateIn(scope, SharingStarted.Eagerly, BrainState.Fresh)

        notifications.ensureChannels()
        live.setServiceConnected(true)

        scope.launch { runtime.markServiceConnected() }
        scope.launch {
            val pack = rules.load()
            // One engine per process: if Android restarted just the service, the sitting carries on.
            val (attached, reused) = engines.attach(pack, host.screen(), System.currentTimeMillis())
            engine = attached
            if (config.isDebug) Log.d(DEBUG_TAG, "service connected, detector ${if (reused) "kept" else "new"}")
            launch { rules.pack.filterNotNull().collect { update -> withEngine { it.updateRules(update) } } }
            for (item in work) {
                when (item) {
                    is Work.Event -> process(item.event)
                    Work.Tick -> tick()
                }
                scheduleTick()
            }
        }
        scope.launch {
            settings.map { it.liveNotification }.distinctUntilChanged().collect { enabled ->
                if (enabled) publish(today.value.values.sum()) else notifications.cancelLive()
            }
        }
    }

    /** The shared engine, under its lock (a restarting service's old thread may still be finishing). */
    private inline fun <T> withEngine(block: (DetectionEngine) -> T): T? {
        val current = engine ?: return null
        return synchronized(engines.lock) { block(current) }
    }

    /** Called from the main thread for every event; processing happens on the service's worker thread. */
    fun submit(event: DetectorEvent) {
        work.trySend(Work.Event(event))
    }

    fun onScreenChanged(size: ScreenSize) {
        scope.launch { withEngine { it.updateScreen(size) } }
    }

    fun onScreenOff() {
        scope.launch {
            withEngine { it.reset() }?.forEach { handle(it) }
            withContext(Dispatchers.Main) { host.overlay.hide() }
        }
    }

    fun stop() {
        engines.detach(System.currentTimeMillis())
        if (config.isDebug) Log.d(DEBUG_TAG, "service disconnected")
        live.setServiceConnected(false)
        recorder?.close()
        tickJob?.cancel()
        // Outlives the service scope, which is cancelled right after this.
        CoroutineScope(Dispatchers.IO).launch { runtime.markServiceDisconnected() }
    }

    private suspend fun process(event: DetectorEvent) {
        val engine = engine ?: return
        val current = settings.value
        // No counting until the user has agreed to the disclosure in our onboarding.
        if (!current.disclosureAccepted) return

        // The home screen sends window events during app launch animations too. Only treat it as
        // "left" when a supported app is no longer the focused window.
        if (event.kind == EventKind.WINDOW_STATE_CHANGED && ReelApp.fromPackage(event.packageName) == null &&
            ReelApp.fromPackage(host.activeWindowPackage()) != null
        ) {
            return
        }
        val outcomes = synchronized(engines.lock) { engine.process(event) { host.root(event.packageName) } }
        if (config.isDebug) logForDebug(event, outcomes)
        if (config.isDebug && current.debugRecordDetection) recordForReplay(event)
        outcomes.forEach { handle(it) }
    }

    /** A swipe settling or a debounced reel, when the app itself sends no further events. */
    private suspend fun tick() {
        val engine = engine ?: return
        if (!settings.value.disclosureAccepted) return
        val outcomes = synchronized(engines.lock) {
            engine.tick(System.currentTimeMillis()) { app -> host.root(app.packageName) }
        }
        if (config.isDebug) outcomes.forEach { android.util.Log.d(DEBUG_TAG, "tick outcome $it") }
        outcomes.forEach { handle(it) }
    }

    private fun scheduleTick() {
        val now = System.currentTimeMillis()
        val next = withEngine { it.nextTickAt(now) }
        tickJob?.cancel()
        if (next == null) return
        tickJob = scope.launch {
            delay((next - now).coerceAtLeast(10))
            work.trySend(Work.Tick)
        }
    }

    private suspend fun handle(outcome: DetectionOutcome) {
        when (outcome) {
            is DetectionOutcome.StatusChanged -> live.setStatus(outcome.app, outcome.status)
            is DetectionOutcome.SurfaceEntered -> evaluateBlock(outcome.app)
            is DetectionOutcome.ReelCounted -> onReel(outcome)
            is DetectionOutcome.SurfaceLeft -> withContext(Dispatchers.Main) {
                if (host.overlay.shownFor == outcome.app) host.overlay.hide()
            }
            DetectionOutcome.LeftSupportedApps -> withContext(Dispatchers.Main) { host.overlay.hide() }
        }
    }

    private suspend fun onReel(outcome: DetectionOutcome.ReelCounted) {
        // A reel behind the block page was not watched.
        if (host.overlay.isShowing) return
        if (evaluateBlock(outcome.app)) return

        val before = today.value.values.sum()
        reels.record(
            ReelEvent(
                app = outcome.app,
                timeMillis = outcome.timeMillis,
                surfaceSessionId = outcome.surfaceSessionId,
                estimated = outcome.estimated,
            ),
        )
        val after = before + 1
        sendNudges(before, after)
        publish(after)
    }

    /** Shows the block page if this reel would go past a limit. Returns true when blocked. */
    private suspend fun evaluateBlock(app: ReelApp): Boolean {
        val totals = today.value
        val total = totals.values.sum()
        val current = settings.value
        val decision = limitEngine.decide(
            app = app,
            todayTotal = total,
            todayForApp = totals[app] ?: 0,
            settings = current.toLimitSettings(),
            isPro = isPro.value,
            unlock = day.value.unlock,
        )
        if (decision !is BlockDecision.Block) return false
        showBlock(app, decision.reason, total)
        return true
    }

    private suspend fun showBlock(app: ReelApp, reason: BlockReason, total: Int) {
        val current = settings.value
        val ui = BlockUiState(
            app = app,
            reason = reason,
            todayTotal = total,
            limit = current.dailyLimit,
            appLimit = current.toLimitSettings().perAppLimits[app],
            offer = UnlockRules.offer(current.strictness, day.value.unlocksUsed),
            rot = maxOf(brain.value.rot, 85f),
            look = current.look,
        )
        val callbacks = object : BlockCallbacks {
            override fun onLeave() {
                host.overlay.hide()
                host.goBack()
                // If Back did not leave the player (some screens need two), try once more.
                scope.launch {
                    delay(1_500)
                    if (withEngine { it.hasActiveSurface(app) } == true && !host.overlay.isShowing) {
                        withContext(Dispatchers.Main) { host.goBack() }
                    }
                }
            }

            override fun onUnlock() {
                host.overlay.hide()
                scope.launch {
                    runtime.updateDay { state ->
                        val granted = UnlockRules.grant(total, state.unlock)
                        state.copy(unlocksUsed = granted.unlocksUsed, allowedUntilCount = granted.allowedUntilCount)
                    }
                }
            }
        }
        withContext(Dispatchers.Main) { host.overlay.show(ui, callbacks) }
    }

    private suspend fun sendNudges(before: Int, after: Int) {
        val current = settings.value
        if (!current.nudgesEnabled) return
        val sent = day.value.nudgesSent
        val crossed = Nudges.crossed(before, after, current.dailyLimit).filter { it.name !in sent }
        if (crossed.isEmpty()) return
        notifications.nudge(
            threshold = crossed.last(),
            count = after,
            limit = current.dailyLimit,
            blocking = isPro.value && current.blockAtLimit,
        )
        runtime.updateDay { it.copy(nudgesSent = it.nudgesSent + crossed.map { t -> t.name }) }
    }

    /** Widget and status bar, at most every 1.5 s so fast scrolling doesn't hammer them. */
    private fun publish(total: Int) {
        publishJob?.cancel()
        publishJob = scope.launch {
            val wait = PUBLISH_INTERVAL_MS - (System.currentTimeMillis() - lastPublishAt)
            if (wait > 0) delay(wait)
            lastPublishAt = System.currentTimeMillis()
            val current = settings.value
            val limit = current.dailyLimit
            val state = BrainState(
                rot = BrainMath.rot(total, limit, brain.value.morningResidual),
                morningResidual = brain.value.morningResidual,
            )
            listeners.forEach { runCatching { it.onCountChanged(total, limit, state) } }
            if (current.liveNotification) {
                notifications.live(total, limit, context.getString(state.stage.nameRes()))
            }
        }
    }

    /** Debug builds: scroll signals and outcomes only (no screen content), for `adb logcat -s ReelDetect`. */
    private fun logForDebug(event: DetectorEvent, outcomes: List<DetectionOutcome>) {
        if (event.kind == EventKind.VIEW_SCROLLED) {
            android.util.Log.d(
                DEBUG_TAG,
                "scroll ${event.packageName.substringAfterLast('.')} src=${event.sourceViewId?.substringAfter('/')} " +
                    "cls=${event.sourceClassName?.substringAfterLast('.')} from=${event.fromIndex} to=${event.toIndex} " +
                    "count=${event.itemCount} dy=${event.scrollDeltaY}",
            )
        }
        outcomes.forEach { android.util.Log.d(DEBUG_TAG, "outcome $it") }
    }

    private suspend fun recordForReplay(event: DetectorEvent) {
        val dir = host.recordingsDirectory() ?: return
        val recorder = recorder ?: DetectionRecorder(dir).also { recorder = it }
        val pack = rules.load()
        recorder.record(
            event = event,
            pack = pack,
            root = if (ReelApp.fromPackage(event.packageName) != null) host.root(event.packageName) else null,
            screen = host.screen(),
            versionCode = host.versionCode(event.packageName),
        )
    }

    private companion object {
        const val PUBLISH_INTERVAL_MS = 1_500L
        const val DEBUG_TAG = "ReelDetect"
    }
}
