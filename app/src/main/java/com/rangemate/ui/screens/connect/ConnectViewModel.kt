package com.rangemate.ui.screens.connect

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rangemate.data.ble.BleManager
import com.rangemate.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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

    fun startScan() {
        viewModelScope.launch {
            bleManager.startScan()
        }
    }

    fun connect(device: BluetoothDevice) {
        bleManager.connect(device)
    }

    fun onDeviceConnected(macAddress: String) {
        viewModelScope.launch {
            preferences.recordConnection(macAddress)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}