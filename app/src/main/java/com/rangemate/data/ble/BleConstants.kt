package com.rangemate.data.ble

object BleConstants {
    // Xiaoxiang/JBD BMS service UUID
    const val BMS_SERVICE_UUID = "0000FFE0-0000-1000-8000-00805F9B34FB"
    const val BMS_CHARACTERISTIC_UUID = "0000FFE1-0000-1000-8000-00805F9B34FB"

    // Protocol commands
    const val CMD_READ_BASIC_INFO: Byte = 0x03
    const val CMD_READ_CELL_VOLTAGE: Byte = 0x04
    const val CMD_READ_DEVICE_NAME: Byte = 0x05

    // Protocol header/footer
    const val PACKET_START: Byte = 0xA5.toByte()
    const val PACKET_END: Byte = 0x77

    // Scan settings
    const val SCAN_TIMEOUT_MS = 10000L
    const val CONNECTION_TIMEOUT_MS = 15000L

    // Update intervals
    const val BMS_UPDATE_INTERVAL_MS = 1000L
}
