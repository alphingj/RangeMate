package com.rangemate.data.protocol

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.flow.Flow

interface BmsProtocol {
    val type: ProtocolType
    val serviceUuid: String
    val characteristicUuid: String
    val scanFilterUuids: List<String>

    fun buildReadCommand(command: Byte): ByteArray
    fun parseBasicInfo(data: ByteArray): BasicInfo?
    fun parseCellVoltages(data: ByteArray): List<Float>?
    fun parseDeviceName(data: ByteArray): String?
    fun validatePacket(data: ByteArray): Boolean
    fun getSupportedCommands(): Set<Byte>

    // Default implementations
    fun buildBasicInfoCommand(): ByteArray = buildReadCommand(type.basicInfoCommand)
    fun buildCellVoltageCommand(): ByteArray = buildReadCommand(type.cellVoltageCommand)
    fun buildDeviceNameCommand(): ByteArray = buildReadCommand(type.deviceNameCommand)
}