package com.reelcounter.feature.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.design.components.AnimatedCounter
import com.reelcounter.core.design.components.AppIcon
import com.reelcounter.core.design.components.EmptyState
import com.reelcounter.core.design.components.ListDivider
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIconButton
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.RcTopBar
import com.reelcounter.core.design.components.ScreenHeader
import com.reelcounter.core.design.components.SectionHeader
import com.reelcounter.core.design.components.SegmentedToggle
import com.reelcounter.core.design.components.StatBlock
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.WatchTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")
private val LongDay = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val Clock = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun ProgressRoute(
    contentPadding: PaddingValues,
    onOpenPaywall: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onShareWeek: (LocalDate) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = RcSpacing.gutter),
    ) {
        ScreenHeader("Progress") {
            SegmentedToggle(
                options = listOf("Day", "Week"),
                selected = if (state.mode == ProgressMode.DAY) 0 else 1,
                onSelect = { viewModel.setMode(if (it == 0) ProgressMode.DAY else ProgressMode.WEEK) },
            )
        }
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "progressMode",
        ) { mode ->
            when (mode) {
                ProgressMode.WEEK -> WeekContent(
                    state = state,
                    onPrevious = { if (!viewModel.previousWeek()) onOpenPaywall() },
                    onNext = viewModel::nextWeek,
                    onDay = onOpenDay,
                    onShare = { state.week?.let { onShareWeek(it.start) } },
                )
                ProgressMode.DAY -> DayContent(
                    state = state,
                    onPrevious = { if (!viewModel.previousDay()) onOpenPaywall() },
                    onNext = viewModel::nextDay,
                )
            }
        }
        Spacer(Modifier.height(RcSpacing.section))
    }
}

/** Day detail opened from a bar or a notification: the Day view on its own screen. */
@Composable
fun DayDetailRoute(onBack: () -> Unit, onOpenPaywall: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        RcTopBar(title = "Day detail", onBack = onBack)
        Column(Modifier.padding(horizontal = RcSpacing.gutter)) {
            DayContent(state, onPrevious = { if (!viewModel.previousDay()) onOpenPaywall() }, onNext = viewModel::nextDay)
            Spacer(Modifier.height(RcSpacing.section))
        }
    }
}

/** "‹  28 Sep – 4 Oct  ›" as a plain row: the arrows are the only controls. */
@Composable
private fun PeriodNavigator(
    title: String,
    subtitle: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RcIconButton(RcIcons.ChevronLeft, "Previous", onPrevious)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RcIconButton(
            RcIcons.ChevronRight,
            if (canGoForward) "Next" else "Next (not yet)",
            onClick = { if (canGoForward) onNext() },
            modifier = Modifier.alpha(if (canGoForward) 1f else 0.3f),
        )
    }
}

@Composable
private fun WeekContent(
    state: ProgressUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onShare: () -> Unit,
) {
    val week = state.week ?: return
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    Column {
        val end = week.start.plusDays(6)
        PeriodNavigator(
            title = "${week.start.format(DayMonth)} – ${end.format(DayMonth)}",
            subtitle = when {
                state.isThisWeek -> "This week"
                week.start == state.thisMonday.minusWeeks(1) -> "Last week"
                else -> "Limit ${state.limit} a day"
            },
            canGoForward = state.canGoForwardWeek,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(week.total, MaterialTheme.typography.displayMedium)
                Text(
                    " reels",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            RcIconButton(RcIcons.Share, "Share my week", onShare, modifier = Modifier.padding(bottom = 6.dp))
        }
        val change = week.changePercent
        Text(
            when {
                change == null -> if (state.isThisWeek) "So far this week · limit ${state.limit} a day" else "Limit ${state.limit} a day"
                change <= 0 -> "${-change}% fewer than the week before"
                else -> "$change% more than the week before"
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (change != null) FontWeight.Medium else FontWeight.Normal),
            color = when {
                change == null -> scheme.onSurfaceVariant
                change <= 0 -> extended.successText
                else -> extended.warningText
            },
        )
        Spacer(Modifier.height(20.dp))
        RcCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reels per day", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                LimitKey(state.limit)
            }
            Spacer(Modifier.height(16.dp))
            WeekBars(week.days, state.limit, state.today, onDay)
        }
        Spacer(Modifier.height(RcSpacing.item))
        val counted = week.days.filter { !it.date.isAfter(state.today) && !it.date.isBefore(state.firstDay) }
        val average = if (counted.isEmpty()) 0 else counted.sumOf { it.total } / counted.size
        val best = counted.filter { it.date.isBefore(state.today) }.minByOrNull { it.total }
        RcCard(Modifier.fillMaxWidth()) {
            Row {
                StatBlock("$average", "a day on average", Modifier.weight(1f))
                StatBlock(
                    "${state.daysUnder} of ${state.daysCounted}",
                    "days under ${state.limit}",
                    Modifier.weight(1f),
                    valueColor = if (state.daysCounted > 0 && state.daysUnder == state.daysCounted) extended.successText else scheme.onSurface,
                )
                StatBlock(
                    best?.let { "${it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.total}" } ?: "–",
                    "best day",
                    Modifier.weight(1f),
                )
            }
        }

        val peak = week.peakHour
        SectionHeader(
            "When you scroll",
            trailing = if (peak != null) {
                { Text("Peak ${hourLabel(peak)}–${hourLabel((peak + 2) % 24)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            } else {
                null
            },
        )
        RcCard(Modifier.fillMaxWidth()) {
            HeatGrid(week.heat)
            Spacer(Modifier.height(14.dp))
            Text(
                if (peak != null) {
                    "Most of your scrolling happens between ${hourLabel(peak)} and ${hourLabel((peak + 2) % 24)}."
                } else {
                    "Your busiest times will show here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        SectionHeader("By app")
        AppBreakdown(week.perApp)
    }
}

@Composable
private fun DayContent(state: ProgressUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val day = state.day ?: return
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    Column {
        PeriodNavigator(
            title = when (day.date) {
                state.today -> "Today"
                state.today.minusDays(1) -> "Yesterday"
                else -> day.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            },
            subtitle = day.date.format(LongDay),
            canGoForward = state.canGoForwardDay,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        Spacer(Modifier.height(24.dp))
        val total = day.summary.total
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedCounter(total, MaterialTheme.typography.displayMedium)
            Text(
                " reels · ≈ ${day.minutes} min",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val over = total - state.limit
        Text(
            when {
                over > 0 -> "$over over your limit of ${state.limit}"
                day.date == state.today -> "${-over} left of ${state.limit}"
                else -> "Under your limit of ${state.limit}"
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (over > 0) extended.warningText else extended.successText,
        )
        Spacer(Modifier.height(20.dp))
        RcCard(Modifier.fillMaxWidth()) {
            val peak = day.hours.indices.maxByOrNull { day.hours[it] }?.takeIf { day.hours[it] > 0 }
            Row {
                Text("By hour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (peak != null) Text("Peak ${hourLabel(peak)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            HourBars(day.hours, state.limit)
            if (over > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${if (RcTheme.tokens.isSticker) "Red" else "Orange"} bars: reels after you passed ${state.limit}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        SectionHeader("Sittings")
        if (day.sittings.isEmpty()) {
            RcCard(Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = RcIcons.Sparkle,
                    title = if (day.date.isBefore(state.firstDay)) "Before counting started" else "No reels",
                    body = if (day.date.isBefore(state.firstDay)) "Reel Counter wasn't set up yet on this day." else "A clean day. Your brain says thanks.",
                )
            }
        } else {
            RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                val zone = ZoneId.systemDefault()
                day.sittings.forEachIndexed { i, sitting ->
                    if (i > 0) ListDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RcSpacing.card, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(sitting.app, size = 28.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sitting.app.displayName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text(
                                java.time.Instant.ofEpochMilli(sitting.startMillis).atZone(zone).format(Clock).lowercase() +
                                    " · ≈ ${WatchTime.minutes(sitting.reels)} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text("${sitting.reels}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        SectionHeader("By app")
        AppBreakdown(day.summary.perApp)
    }
}

/** Each app as a row with its share of the total as a thin bar. One accent colour for both bars. */
@Composable
private fun AppBreakdown(perApp: Map<ReelApp, Int>) {
    val total = perApp.values.sum()
    val scheme = MaterialTheme.colorScheme
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        ReelApp.entries.forEachIndexed { i, app ->
            if (i > 0) ListDivider()
            val count = perApp[app] ?: 0
            val share = if (total == 0) 0 else (count * 100f / total).roundToInt()
            Column(Modifier.padding(horizontal = RcSpacing.card, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, size = 32.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.displayName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("≈ ${WatchTime.minutes(count)} min · ${app.surfaceName}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$count", style = MaterialTheme.typography.titleMedium)
                        Text("$share%", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val barHeight = if (sticker) 10.dp else 6.dp
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .track(if (sticker) Color.White else extended.track),
                ) {
                    if (share > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(share / 100f)
                                .height(barHeight)
                                .track(extended.cta),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.track(color: Color): Modifier =
    lookSurface(RoundedCornerShape(6.dp), color, outlined = RcTheme.tokens.isSticker, thin = true)
