package com.reelcounter.feature.history

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.components.Pill
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.components.lookLabel
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.WatchTime
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STORE_LINK = "https://play.google.com/store/apps/details?id=com.reelcounter.android"

/** A story-sized card of the week, shared as an image through the normal Android share sheet. */
@Composable
fun ShareWeekRoute(onBack: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val week = state.week ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var showNumbers by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    val change = week.changePercent
    val range = "${week.start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${week.start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))}"
    val counted = week.days.filter { !it.date.isAfter(state.today) }
    val avg = if (counted.isEmpty()) 0 else counted.sumOf { it.total } / counted.size
    val rot = if (state.limit <= 0) 0f else (avg * 100f / state.limit).coerceIn(0f, 100f)

    fun share(save: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val bitmap = layer.toImageBitmap().asAndroidBitmap()
            val uri = withContext(Dispatchers.IO) { writeShareImage(context, bitmap) }
            busy = false
            val text = buildString {
                append(if (change != null && change < 0) "${-change}% fewer reels this week. " else "My reel week. ")
                append("Counting with Reel Counter: $STORE_LINK")
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, if (save) "Save or share" else "Share my week"))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        RcTopBar(title = "Share my week", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(270.dp)
                    .height(480.dp)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                // The card itself: what gets saved as the image.
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(if (sticker) 6.dp else 0.dp)
                        .lookSurface(RoundedCornerShape(26.dp), if (sticker) extended.cta else Color(0xFF1B1A18), raised = true, shadowSize = 6.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(lookLabel("Reel Counter"), style = MaterialTheme.typography.labelMedium, color = if (sticker) Color(0xFF111111) else Color.White, modifier = Modifier.weight(1f))
                        Text(range, style = MaterialTheme.typography.labelMedium, color = if (sticker) Color(0xFF111111) else Color(0xFFA39D94))
                    }
                    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                        Brain(rot = rot, glow = change != null && change < 0, modifier = Modifier.size(170.dp))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (showNumbers) "${week.total}" else if (change != null && change < 0) "${-change}%" else "My week",
                            style = MaterialTheme.typography.displayMedium,
                            color = if (sticker) Color(0xFF111111) else Color.White,
                        )
                        Text(
                            (if (showNumbers) "reels this week" else if (change != null && change < 0) "fewer reels than last week" else "of scrolling less"),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (sticker) Color(0xFF111111) else Color(0xFFD9D3CB),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (showNumbers) Pill("≈ ${WatchTime.minutes(week.total)} min", color = if (sticker) Color.White else Color(0xFF2C2A27), contentColor = if (sticker) Color(0xFF111111) else Color(0xFFD9D3CB))
                            if (change != null && showNumbers) {
                                Pill(
                                    if (change <= 0) "${-change}% fewer" else "$change% more",
                                    color = extended.tintMint,
                                    contentColor = extended.onTintMint,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(if (sticker) Color(0xFF111111) else RcTheme.extended.cta)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text(lookLabel("Scroll less with me"), style = MaterialTheme.typography.titleSmall, color = if (sticker) Color.White else RcTheme.extended.onCta)
                        Text("Reel Counter on Google Play", style = MaterialTheme.typography.bodySmall, color = if (sticker) Color.White else RcTheme.extended.onCta)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show my numbers", style = MaterialTheme.typography.titleSmall)
                        Text("Off shows only the change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = showNumbers, onCheckedChange = { showNumbers = it })
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RcButton("Save image", onClick = { share(save = true) }, style = RcButtonStyle.Secondary, icon = RcIcons.Save, modifier = Modifier.weight(1f))
                RcButton("Share", onClick = { share(save = false) }, icon = RcIcons.Share, loading = busy, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun writeShareImage(context: Context, bitmap: Bitmap): android.net.Uri {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "my-week.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.share", file)
}
