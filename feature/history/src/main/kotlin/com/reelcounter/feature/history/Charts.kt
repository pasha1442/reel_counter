package com.reelcounter.feature.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.DaySummary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

/**
 * Seven columns with a dashed limit line. Under the limit = the look's "under" colour, over = the
 * "over" colour, and over-limit days also print their number, so colour never carries it alone.
 * Tap a column to open that day.
 */
@Composable
internal fun WeekBars(
    days: List<DaySummary>,
    limit: Int,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    val sticker = RcTheme.tokens.isSticker
    val reducedMotion = LocalReducedMotion.current
    val grow = remember(days.firstOrNull()?.date) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(days.firstOrNull()?.date) { grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }

    val chartHeight = 150.dp
    val maxValue = max(limit * 1.35f, (days.maxOfOrNull { it.total } ?: 0).toFloat()).coerceAtLeast(1f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(chartHeight + 20.dp)) {
            // Limit line.
            val limitFraction = limit / maxValue
            Canvas(Modifier.fillMaxWidth().height(chartHeight + 20.dp)) {
                val y = size.height - (size.height - 20.dp.toPx()) * limitFraction
                drawLine(
                    color = scheme.onSurfaceVariant.copy(alpha = if (sticker) 0.7f else 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            Row(
                Modifier.fillMaxWidth().height(chartHeight + 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val over = day.total > limit
                    val future = day.date.isAfter(today)
                    val fraction = (day.total / maxValue) * grow.value
                    val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = !future, role = Role.Button) { onDay(day.date) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$dayName, ${day.total} reels${if (over) ", over the limit" else ""}"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (over) {
                            Text("${day.total}", style = MaterialTheme.typography.labelSmall, color = if (sticker) scheme.onSurface else extended.chartOver, maxLines = 1)
                        }
                        val barHeight = chartHeight * fraction
                        if (day.total > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(max(barHeight.value, 4f).dp)
                                    .lookSurface(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp), if (over) extended.chartOver else extended.chartUnder, thin = true),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(extended.track),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { day ->
                Text(
                    if (day.date == today) "Today" else day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.date == today) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 24 hourly columns; hours after the limit was passed use the "over" colour. */
@Composable
internal fun HourBars(hours: List<Int>, limit: Int, modifier: Modifier = Modifier) {
    val extended = RcTheme.extended
    val maxValue = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
    var running = 0
    val overFlags = hours.map { count ->
        running += count
        count > 0 && running > limit
    }
    val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics {
                    contentDescription = if (peak == null) "No reels yet" else "Busiest hour: ${hourLabel(peak)}, ${hours[peak]} reels"
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEachIndexed { i, count ->
                val h = if (count == 0) 3.dp else (96.dp * (count / maxValue.toFloat())).coerceAtLeast(6.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .height(h)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            when {
                                count == 0 -> extended.track
                                overFlags[i] -> extended.chartOver
                                else -> extended.chartUnder
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            listOf("12 am", "6 am", "12 pm", "6 pm").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Seven rows (Mon..Sun) of twelve two-hour blocks, darker where more reels were watched. */
@Composable
internal fun HeatGrid(rows: List<IntArray>, modifier: Modifier = Modifier) {
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    // Heat is intensity, not "good" or "bad": the accent in Midnight, ink in Sticker.
    val base = if (sticker) Color(0xFF111111) else extended.cta
    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    val max = rows.maxOfOrNull { it.maxOrNull() ?: 0 }?.coerceAtLeast(1) ?: 1
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = heatDescription(rows) }) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLetters[r], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { v ->
                        val alpha = if (v == 0) 0f else 0.18f + 0.82f * (v / max.toFloat())
                        Box(
                            Modifier
                                .weight(1f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (v == 0) extended.track else base.copy(alpha = alpha)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("12 am", "6 am", "12 pm", "6 pm").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

internal fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 am"
    hour < 12 -> "$hour am"
    hour == 12 -> "12 pm"
    else -> "${hour - 12} pm"
}

private fun heatDescription(rows: List<IntArray>): String {
    val sums = IntArray(12)
    rows.forEach { row -> row.forEachIndexed { i, v -> sums[i] += v } }
    val max = sums.maxOrNull() ?: 0
    if (max == 0) return "Heat map: no reels this week"
    val block = sums.indexOfFirst { it == max }
    return "Heat map of reels by day and time. Busiest time: ${hourLabel(block * 2)} to ${hourLabel((block * 2 + 2) % 24)}."
}

/** "- - - limit 20": the key for the dashed line, shown above the chart so it never sits on a bar. */
@Composable
internal fun LimitKey(limit: Int, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(18.dp).height(2.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("limit $limit", style = MaterialTheme.typography.bodySmall, color = color)
    }
}
