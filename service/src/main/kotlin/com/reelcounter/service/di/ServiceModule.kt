package com.reelcounter.service.di

import com.reelcounter.core.data.CounterHealthMonitor
import com.reelcounter.service.health.AccessibilityHealthMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    abstract fun healthMonitor(impl: AccessibilityHealthMonitor): CounterHealthMonitor
}
