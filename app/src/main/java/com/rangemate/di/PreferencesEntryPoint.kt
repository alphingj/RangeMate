package com.rangemate.di

import com.rangemate.data.preferences.PreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Provides singleton access without field injection (see MainActivity). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun preferencesManager(): PreferencesManager
}
