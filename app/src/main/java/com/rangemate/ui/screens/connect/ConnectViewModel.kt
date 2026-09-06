package com.rangemate.ui.screens.connect

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rangemate.data.ble.BleManager
import com.rangemate.data.preferences.PreferencesManager
import com.rangemate.data.protocol.ProtocolType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val preferences: PreferencesManager
) : ViewModel() {

    val scanResults = bleManager.scanResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val connectionState = bleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleManager.ConnectionState.DISCONNECTED)

    val connectedMac = bleManager.connectedDeviceMac
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val detectedProtocol = bleManager.detectedProtocol
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val protocolDetectionState = bleManager.protocolDetectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleManager.DetectionState.IDLE)

    val compatibleMacs = bleManager.compatibleMacs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _filterCompatibleOnly = MutableStateFlow(false)
    val filterCompatibleOnly: StateFlow<Boolean> = _filterCompatibleOnly

    fun toggleCompatibilityFilter() {
        _filterCompatibleOnly.value = !_filterCompatibleOnly.value
    }

    fun startScan() {
        viewModelScope.launch {
            bleManager.startScan()
        }
    }

    fun connect(device: BluetoothDevice, protocolType: ProtocolType? = null) {
        bleManager.connect(device, protocolType)
    }

    fun onDeviceConnected(macAddress: String) {
        viewModelScope.launch {
            preferences.recordConnection(macAddress)
            // Persist the verified protocol so Settings shows real data.
            bleManager.detectedProtocol.value?.let { type ->
                preferences.cacheProtocol(macAddress, type.displayName)
            }
        }
    }

    // NOTE: no onCleared disconnect — the connection must survive navigation
    // to Dashboard (Connect is popped inclusively). Disconnect is explicit only.
}