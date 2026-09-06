package com.rangemate.data.protocol.jbd

import com.rangemate.data.protocol.BasicInfo
import com.rangemate.data.protocol.BmsProtocol
import com.rangemate.data.protocol.JbdFrameBuilder
import com.rangemate.data.protocol.ProtocolType
import com.rangemate.data.protocol.common.PacketUtils

/**
 * JBD DD-dialect protocol (open_battery BLE spec: service FF00/char FF02).
 *
 * Requests use the confirmed DD A5 framing; responses validate under the
 * unified DD rule and parse under layout B (doc) with layout A (lib) fallback.
 * Payload decoding is shared with [JbdPayloads] so detection, polling and the
 * Komaki scaffold can never disagree.
 */
class JbdDdProtocol : BmsProtocol {
    override val type: ProtocolType = ProtocolType.JBD_DD
    override val serviceUuid: String = ProtocolType.JBD_DD.serviceUuid
    override val characteristicUuid: String = ProtocolType.JBD_DD.characteristicUuid
    override val scanFilterUuids: List<String> = ProtocolType.JBD_DD.scanFilterUuids

    override fun buildReadCommand(command: Byte): ByteArray =
        JbdFrameBuilder.buildReadCommand(command, JbdFrameBuilder.FrameFormat.DD_A5)

    override fun validatePacket(data: ByteArray): Boolean =
        PacketUtils.extractDdPayload(data) != null

    override fun parseBasicInfo(data: ByteArray): BasicInfo? {
        val payload = PacketUtils.extractDdPayload(data) ?: return null
        val parsed = JbdPayloads.parseBasic(payload) ?: return null
        return BasicInfo(
            voltage = parsed.voltage,
            current = parsed.current,
            capacity = parsed.remainingAh,
            totalCapacity = 0f,
            cycles = parsed.cycles,
            soc = parsed.soc,
            temperature = if (parsed.temperatures.isNotEmpty()) {
                parsed.temperatures.average().toFloat()
            } else 0f,
            cellCount = 0,
            protectionStatus = parsed.protection,
            fetStatus = (if (parsed.mosCharge) 1 else 0) or (if (parsed.mosDischarge) 2 else 0),
            manufacturer = "JBD"
        )
    }

    override fun parseCellVoltages(data: ByteArray): List<Float>? {
        val payload = PacketUtils.extractDdPayload(data) ?: return null
        return JbdPayloads.parseCells(payload)
    }

    override fun parseDeviceName(data: ByteArray): String? = null

    override fun getSupportedCommands(): Set<Byte> = setOf(
        ProtocolType.JBD_DD.basicInfoCommand,
        ProtocolType.JBD_DD.cellVoltageCommand
    )
}
