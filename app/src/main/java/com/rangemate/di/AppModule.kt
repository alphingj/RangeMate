package com.rangemate.di

import android.content.Context
import com.rangemate.data.ble.BleManager
import com.rangemate.data.ble.BmsScanFilter
import com.rangemate.data.location.SpeedProvider
import com.rangemate.data.log.RawPacketLogger
import com.rangemate.data.log.TelegramLogUploader
import com.rangemate.data.preferences.PreferencesManager
import com.rangemate.data.protocol.ProtocolAutoDetector
import com.rangemate.data.protocol.ProtocolRegistry
import com.rangemate.data.protocol.komaki.KomakiExtendedParser
import com.rangemate.data.range.AdaptiveRangeEngine
import com.rangemate.data.range.RangeViewModel
import com.rangemate.data.range.RideTracker
import com.rangemate.data.repository.BmsRepository
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
    fun provideRawPacketLogger(
        @ApplicationContext context: Context
    ): RawPacketLogger = RawPacketLogger(context)

    @Provides
    @Singleton
    fun provideBmsScanFilter(
        logger: RawPacketLogger
    ): BmsScanFilter = BmsScanFilter(logger)

    @Provides
    @Singleton
    fun provideTelegramLogUploader(): TelegramLogUploader = TelegramLogUploader()

    @Provides
    @Singleton
    fun provideProtocolRegistry(): ProtocolRegistry = ProtocolRegistry()



    @Provides
    @Singleton
    fun provideKomakiExtendedParser(
        logger: RawPacketLogger
    ): KomakiExtendedParser = KomakiExtendedParser(logger)

    @Provides
    @Singleton
    fun provideBleManager(
        @ApplicationContext context: Context,
        protocolRegistry: ProtocolRegistry,
        protocolAutoDetector: ProtocolAutoDetector,
        logger: RawPacketLogger,
        scanFilter: BmsScanFilter,
        preferences: PreferencesManager
    ): BleManager = BleManager(context, protocolRegistry, protocolAutoDetector, logger, scanFilter, preferences)

    @Provides
    @Singleton
    fun provideProtocolAutoDetector(
        registry: ProtocolRegistry,
        logger: RawPacketLogger
    ): ProtocolAutoDetector = ProtocolAutoDetector(registry, logger)

    @Provides
    @Singleton
    fun provideSpeedProvider(
        @ApplicationContext context: Context
    ): SpeedProvider = SpeedProvider(context)

    @Provides
    @Singleton
    fun providePreferencesManager(): PreferencesManager = PreferencesManager()

    @Provides
    @Singleton
    fun provideAdaptiveRangeEngine(): AdaptiveRangeEngine = AdaptiveRangeEngine()

    @Provides
    @Singleton
    fun provideRideTracker(
        bmsRepository: BmsRepository,
        adaptiveRangeEngine: AdaptiveRangeEngine
    ): RideTracker = RideTracker(bmsRepository, adaptiveRangeEngine)

    @Provides
    @Singleton
    fun provideRangeViewModel(
        adaptiveRangeEngine: AdaptiveRangeEngine,
        rideTracker: RideTracker,
        bmsRepository: BmsRepository
    ): RangeViewModel = RangeViewModel(adaptiveRangeEngine, rideTracker, bmsRepository)
}