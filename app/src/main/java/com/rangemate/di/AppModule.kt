package com.rangemate.di

import android.content.Context
import com.rangemate.data.ble.BleManager
import com.rangemate.data.location.SpeedProvider
import com.rangemate.data.preferences.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBleManager(
        @ApplicationContext context: Context
    ): BleManager = BleManager(context)

    @Provides
    @Singleton
    fun provideSpeedProvider(
        @ApplicationContext context: Context
    ): SpeedProvider = SpeedProvider(context)

    @Provides
    @Singleton
    fun providePreferencesManager(): PreferencesManager = PreferencesManager()
}