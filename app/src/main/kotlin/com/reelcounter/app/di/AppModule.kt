package com.reelcounter.app.di

import com.reelcounter.app.BuildConfig
import com.reelcounter.app.widget.WidgetCountListener
import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.ReelCountListener
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun appConfig(): AppConfig = AppConfig(
        isDebug = BuildConfig.DEBUG,
        versionName = BuildConfig.VERSION_NAME,
        revenueCatApiKey = BuildConfig.REVENUECAT_API_KEY,
        supportEmail = BuildConfig.SUPPORT_EMAIL,
        legalBaseUrl = BuildConfig.LEGAL_BASE_URL,
        demoMode = BuildConfig.DEMO_MODE,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    @IntoSet
    abstract fun widgetListener(impl: WidgetCountListener): ReelCountListener
}
