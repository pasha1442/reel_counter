package com.reelcounter.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.ReelCountListener
import com.reelcounter.core.data.account.AuthRepository
import com.reelcounter.core.data.account.DisabledAuthRepository
import com.reelcounter.core.data.account.FirebaseAuthRepository
import com.reelcounter.core.data.account.TestAuthRepository
import com.reelcounter.core.data.db.ReelDao
import com.reelcounter.core.data.db.ReelDatabase
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.repository.FriendsRepository
import com.reelcounter.core.data.repository.LocalFriendsRepository
import com.reelcounter.core.data.repository.LocalEntitlementRepository
import com.reelcounter.core.data.repository.RevenueCatEntitlementRepository
import com.reelcounter.core.data.telemetry.FirebaseTelemetry
import com.reelcounter.core.data.telemetry.NoTelemetry
import com.reelcounter.core.data.telemetry.Telemetry
import com.revenuecat.purchases.Purchases
import com.reelcounter.core.data.settings.AppSettings
import com.reelcounter.core.data.settings.JsonSerializer
import com.reelcounter.core.data.settings.RuntimeState
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ReelDatabase =
        Room.databaseBuilder(context, ReelDatabase::class.java, "reel_counter.db").build()

    @Provides
    fun reelDao(db: ReelDatabase): ReelDao = db.reelDao()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<AppSettings> = DataStoreFactory.create(
        serializer = JsonSerializer(AppSettings.serializer(), AppSettings()),
        corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.dataStoreFile("app_settings.json") },
    )

    /** Real payments once a RevenueCat key is set; until then the local stand-in. */
    @Provides
    @Singleton
    fun entitlements(
        config: AppConfig,
        revenueCat: Provider<RevenueCatEntitlementRepository>,
        local: Provider<LocalEntitlementRepository>,
    ): EntitlementRepository = if (config.billingConfigured && Purchases.isConfigured) revenueCat.get() else local.get()

    /** Real accounts with Firebase; a labelled test mode in debug builds without it; else off. */
    @Provides
    @Singleton
    fun auth(
        @ApplicationContext context: Context,
        config: AppConfig,
        firebase: Provider<FirebaseAuthRepository>,
        test: Provider<TestAuthRepository>,
        disabled: Provider<DisabledAuthRepository>,
    ): AuthRepository = when {
        FirebaseTelemetry.isAvailable(context) -> firebase.get()
        config.isDebug -> test.get()
        else -> disabled.get()
    }

    @Provides
    @Singleton
    fun telemetry(@ApplicationContext context: Context, firebase: Provider<FirebaseTelemetry>): Telemetry =
        if (FirebaseTelemetry.isAvailable(context)) firebase.get() else NoTelemetry

    @Provides
    @Singleton
    fun runtimeStore(@ApplicationContext context: Context): DataStore<RuntimeState> = DataStoreFactory.create(
        serializer = JsonSerializer(RuntimeState.serializer(), RuntimeState()),
        corruptionHandler = ReplaceFileCorruptionHandler { RuntimeState() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.dataStoreFile("runtime_state.json") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds
    abstract fun friends(impl: LocalFriendsRepository): FriendsRepository

    /** Lets the service inject the set even before any listener is bound. */
    @Multibinds
    abstract fun countListeners(): Set<ReelCountListener>
}
