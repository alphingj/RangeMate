package com.rangemate.data.protocol.komaki

import com.rangemate.data.log.RawPacketLogger
import com.rangemate.data.protocol.ExtendedBmsTelemetry
import com.rangemate.data.protocol.common.PacketUtils
import com.rangemate.data.protocol.common.ProtectionFlags
import com.rangemate.data.protocol.jbd.JbdPayloads
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drop-in decoder for extended JBD-family payloads (Komaki X-One class).
 *
 * Payload decoding is shared with [JbdPayloads] (single source of truth with
 * JbdDdProtocol): layout B per the open_battery spec, layout A per the
 * rakhmaevao/JbdBms reference, each gated by checksum + sanity windows.
 * Protection bits use the reference bit table — no guesses remain.
 */
@Singleton
class KomakiExtendedParser @Inject constructor(
    private val logger: RawPacketLogger? = null
) {
    fun parseExtendedPayload(rawFrame: ByteArray): ExtendedBmsTelemetry? {
        val payload = PacketUtils.extractDdPayload(rawFrame)
        if (payload == null) {
            logger?.logEvent("BMS_PROTO", "Extended parse: frame rejected by DD validation")
            return null
        }
        val parsed = JbdPayloads.parseBasic(payload)
        if (parsed == null) {
            logger?.logEvent("BMS_PROTO", "Extended parse: no layout validated (${payload.size}B payload)")
            return null
        }

        val faults = ProtectionFlags.labels(parsed.protection)
        logger?.logEvent(
            "BMS_PROTO",
            "Extended decode [${parsed.layout}]: V=${parsed.voltage} A=${parsed.current} " +
                "SOC=${parsed.soc}% probes=${parsed.temperatures.size} " +
                "mask=0x${Integer.toHexString(parsed.protection)} faults=$faults"
        )

        return ExtendedBmsTelemetry(
            voltage = parsed.voltage,
            current = parsed.current,
            power = parsed.voltage * parsed.current,
            soc = parsed.soc,
            cycleCount = parsed.cycles,
            temperatures = parsed.temperatures,
            protectionBitmask = parsed.protection,
            isOverTempCharging = parsed.protection and ProtectionFlags.CHG_OTP != 0,
            isOverTempDischarging = parsed.protection and ProtectionFlags.DSG_OTP != 0,
            isShortCircuitTriggered = parsed.protection and ProtectionFlags.SHORT_CIRCUIT != 0,
            mosCharge = parsed.mosCharge,
            mosDischarge = parsed.mosDischarge
        )
    }
}
