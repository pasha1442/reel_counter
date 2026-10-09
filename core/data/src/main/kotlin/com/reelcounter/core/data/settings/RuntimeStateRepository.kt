package com.reelcounter.core.data.settings

import androidx.datastore.core.DataStore
import com.reelcounter.core.data.time.TimeSource
import com.reelcounter.core.model.CounterHealth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RuntimeStateRepository @Inject constructor(
    private val store: DataStore<RuntimeState>,
    private val time: TimeSource,
) {
    /** Today's nudges and unlocks; a stored day from before midnight reads as a fresh day. */
    val dayState: Flow<DayState> = combine(store.data, time.todayFlow) { state, today ->
        val epochDay = today.toEpochDay()
        if (state.day.epochDay == epochDay) state.day else DayState(epochDay = epochDay)
    }.distinctUntilChanged()

    val heartbeat: Flow<Heartbeat> = store.data.map { it.heartbeat }.distinctUntilChanged()

    suspend fun currentDayState(): DayState = dayState.first()

    suspend fun updateDay(transform: (DayState) -> DayState) {
        store.updateData { state ->
            val epochDay = time.today().toEpochDay()
            val current = if (state.day.epochDay == epochDay) state.day else DayState(epochDay = epochDay)
            state.copy(day = transform(current))
        }
    }

    suspend fun markServiceConnected() = store.updateData {
        it.copy(heartbeat = it.heartbeat.copy(lastConnectedAt = time.now(), lastAlerted = null))
    }

    suspend fun markServiceDisconnected() = store.updateData {
        it.copy(heartbeat = it.heartbeat.copy(lastDisconnectedAt = time.now()))
    }

    suspend fun setLastAlerted(health: CounterHealth?) = store.updateData {
        it.copy(heartbeat = it.heartbeat.copy(lastAlerted = health))
    }
}
