package com.reelcounter.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.time.TimeSource
import com.reelcounter.core.limits.WeekStreaks
import com.reelcounter.core.model.DaySummary
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.Sitting
import com.reelcounter.core.model.WatchTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class ProgressMode { DAY, WEEK }

data class WeekData(
    val start: LocalDate,
    val days: List<DaySummary>,
    val previousTotal: Int,
    /** Seven rows (Mon..Sun) of twelve two-hour blocks. */
    val heat: List<IntArray>,
) {
    val total: Int get() = days.sumOf { it.total }
    val perApp: Map<ReelApp, Int> get() = ReelApp.entries.associateWith { app -> days.sumOf { it.perApp[app] ?: 0 } }

    /** Change on last week in percent (negative = fewer reels), or null without a previous week. */
    val changePercent: Int? get() = if (previousTotal <= 0) null else ((total - previousTotal) * 100f / previousTotal).toInt()

    /** Peak two-hour block across the week, as the hour it starts, or null with no data. */
    val peakHour: Int? get() {
        val sums = IntArray(12)
        heat.forEach { row -> row.forEachIndexed { i, v -> sums[i] += v } }
        val max = sums.max()
        return if (max <= 0) null else sums.indexOfFirst { it == max } * 2
    }
}

data class DayData(
    val date: LocalDate,
    val summary: DaySummary,
    val hours: List<Int>,
    val sittings: List<Sitting>,
) {
    val minutes: Int get() = WatchTime.minutes(summary.total)
}

data class ProgressUiState(
    val loading: Boolean = true,
    val mode: ProgressMode = ProgressMode.WEEK,
    val today: LocalDate = LocalDate.now(),
    val limit: Int = 50,
    val firstDay: LocalDate = LocalDate.now(),
    val week: WeekData? = null,
    val day: DayData? = null,
    val isPro: Boolean = false,
) {
    val thisMonday: LocalDate get() = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val isThisWeek: Boolean get() = week?.start == thisMonday
    val canGoForwardWeek: Boolean get() = week != null && week.start.isBefore(thisMonday)
    val canGoForwardDay: Boolean get() = day != null && day.date.isBefore(today)

    /** Days of the shown week that are already over (or today), judged against the limit. */
    val daysUnder: Int get() = week?.days?.count { !it.date.isAfter(today) && !it.date.isBefore(firstDay) && it.total <= limit } ?: 0
    val daysCounted: Int get() = week?.days?.count { !it.date.isAfter(today) && !it.date.isBefore(firstDay) } ?: 0
    val weekCounts: Boolean get() = daysUnder >= WeekStreaks.GOOD_DAYS_PER_WEEK

    /** Good days so far (today included while it is still under) plus the days still to come. */
    val weekStillPossible: Boolean get() =
        daysUnder + (week?.days?.count { it.date.isAfter(today) } ?: 0) >= WeekStreaks.GOOD_DAYS_PER_WEEK
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val reels: ReelRepository,
    settings: SettingsRepository,
    entitlements: EntitlementRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val today = time.today()
    private val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private val initialDay: LocalDate? = savedState.get<Long>("epochDay")?.let(LocalDate::ofEpochDay)
    private val mode = MutableStateFlow(if (initialDay != null) ProgressMode.DAY else ProgressMode.WEEK)
    private val weekStart = MutableStateFlow(
        savedState.get<Long>("weekStartEpochDay")?.let(LocalDate::ofEpochDay) ?: thisMonday,
    )
    private val dayDate = MutableStateFlow(initialDay ?: today)

    private val week = weekStart.flatMapLatest { start ->
        combine(
            reels.daysBetween(start, start.plusDays(6)),
            reels.daysBetween(start.minusDays(7), start.minusDays(1)),
            reels.weekHeatmap(start),
        ) { days, previous, heat -> WeekData(start, days, previous.sumOf { it.total }, heat) }
    }

    private val day = dayDate.flatMapLatest { date ->
        combine(reels.daysBetween(date, date), reels.hoursOf(date), reels.sittings(date)) { summary, hours, sittings ->
            DayData(date, summary.first(), hours, sittings)
        }
    }

    val state: StateFlow<ProgressUiState> = combine(mode, week, day, settings.settings, entitlements.isPro) { m, w, d, s, pro ->
        ProgressUiState(
            loading = false,
            mode = m,
            today = today,
            limit = s.dailyLimit,
            firstDay = s.firstRunEpochDay?.let(LocalDate::ofEpochDay) ?: today,
            week = w,
            day = d,
            isPro = pro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun setMode(target: ProgressMode) {
        mode.value = target
    }

    /** Free users can look back one week; further back needs Pro. Returns false to open the paywall. */
    fun previousWeek(): Boolean {
        val target = weekStart.value.minusWeeks(1)
        if (!state.value.isPro && target.isBefore(thisMonday.minusWeeks(FREE_WEEKS_BACK))) return false
        weekStart.value = target
        return true
    }

    fun nextWeek() {
        if (weekStart.value.isBefore(thisMonday)) weekStart.value = weekStart.value.plusWeeks(1)
    }

    fun previousDay(): Boolean {
        val target = dayDate.value.minusDays(1)
        if (!state.value.isPro && target.isBefore(today.minusDays(FREE_DAYS_BACK))) return false
        dayDate.value = target
        return true
    }

    fun nextDay() {
        if (dayDate.value.isBefore(today)) dayDate.value = dayDate.value.plusDays(1)
    }

    fun openDay(date: LocalDate) {
        dayDate.value = date
        mode.value = ProgressMode.DAY
    }

    private companion object {
        const val FREE_WEEKS_BACK = 1L
        const val FREE_DAYS_BACK = 13L
    }
}
