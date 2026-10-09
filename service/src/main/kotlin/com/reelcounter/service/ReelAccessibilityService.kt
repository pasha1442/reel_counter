package com.reelcounter.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.reelcounter.core.detection.ScreenSize
import com.reelcounter.core.detection.UiNode
import com.reelcounter.core.model.ReelApp
import com.reelcounter.service.overlay.BlockOverlay
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel

/**
 * The only component that talks to the Accessibility framework. It copies each event, hands it
 * to [CounterController] on a dedicated worker thread, and never lets an error crash the service
 * (a crashing service can be switched off by the system).
 */
@AndroidEntryPoint
class ReelAccessibilityService : AccessibilityService(), CounterHost {

    @Inject lateinit var controller: CounterController

    private val worker = HandlerThread("reel-detector")
    private var scope: CoroutineScope? = null
    private var blockOverlay: BlockOverlay? = null
    private val versionCache = HashMap<String, Pair<Long?, Long>>()
    private var screenReceiverRegistered = false

    override val overlay: BlockOverlay
        get() = blockOverlay ?: BlockOverlay(this).also { blockOverlay = it }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) guarded { controller.onScreenOff() }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        guarded {
            watchHomeScreen()
            worker.start()
            val errors = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Counter error", e) }
            val workerScope = CoroutineScope(SupervisorJob() + Handler(worker.looper).asCoroutineDispatcher() + errors)
            scope = workerScope
            controller.start(this, workerScope)
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            screenReceiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || scope == null) return
        guarded {
            val snapshot = event.toDetectorEvent(System.currentTimeMillis()) ?: return@guarded
            controller.submit(snapshot)
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        guarded { if (scope != null) controller.onScreenChanged(screen()) }
    }

    override fun onDestroy() {
        guarded {
            blockOverlay?.hide()
            if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
            // Never block the main thread while the system is tearing us down.
            if (scope != null) controller.stop()
            scope?.cancel()
            worker.quitSafely()
        }
        scope = null
        super.onDestroy()
    }

    // CounterHost

    override fun root(packageName: String): UiNode? {
        val active = rootInActiveWindow
        if (active != null && active.packageName == packageName) return AndroidUiNode(active)
        // Our own block page or the keyboard can hold focus; find the app's window instead.
        for (window in windows.orEmpty()) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            if (root.packageName == packageName) return AndroidUiNode(root)
        }
        return null
    }

    override fun activeWindowPackage(): String? = rootInActiveWindow?.packageName?.toString()

    override fun versionCode(packageName: String): Long? {
        val now = SystemClock.elapsedRealtime()
        versionCache[packageName]?.let { (code, at) -> if (now - at < VERSION_TTL_MS) return code }
        val code = try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        versionCache[packageName] = code to now
        return code
    }

    override fun screen(): ScreenSize {
        val display = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics) ?: return ScreenSize(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        return ScreenSize(metrics.widthPixels, metrics.heightPixels)
    }

    override fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun recordingsDirectory(): File? = getExternalFilesDir("recordings")

    /**
     * Adds the home screen app to our event filter so we know when the user leaves Instagram or
     * YouTube. We only look at its package name, never its screen content.
     */
    private fun watchHomeScreen() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != packageName && it != "android" && it != "com.android.settings" }
        val info = serviceInfo ?: return
        info.packageNames = (ReelApp.entries.map { it.packageName } + launchers).distinct().toTypedArray()
        serviceInfo = info
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "Accessibility callback failed", t)
        }
    }

    private companion object {
        const val TAG = "ReelCounter"
        const val VERSION_TTL_MS = 10 * 60_000L
    }
}
