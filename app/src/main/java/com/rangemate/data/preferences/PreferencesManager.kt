package com.rangemate.data.preferences

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferences(
    val maxPower: Float = 2000f,
    val fullRangeKm: Float = 70f,
    val distanceUnit: String = "km",
    val greenZoneEnd: Float = 1000f,
    val yellowZoneEnd: Float = 1500f,
    val showPowerGraph: Boolean = false,
    val warningTempC: Float = 45f,
    val criticalTempC: Float = 55f
)

@Singleton
class PreferencesManager @Inject constructor() {
    private val _userPreferences = MutableStateFlow(UserPreferences())
    val userPreferences: Flow<UserPreferences> = _userPreferences.asStateFlow()

    suspend fun updateMaxPower(maxPower: Float) {
        _userPreferences.value = _userPreferences.value.copy(maxPower = maxPower)
    }

    suspend fun updateFullRange(rangeKm: Float) {
        _userPreferences.value = _userPreferences.value.copy(fullRangeKm = rangeKm)
    }

    suspend fun updateDistanceUnit(unit: String) {
        _userPreferences.value = _userPreferences.value.copy(distanceUnit = unit)
    }

    suspend fun updatePowerZones(greenEnd: Float, yellowEnd: Float) {
        _userPreferences.value = _userPreferences.value.copy(
            greenZoneEnd = greenEnd,
            yellowZoneEnd = yellowEnd
        )
    }

    suspend fun updateShowPowerGraph(show: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(showPowerGraph = show)
    }

    suspend fun updateTempThresholds(warning: Float, critical: Float) {
        _userPreferences.value = _userPreferences.value.copy(
            warningTempC = warning,
            criticalTempC = critical
        )
    }
}