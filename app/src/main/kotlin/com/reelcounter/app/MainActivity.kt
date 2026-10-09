package com.reelcounter.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reelcounter.app.navigation.ReelCounterNavHost
import com.reelcounter.core.data.DeepLinks
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.app.widget.ReelCountWidgetReceiver
import com.reelcounter.core.model.AppLook
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppUiState(val look: AppLook, val onboardingComplete: Boolean)

@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** Null until settings load, so the first frame never flashes the wrong screen. */
    val state: StateFlow<AppUiState?> = settings.settings
        .map { AppUiState(it.look, it.onboardingComplete) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val destination = MutableStateFlow<String?>(null)
    val pendingDestination: StateFlow<String?> = destination.asStateFlow()

    private val health = MutableStateFlow(false)
    val healthRequested: StateFlow<Boolean> = health.asStateFlow()

    fun onIntent(intent: Intent?) {
        when (val target = intent?.getStringExtra(DeepLinks.EXTRA_DESTINATION)) {
            DeepLinks.HEALTH -> {
                health.value = true
                destination.value = target
            }
            null -> Unit
            else -> destination.value = target
        }
        intent?.removeExtra(DeepLinks.EXTRA_DESTINATION)
    }

    fun destinationHandled() {
        destination.value = null
    }

    fun healthHandled() {
        health.value = false
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    /** Set once the first screen has its data; until then the splash stays up instead of a blank frame. */
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.onIntent(intent)
        holdSplashUntilContentReady()

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val app = state ?: return@setContent
            val pending by viewModel.pendingDestination.collectAsStateWithLifecycle()
            val health by viewModel.healthRequested.collectAsStateWithLifecycle()
            val dark = app.look == AppLook.MIDNIGHT
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            RcTheme(look = app.look) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReelCounterNavHost(
                        onContentReady = { contentReady = true },
                        onboardingComplete = app.onboardingComplete,
                        pendingDestination = pending,
                        onDestinationHandled = viewModel::destinationHandled,
                        healthRequested = health,
                        onHealthRequestHandled = viewModel::healthHandled,
                        onAddWidget = ::requestWidget,
                    )
                }
            }
        }
    }

    /**
     * Skips drawing until [contentReady] (the platform keeps the splash, or the window background
     * before Android 12, on screen meanwhile), capped so a slow disk can never hold the app hostage.
     */
    private fun holdSplashUntilContentReady() {
        val content: View = findViewById(android.R.id.content)
        val start = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!contentReady && SystemClock.uptimeMillis() - start < MAX_SPLASH_MS) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    /** Asks the launcher to place our widget; falls back to the app's details when it can't. */
    private fun requestWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, ReelCountWidgetReceiver::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(this, "Long-press your home screen, tap Widgets, then find Reel Counter.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onIntent(intent)
    }

    private companion object {
        const val MAX_SPLASH_MS = 2_000L
    }
}
