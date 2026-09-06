package com.rangemate.data.model

data class BmsData(
    val voltage: Float = 0f,           // Pack voltage in V
    val current: Float = 0f,           // Current in A (negative = discharge, positive = charge)
    val power: Float = 0f,             // Power in W (calculated: voltage * current)
    val soc: Int = 0,                  // State of charge 0-100%
    val capacity: Float = 0f,          // Remaining capacity in Ah
    val totalCapacity: Float = 0f,     // Total capacity in Ah
    val cycles: Int = 0,               // Charge cycles
    val temperature: Float = 0f,       // Temperature in °C
    val cellVoltages: List<Float> = emptyList(), // Per-cell voltages in V
    val protectionStatus: Int = 0,   // Protection bitmask (JBD table)
    val fetStatus: Int = 0,          // MOS/FET state (bit0=charge, bit1=discharge)
    val manufacturer: String = "",   // Reporting protocol/driver
    val connected: Boolean = false,
    val deviceName: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val range: Float
        get() = if (totalCapacity > 0f) {
            (capacity / totalCapacity) * 70f  // Default 70km full range
        } else 0f
}
