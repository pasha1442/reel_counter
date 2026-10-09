package com.reelcounter.service.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.ReelApp

interface BlockCallbacks {
    fun onLeave()

    fun onUnlock()
}

/**
 * Covers only the Reels / Shorts player with our block page. Uses the accessibility overlay
 * window type, so no "display over other apps" permission is needed.
 */
class BlockOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val state = mutableStateOf<BlockUiState?>(null)
    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null

    var shownFor: ReelApp? = null
        private set

    val isShowing: Boolean get() = root != null

    @MainThread
    fun show(ui: BlockUiState, callbacks: BlockCallbacks) {
        state.value = ui
        shownFor = ui.app
        if (root != null) return

        val lifecycleOwner = OverlayLifecycleOwner().also { it.create() }
        val compose = ComposeView(service).apply {
            setContent {
                state.value?.let { current ->
                    RcTheme(look = current.look) { BlockScreen(state = current, callbacks = callbacks) }
                }
            }
        }
        val container = FrameLayout(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(compose)
        }
        try {
            windowManager.addView(container, layoutParams())
        } catch (e: RuntimeException) {
            lifecycleOwner.destroy()
            return
        }
        lifecycleOwner.resume()
        root = container
        owner = lifecycleOwner
    }

    @MainThread
    fun hide() {
        val view = root ?: return
        root = null
        shownFor = null
        runCatching { windowManager.removeViewImmediate(view) }
        owner?.destroy()
        owner = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Not focusable: the unlock task needs no keyboard, and the app underneath keeps key focus
        // (system Back then leaves the player, which also dismisses this page).
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
    }
}

/** Compose needs a lifecycle and saved-state owner; a window added by a service has neither. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun create() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
