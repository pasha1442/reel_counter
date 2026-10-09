package com.reelcounter.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One counted reel. Kept for 90 days, then only [DailyTotalEntity] remains. */
@Entity(
    tableName = "reel_events",
    indices = [Index("epochDay"), Index("timestamp")],
)
data class ReelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.reelcounter.core.model.ReelApp] name. */
    val app: String,
    val timestamp: Long,
    /** Local calendar day of [timestamp]. */
    val epochDay: Long,
    val sessionId: Long,
    val estimated: Boolean,
)

/** Reels per app per day, kept forever so history survives event pruning. */
@Entity(
    tableName = "daily_totals",
    primaryKeys = ["epochDay", "app"],
)
data class DailyTotalEntity(
    val epochDay: Long,
    val app: String,
    val count: Int,
)
