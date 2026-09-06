package com.rangemate.data.protocol

/**
 * Extended telemetry footprint for JBD-family packs with multi-probe thermal
 * arrays (e.g. Komaki X-One quad-sensor pack).
 *
 * Float types mirror [BasicInfo] so this drops into the existing UI pipeline via
 * [toBasicInfo]. Fields parsed from offsets not yet confirmed against a field
 * capture are populated best-effort; see [komaki.KomakiExtendedParser].
 */
data class ExtendedBmsTelemetry(
    val voltage: Float,
    val current: Float,
    val power: Float,
    val soc: Int,
    val cycleCount: Int = 0,
    /** All decoded NTC probe readings in °C (empty until offsets confirmed). */
    val temperatures: List<Float> = emptyList(),
    /** Raw protection status word as received on the wire. */
    val protectionBitmask: Int = 0,
    // Bit decodes use the authoritative ProtectionFlags table (rakhmaevao/JbdBms).
    val isOverTempCharging: Boolean = false,
    val isOverTempDischarging: Boolean = false,
    val isShortCircuitTriggered: Boolean = false,
    /** MOS charge/discharge FET state (layout-B MOS byte; layout A reports none). */
    val mosCharge: Boolean = false,
    val mosDischarge: Boolean = false,
    /** Nominal pack capacity in Ah (layout-A cycles offset = 8; layout-B not present). */
    val nominalCapacityAh: Float = 0f
) {
    fun toBasicInfo(): BasicInfo = BasicInfo(
        voltage = voltage,
        current = current,
        capacity = 0f,
        totalCapacity = nominalCapacityAh,
        cycles = cycleCount,
        soc = soc,
        temperature = temperatures.firstOrNull() ?: 0f,
        cellCount = 0,
        protectionStatus = protectionBitmask,
        fetStatus = (if (mosCharge) 1 else 0) or (if (mosDischarge) 2 else 0)
    )
}
