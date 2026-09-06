package com.rangemate.ui.screens.settings

import com.rangemate.data.preferences.DevicePreferences
import com.rangemate.data.preferences.GlobalPreferences
import com.rangemate.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesManager
) : ViewModel() {

    private var currentDeviceMac: String? = null

    fun setCurrentDevice(macAddress: String) {
        currentDeviceMac = macAddress
        loadDeviceSettings(macAddress)
    }

    init {
        // Bind to the last-connected device so device sliders edit a real
        // device instead of a phantom. Falls back to explicit setCurrentDevice.
        viewModelScope.launch {
            preferences.globalPreferences
                .map { it.lastConnectedDeviceMac }
                .distinctUntilChanged()
                .collect { mac ->
                    if (mac != null) {
                        currentDeviceMac = mac
                        _deviceSettings.value = preferences.getDevicePreferences(mac)
                    }
                }
        }
    }

    // Device settings as a reactive flow
    private val _deviceSettings = MutableStateFlow<DevicePreferences?>(null)
    val deviceSettings: StateFlow<DevicePreferences> = _deviceSettings
        .asStateFlow()
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicePreferences(macAddress = ""))

    val globalSettings = preferences.globalPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalPreferences())

    // Load device settings when device is set
    fun loadDeviceSettings(macAddress: String) {
        viewModelScope.launch {
            _deviceSettings.value = preferences.getDevicePreferences(macAddress)
        }
    }

    // Device settings methods
    fun updateMaxPower(maxPower: Float) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { preferences.updateMaxPower(mac, maxPower) }
        }
    }

    fun updateFullRange(rangeKm: Float) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { preferences.updateFullRange(mac, rangeKm) }
        }
    }

    fun updateGreenZone(greenEnd: Float) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { 
                val prefs = preferences.getDevicePreferences(mac)
                preferences.updatePowerZones(mac, greenEnd, prefs.yellowZoneEnd)
            }
        }
    }

    fun updateYellowZone(yellowEnd: Float) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { 
                val prefs = preferences.getDevicePreferences(mac)
                preferences.updatePowerZones(mac, prefs.greenZoneEnd, yellowEnd)
            }
        }
    }

    fun updateDistanceUnit(unit: String) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { preferences.updateDistanceUnit(mac, unit) }
        }
    }

    fun updateWarningTemp(warning: Float) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { 
                val prefs = preferences.getDevicePreferences(mac)
                preferences.updateTempThresholds(mac, warning, prefs.criticalTempC)
            }
        }
    }

    fun updateCriticalTemp(critical: Float) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { 
                val prefs = preferences.getDevicePreferences(mac)
                preferences.updateTempThresholds(mac, prefs.warningTempC, critical)
            }
        }
    }

    fun updateShowPowerGraph(show: Boolean) {
        currentDeviceMac?.let { mac ->
            viewModelScope.launch { preferences.updateShowPowerGraph(mac, show) }
        }
    }

    // Global settings
    fun updateThemeMode(mode: String) {
        viewModelScope.launch { preferences.updateThemeMode(mode) }
    }

    fun updateAccentColor(color: Int) {
        viewModelScope.launch { preferences.updateAccentColor(color) }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { preferences.updateAutoReconnect(enabled) }
    }

    fun updateDashboardLayout(layout: String) {
        viewModelScope.launch { preferences.updateDashboardLayout(layout) }
    }

    fun updatePanelContent(left: String, right: String) {
        viewModelScope.launch { preferences.updatePanelContent(left, right) }
    }

    fun updateShowMetricsRow(show: Boolean) {
        viewModelScope.launch { preferences.updateShowMetricsRow(show) }
    }

    fun updateShowSpeedTop(show: Boolean) {
        viewModelScope.launch { preferences.updateShowSpeedTop(show) }
    }
}