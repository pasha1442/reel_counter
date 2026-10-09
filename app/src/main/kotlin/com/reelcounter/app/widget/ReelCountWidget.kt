package com.reelcounter.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.reelcounter.app.MainActivity
import com.reelcounter.core.data.ReelCountListener
import com.reelcounter.core.data.repository.BrainRepository
import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.design.brain.nameRes
import com.reelcounter.core.model.AppLook
import com.reelcounter.app.R
import com.reelcounter.core.model.BrainState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun reels(): ReelRepository
    fun settings(): SettingsRepository
    fun brain(): BrainRepository
}

/** Home-screen widget: today's count, the limit bar and the brain's mood. Tap to open the app. */
class ReelCountWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val total = entry.reels().todayTotal.first()
        val settings = entry.settings().settings.first()
        val brain = entry.brain().brain.first()
        val stageName = context.getString(brain.stage.nameRes())
        provideContent { WidgetContent(total, settings.dailyLimit, stageName, settings.look) }
    }
}

/** Colours per look: Midnight is dark with a lime accent, Sticker is a yellow card with ink text. */
private class WidgetColors(val background: Color, val text: Color, val muted: Color, val bar: Color, val over: Color, val track: Color)

private fun colorsFor(look: AppLook) = when (look) {
    AppLook.MIDNIGHT -> WidgetColors(Color(0xFF1B1A18), Color(0xFFEDE8E1), Color(0xFFA39D94), Color(0xFF8FBF9F), Color(0xFFE0876A), Color(0xFF2C2A27))
    AppLook.STICKER -> WidgetColors(Color(0xFFFFD43B), Color(0xFF111111), Color(0xFF3D3A33), Color(0xFF111111), Color(0xFFB33A2C), Color(0xFFFFFFFF))
}

@Composable
private fun WidgetContent(total: Int, limit: Int, stageName: String, look: AppLook) {
    val c = colorsFor(look)
    val over = total > limit
    // Stacked, because a 2x2 widget is close to square: brain and stage on top, then the count.
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(c.background)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(if (look == AppLook.STICKER) R.drawable.widget_brain_sticker else R.drawable.widget_brain_soft),
                contentDescription = "Your brain: $stageName",
                modifier = GlanceModifier.size(56.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    if (look == AppLook.STICKER) stageName.uppercase() else stageName,
                    style = TextStyle(color = ColorProvider(c.muted), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    if (over) "${total - limit} over" else "${limit - total} left",
                    style = TextStyle(color = ColorProvider(if (over) c.over else c.muted), fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$total", style = TextStyle(color = ColorProvider(c.text), fontSize = 34.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(
                " / $limit",
                style = TextStyle(color = ColorProvider(c.muted), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (limit > 0) (total.toFloat() / limit).coerceIn(0f, 1f) else 1f,
            color = ColorProvider(if (over) c.over else c.bar),
            backgroundColor = ColorProvider(c.track),
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
        )
    }
}

class ReelCountWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReelCountWidget()
}

/** Pushes new counts to every placed widget (the counter throttles how often this runs). */
class WidgetCountListener @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReelCountListener {
    override suspend fun onCountChanged(todayTotal: Int, dailyLimit: Int, brain: BrainState) {
        ReelCountWidget().updateAll(context)
    }
}
