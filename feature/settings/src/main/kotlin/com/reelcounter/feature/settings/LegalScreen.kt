package com.reelcounter.feature.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.reelcounter.core.data.LegalPages
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.theme.RcMotion
import com.reelcounter.core.design.theme.RcTheme
import kotlinx.coroutines.delay

/**
 * Privacy Policy, Terms of Use and account deletion, read from the same files that are published
 * on the web (docs/, bundled as assets). Links between them stay in here; anything else (email,
 * the web) opens outside the app.
 *
 * The WebView is created only after the screen has finished sliding in: the first WebView in a
 * process takes a second or two to start, and starting it mid-transition froze the fade-in.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LegalRoute(page: String, onBack: () -> Unit) {
    val theme = if (RcTheme.tokens.dark) "dark" else "light"
    val background = MaterialTheme.colorScheme.background
    var started by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(RcMotion.MEDIUM + 80L)
        started = true
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        // Drawn above the page: while the first WebView of a run starts up, it can paint outside
        // its own bounds for a moment, which hid the bar.
        RcTopBar(title = LegalPages.title(page), onBack = onBack, modifier = Modifier.zIndex(1f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (started) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            // Transparent: the page paints its own background, in the app's colours.
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            // Only our own bundled pages run here; the script fills in the contact details.
                            settings.javaScriptEnabled = true
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    loaded = true
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val url = request.url
                                    if (url.scheme == "file" && url.path.orEmpty().startsWith("/android_asset/")) {
                                        // Keep the app's look on pages opened from links.
                                        if (url.getQueryParameter("theme") == null) {
                                            view.loadUrl(url.buildUpon().appendQueryParameter("theme", theme).appendQueryParameter("embed", "1").build().toString())
                                            return true
                                        }
                                        return false
                                    }
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                                    return true
                                }
                            }
                            loadUrl("file:///android_asset/$page.html?theme=$theme&embed=1")
                        }
                    },
                )
            }
            if (!loaded) {
                Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.padding(top = 48.dp).size(28.dp), strokeWidth = 2.5.dp)
                }
            }
        }
    }
}
