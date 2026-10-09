package com.reelcounter.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ReelDao {

    @Insert
    protected abstract suspend fun insertEvent(event: ReelEventEntity): Long

    // Two statements instead of UPSERT: Android 9 ships SQLite 3.22, which predates ON CONFLICT DO UPDATE.
    @Query("INSERT OR IGNORE INTO daily_totals(epochDay, app, count) VALUES(:epochDay, :app, 0)")
    protected abstract suspend fun ensureDailyRow(epochDay: Long, app: String)

    @Query("UPDATE daily_totals SET count = count + 1 WHERE epochDay = :epochDay AND app = :app")
    protected abstract suspend fun incrementDaily(epochDay: Long, app: String)

    @Transaction
    open suspend fun record(event: ReelEventEntity) {
        insertEvent(event)
        ensureDailyRow(event.epochDay, event.app)
        incrementDaily(event.epochDay, event.app)
    }

    @Query("SELECT * FROM daily_totals WHERE epochDay BETWEEN :fromDay AND :toDay")
    abstract fun observeDailyTotals(fromDay: Long, toDay: Long): Flow<List<DailyTotalEntity>>

    @Query("SELECT timestamp FROM reel_events WHERE timestamp >= :startMillis AND timestamp < :endMillis ORDER BY timestamp")
    abstract fun observeEventTimes(startMillis: Long, endMillis: Long): Flow<List<Long>>

    @Query("SELECT * FROM reel_events WHERE timestamp >= :startMillis AND timestamp < :endMillis ORDER BY timestamp")
    abstract fun observeEvents(startMillis: Long, endMillis: Long): Flow<List<ReelEventEntity>>

    @Query("SELECT MIN(epochDay) FROM daily_totals")
    abstract suspend fun firstDay(): Long?

    @Query("DELETE FROM reel_events WHERE epochDay < :beforeDay")
    abstract suspend fun pruneEventsBefore(beforeDay: Long): Int

    @Query("DELETE FROM reel_events")
    protected abstract suspend fun clearEvents()

    @Query("DELETE FROM daily_totals")
    protected abstract suspend fun clearDailyTotals()

    @Transaction
    open suspend fun clearAll() {
        clearEvents()
        clearDailyTotals()
    }
}
