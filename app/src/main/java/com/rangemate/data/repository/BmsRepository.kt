package com.rangemate.data.repository

import com.rangemate.data.ble.BleManager
import com.rangemate.data.model.BmsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BmsRepository @Inject constructor(
    private val bleManager: BleManager
) {
    val bmsData: Flow<BmsData> = combine(
        bleManager.bmsData,
        bleManager.connectionState,
        bleManager.deviceName,
        bleManager.connectedDeviceMac,
        bleManager.cellVoltages
    ) { data, state, deviceName, macAddress, cells ->
        if (data != null && state == BleManager.ConnectionState.CONNECTED) {
            BmsData(
                voltage = data.voltage,
                current = data.current,
                power = data.voltage * data.current,
                soc = data.soc,
                capacity = data.capacity,
                totalCapacity = data.totalCapacity,
                cycles = data.cycles,
                temperature = data.temperature,
                cellVoltages = cells,
                protectionStatus = data.protectionStatus,
                fetStatus = data.fetStatus,
                manufacturer = data.manufacturer,
                connected = true,
                deviceName = macAddress ?: deviceName ?: "Unknown BMS",
                timestamp = System.currentTimeMillis()
            )
        } else {
            BmsData(connected = false)
        }
    }

    val connectionState = bleManager.connectionState
}