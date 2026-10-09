package com.reelcounter.core.data.repository

import com.reelcounter.core.data.db.ReelDao
import com.reelcounter.core.data.db.ReelEventEntity
import com.reelcounter.core.data.time.TimeSource
import com.reelcounter.core.model.DaySummary
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.ReelEvent
import com.reelcounter.core.model.Sitting
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class ReelRepository @Inject constructor(
    private val dao: ReelDao,
    private val time: TimeSource,
) {
    suspend fun record(event: ReelEvent) {
        dao.record(
            ReelEventEntity(
                app = event.app.name,
                timestamp = event.timeMillis,
                epochDay = time.dayOf(event.timeMillis).toEpochDay(),
                sessionId = event.surfaceSessionId,
                estimated = event.estimated,
            ),
        )
    }

    /** Today's reels per app; rolls over at midnight. */
    val todayPerApp: Flow<Map<ReelApp, Int>> = time.todayFlow.flatMapLatest { today ->
        dao.observeDailyTotals(today.toEpochDay(), today.toEpochDay()).map { rows ->
            rows.mapNotNull { row -> ReelApp.fromName(row.app)?.let { it to row.count } }.toMap()
        }
    }.distinctUntilChanged()

    val todayTotal: Flow<Int> = todayPerApp.map { it.values.sum() }.distinctUntilChanged()

    /** The last [days] days including today, oldest first, with empty days filled in. */
    fun recentDays(days: Int): Flow<List<DaySummary>> = time.todayFlow.flatMapLatest { today ->
        val from = today.minusDays(days - 1L)
        dao.observeDailyTotals(from.toEpochDay(), today.toEpochDay()).map { rows ->
            val byDay = rows.groupBy { it.epochDay }
            (0 until days).map { offset ->
                val date = from.plusDays(offset.toLong())
                val perApp = byDay[date.toEpochDay()].orEmpty()
                    .mapNotNull { row -> ReelApp.fromName(row.app)?.let { it to row.count } }
                    .toMap()
                DaySummary(date, perApp)
            }
        }
    }

    /** Totals of the [days] days before today, oldest first. Feeds the brain's overnight healing. */
    fun previousDayTotals(days: Int): Flow<List<Int>> = recentDays(days + 1).map { summaries ->
        summaries.dropLast(1).map { it.total }
    }.distinctUntilChanged()

    /** Reels per hour of today, 24 buckets. */
    val todayByHour: Flow<List<Int>> = time.todayFlow.flatMapLatest { today ->
        val start = time.startOfDay(today)
        val end = time.startOfDay(today.plusDays(1))
        dao.observeEventTimes(start, end).map { times ->
            val buckets = IntArray(24)
            val zone = time.clock().zone
            times.forEach { t ->
                val hour = java.time.Instant.ofEpochMilli(t).atZone(zone).hour
                buckets[hour]++
            }
            buckets.toList()
        }
    }

    /** Every day from [from] to [to] inclusive, with empty days filled in. */
    fun daysBetween(from: LocalDate, to: LocalDate): Flow<List<DaySummary>> =
        dao.observeDailyTotals(from.toEpochDay(), to.toEpochDay()).map { rows ->
            val byDay = rows.groupBy { it.epochDay }
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { date ->
                val perApp = byDay[date.toEpochDay()].orEmpty()
                    .mapNotNull { row -> ReelApp.fromName(row.app)?.let { it to row.count } }
                    .toMap()
                DaySummary(date, perApp)
            }.toList()
        }

    /** Individual reels from [from] to [to] inclusive (kept for 90 days). */
    fun eventsBetween(from: LocalDate, to: LocalDate): Flow<List<ReelEvent>> =
        dao.observeEvents(time.startOfDay(from), time.startOfDay(to.plusDays(1))).map { rows ->
            rows.mapNotNull { row ->
                ReelApp.fromName(row.app)?.let { ReelEvent(it, row.timestamp, row.sessionId, row.estimated) }
            }
        }

    /**
     * Reels per two-hour block for each day from [from], seven rows of twelve, oldest day first:
     * the "when you scroll" heat map.
     */
    fun weekHeatmap(from: LocalDate): Flow<List<IntArray>> = eventsBetween(from, from.plusDays(6)).map { events ->
        val rows = List(7) { IntArray(12) }
        val zone = time.clock().zone
        events.forEach { e ->
            val at = java.time.Instant.ofEpochMilli(e.timeMillis).atZone(zone)
            val day = (at.toLocalDate().toEpochDay() - from.toEpochDay()).toInt()
            if (day in 0..6) rows[day][at.hour / 2]++
        }
        rows
    }

    /** One day's reels grouped into sittings: same app, less than [gapMillis] between reels. Newest first. */
    fun sittings(date: LocalDate, gapMillis: Long = 10 * 60_000L): Flow<List<Sitting>> =
        eventsBetween(date, date).map { events -> groupSittings(events, gapMillis) }

    /** Reels per hour of [date], 24 buckets. */
    fun hoursOf(date: LocalDate): Flow<List<Int>> = eventsBetween(date, date).map { events ->
        val buckets = IntArray(24)
        val zone = time.clock().zone
        events.forEach { buckets[java.time.Instant.ofEpochMilli(it.timeMillis).atZone(zone).hour]++ }
        buckets.toList()
    }

    suspend fun firstDay(): LocalDate? = dao.firstDay()?.let(LocalDate::ofEpochDay)

    /** Keep individual events for [retainDays]; daily totals are kept forever. */
    suspend fun pruneOldEvents(retainDays: Int = 90): Int =
        dao.pruneEventsBefore(time.today().minusDays(retainDays.toLong()).toEpochDay())

    suspend fun deleteAll() = dao.clearAll()
}

internal fun groupSittings(events: List<ReelEvent>, gapMillis: Long): List<Sitting> {
    val sittings = mutableListOf<Sitting>()
    for (e in events.sortedBy { it.timeMillis }) {
        val last = sittings.lastOrNull()
        if (last != null && last.app == e.app && e.timeMillis - last.endMillis <= gapMillis) {
            sittings[sittings.lastIndex] = last.copy(endMillis = e.timeMillis, reels = last.reels + 1)
        } else {
            sittings += Sitting(e.app, e.timeMillis, e.timeMillis, 1)
        }
    }
    return sittings.asReversed()
}
