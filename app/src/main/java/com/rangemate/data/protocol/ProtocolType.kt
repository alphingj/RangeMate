package com.rangemate.data.protocol

enum class ProtocolType(
    val displayName: String,
    val serviceUuid: String,
    val characteristicUuid: String,
    val scanFilterUuids: List<String>,
    val basicInfoCommand: Byte,
    val cellVoltageCommand: Byte,
    val deviceNameCommand: Byte,
    val priority: Int
) {
    XIAOXIANG(
        displayName = "Xiaoxiang/JBD",
        serviceUuid = "0000FFE0-0000-1000-8000-00805F9B34FB",
        characteristicUuid = "0000FFE1-0000-1000-8000-00805F9B34FB",
        scanFilterUuids = listOf("0000FFE0-0000-1000-8000-00805F9B34FB"),
        basicInfoCommand = 0x03,
        cellVoltageCommand = 0x04,
        deviceNameCommand = 0x05,
        priority = 1
    ),
    JBD_DD(
        displayName = "JBD (DD-BLE)",
        serviceUuid = "0000FF00-0000-1000-8000-00805F9B34FB",
        characteristicUuid = "0000FF02-0000-1000-8000-00805F9B34FB",
        scanFilterUuids = listOf("0000FF00-0000-1000-8000-00805F9B34FB"),
        basicInfoCommand = 0x03,
        cellVoltageCommand = 0x04,
        deviceNameCommand = 0x05,
        priority = 2
    ),
    JBD(
        displayName = "JBD (Newer)",
        serviceUuid = "0000FFE0-0000-1000-8000-00805F9B34FB",
        characteristicUuid = "0000FFE1-0000-1000-8000-00805F9B34FB",
        scanFilterUuids = listOf("0000FFE0-0000-1000-8000-00805F9B34FB"),
        basicInfoCommand = 0x03,
        cellVoltageCommand = 0x04,
        deviceNameCommand = 0x05,
        priority = 3
    ),
    OVERKILL(
        displayName = "Overkill Solar",
        serviceUuid = "0000FFE0-0000-1000-8000-00805F9B34FB",
        characteristicUuid = "0000FFE1-0000-1000-8000-00805F9B34FB",
        scanFilterUuids = listOf("0000FFE0-0000-1000-8000-00805F9B34FB"),
        basicInfoCommand = 0x03,
        cellVoltageCommand = 0x04,
        deviceNameCommand = 0x05,
        priority = 4
    ),
    DALY(
        displayName = "Daly BMS",
        serviceUuid = "0000FFF0-0000-1000-8000-00805F9B34FB",
        characteristicUuid = "0000FFF1-0000-1000-8000-00805F9B34FB",
        scanFilterUuids = listOf("0000FFF0-0000-1000-8000-00805F9B34FB"),
        basicInfoCommand = 0x90.toByte(),
        cellVoltageCommand = 0x95.toByte(),
        deviceNameCommand = 0x91.toByte(),
        priority = 5
    ),
    GENERIC(
        displayName = "Generic/Unknown",
        serviceUuid = "",
        characteristicUuid = "",
        scanFilterUuids = emptyList(),
        basicInfoCommand = 0x00.toByte(),
        cellVoltageCommand = 0x00.toByte(),
        deviceNameCommand = 0x00.toByte(),
        priority = 99
    );

    companion object {
        fun fromName(name: String): ProtocolType? {
            return values().firstOrNull { it.displayName.equals(name, ignoreCase = true) }
        }

        fun fromServiceUuid(uuid: String): ProtocolType? {
            return values().firstOrNull { it.serviceUuid.equals(uuid, ignoreCase = true) }
        }

        val autoDetectionOrder: List<ProtocolType>
            get() = values().filter { it != GENERIC }.sortedBy { it.priority }
    }
}