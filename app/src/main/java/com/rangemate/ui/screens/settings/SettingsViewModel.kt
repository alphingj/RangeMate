package com.rangemate.ui.screens.settings

import com.rangemate.data.preferences.PreferencesManager
import com.rangemate.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesManager
) : ViewModel() {

    val settings = preferences.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun updateMaxPower(maxPower: Float) {
        viewModelScope.launch { preferences.updateMaxPower(maxPower) }
    }

    fun updateFullRange(rangeKm: Float) {
        viewModelScope.launch { preferences.updateFullRange(rangeKm) }
    }

    fun updateGreenZone(greenEnd: Float) {
        val current = settings.value ?: UserPreferences()
        viewModelScope.launch {
            preferences.updatePowerZones(greenEnd, current.yellowZoneEnd)
        }
    }

    fun updateYellowZone(yellowEnd: Float) {
        val current = settings.value ?: UserPreferences()
        viewModelScope.launch {
            preferences.updatePowerZones(current.greenZoneEnd, yellowEnd)
        }
    }

    fun updateDistanceUnit(unit: String) {
        viewModelScope.launch { preferences.updateDistanceUnit(unit) }
    }

    fun updateWarningTemp(warning: Float) {
        val current = settings.value ?: UserPreferences()
        viewModelScope.launch {
            preferences.updateTempThresholds(warning, current.criticalTempC)
        }
    }

    fun updateCriticalTemp(critical: Float) {
        val current = settings.value ?: UserPreferences()
        viewModelScope.launch {
            preferences.updateTempThresholds(current.warningTempC, critical)
        }
    }

    fun updateShowPowerGraph(show: Boolean) {
        viewModelScope.launch { preferences.updateShowPowerGraph(show) }
    }
}