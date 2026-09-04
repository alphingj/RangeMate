package com.rangemate.ui.screens.dashboard

import com.rangemate.data.location.SpeedProvider
import com.rangemate.data.model.BmsData
import com.rangemate.data.preferences.DevicePreferences
import com.rangemate.data.preferences.GlobalPreferences
import com.rangemate.data.preferences.PreferencesManager
import com.rangemate.data.repository.BmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val bmsRepository: BmsRepository,
    private val preferences: PreferencesManager,
    private val speedProvider: SpeedProvider
) : ViewModel() {

    val bmsData: StateFlow<BmsData> = bmsRepository.bmsData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BmsData())

    // Per-device settings based on connected device
    val deviceSettings: StateFlow<DevicePreferences> = bmsRepository.bmsData
        .filter { it.connected }
        .distinctUntilChangedBy { it.deviceName }
        .flatMapLatest { bmsData ->
            val mac = bmsData.deviceName // deviceName now holds MAC address
            preferences.getDevicePreferencesFlow(mac)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicePreferences(macAddress = ""))

    // Global settings
    val globalSettings: StateFlow<GlobalPreferences> = preferences.globalPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalPreferences())

    val speedKmh: StateFlow<Float> = speedProvider.speedKmh
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val isGpsEnabled: StateFlow<Boolean> = speedProvider.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Power history for graph (last 60 seconds)
    private val _powerHistory = MutableStateFlow<List<Pair<Long, Float>>>(emptyList())
    val powerHistory: StateFlow<List<Pair<Long, Float>>> = _powerHistory.asStateFlow()

    init {
        speedProvider.start()

        // Collect power data for graph
        bmsRepository.bmsData
            .filter { it.connected }
            .map { Pair(System.currentTimeMillis(), it.power) }
            .onEach { entry ->
                val current = _powerHistory.value.toMutableList()
                current.add(entry)
                // Keep last 60 seconds
                val cutoff = System.currentTimeMillis() - 60_000
                val filtered = current.filter { it.first > cutoff }
                _powerHistory.value = filtered
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        speedProvider.stop()
    }
}