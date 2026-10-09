package com.reelcounter.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.LimitSettings
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.Strictness
import com.reelcounter.core.model.ThemeMode
import com.reelcounter.core.model.UnlockState
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** User choices. Stored as typed JSON in DataStore (crash-safe, no schema compiler needed). */
@Serializable
data class AppSettings(
    val onboardingComplete: Boolean = false,
    /** The user tapped "Agree" on the prominent disclosure. */
    val disclosureAccepted: Boolean = false,
    val dailyLimit: Int = LimitSettings.DEFAULT_DAILY_LIMIT,
    /** ReelApp name -> limit. Pro only. */
    val perAppLimits: Map<String, Int> = emptyMap(),
    val blockAtLimit: Boolean = true,
    val strictness: Strictness = Strictness.FIRM,
    val nudgesEnabled: Boolean = true,
    val liveNotification: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Which of the two looks the user picked. */
    val look: AppLook = AppLook.MIDNIGHT,
    /** Onboarding's "how many reels a day?" answer, in reels; null when skipped or "no idea". */
    val dailyGuess: Int? = null,
    /** Debug builds only: pretend to be Pro until billing is connected. */
    val debugPro: Boolean = false,
    /** Debug builds only: save detection sessions (hashed text only) for replay tests. */
    val debugRecordDetection: Boolean = false,
    /** Debug builds only: show a sample battle on the Friends tab until accounts exist. */
    val debugDemoFriends: Boolean = false,
    /** First day the counter ran, for streaks. */
    val firstRunEpochDay: Long? = null,
    /** Opt-in: send crash reports (Firebase Crashlytics). Off until the user says yes. */
    val shareCrashReports: Boolean = false,
    /** Opt-in: send anonymous usage events (Firebase Analytics). Off until the user says yes. */
    val shareUsageStats: Boolean = false,
    /** The user answered the one-time "help fix bugs?" question, either way. */
    val privacyChoiceMade: Boolean = false,
    /** Debug builds without Firebase only: a pretend signed-in account, to try the account screens. */
    val testAccount: TestAccount? = null,
    /**
     * Account IDs that finished setup on this phone (a name and the 18+ check). The date of birth
     * itself is never stored; only that the check passed.
     */
    val ageConfirmedAccounts: Set<String> = emptySet(),
) {
    fun toLimitSettings(): LimitSettings = LimitSettings(
        dailyLimit = dailyLimit,
        perAppLimits = perAppLimits.mapNotNull { (name, limit) -> ReelApp.fromName(name)?.let { it to limit } }.toMap(),
        blockAtLimit = blockAtLimit,
        strictness = strictness,
        nudgesEnabled = nudgesEnabled,
    )
}

/** A pretend account for debug builds that have no Firebase project yet. Never used in release. */
@Serializable
data class TestAccount(
    val id: String,
    val name: String?,
    val email: String,
    val viaGoogle: Boolean,
)

/** Per-day state that resets at midnight. */
@Serializable
data class DayState(
    val epochDay: Long = 0,
    /** Threshold names already notified today. */
    val nudgesSent: Set<String> = emptySet(),
    val unlocksUsed: Int = 0,
    val allowedUntilCount: Int? = null,
) {
    val unlock: UnlockState get() = UnlockState(unlocksUsed, allowedUntilCount)
}

/** Service lifecycle stamps, used by the health check. */
@Serializable
data class Heartbeat(
    val lastConnectedAt: Long? = null,
    val lastDisconnectedAt: Long? = null,
    /** The last unhealthy state we alerted about, so each incident alerts once. */
    val lastAlerted: CounterHealth? = null,
)

@Serializable
data class RuntimeState(
    val day: DayState = DayState(),
    val heartbeat: Heartbeat = Heartbeat(),
)

internal val StoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        StoreJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(StoreJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
