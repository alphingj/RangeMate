package com.rangemate.data.protocol.xiaoxiang

import com.rangemate.data.protocol.BasicInfo
import com.rangemate.data.protocol.BmsProtocol
import com.rangemate.data.protocol.ProtocolType
import com.rangemate.data.protocol.common.PacketUtils
import com.rangemate.data.protocol.common.readByte
import com.rangemate.data.protocol.common.readInt
import com.rangemate.data.protocol.common.readShort
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class XiaoxiangProtocol : BmsProtocol {
    override val type: ProtocolType = ProtocolType.XIAOXIANG
    override val serviceUuid: String = ProtocolType.XIAOXIANG.serviceUuid
    override val characteristicUuid: String = ProtocolType.XIAOXIANG.characteristicUuid
    override val scanFilterUuids: List<String> = ProtocolType.XIAOXIANG.scanFilterUuids

    companion object {
        const val PACKET_START: Byte = 0xA5.toByte()
        const val PACKET_END: Byte = 0x77
        private const val MAX_NTC_PROBES = 8
    }

    override fun buildReadCommand(command: Byte): ByteArray {
        val buffer = ByteArray(7)
        buffer[0] = PACKET_START
        buffer[1] = command
        buffer[2] = 0x00

        val checksum = ((buffer[1].toInt() and 0xFF) + (buffer[2].toInt() and 0xFF)) and 0xFFFF
        buffer[3] = ((checksum shr 8) and 0xFF).toByte()
        buffer[4] = (checksum and 0xFF).toByte()
        buffer[5] = PACKET_END
        buffer[6] = 0x00

        return buffer
    }

    override fun parseBasicInfo(data: ByteArray): BasicInfo? {
        if (data.size < 34 || data[0] != PACKET_START || data[data.size - 1] != PACKET_END) {
            return null
        }

        try {
            val buffer = PacketUtils.createByteBuffer(data, 4, data.size - 7, ByteOrder.BIG_ENDIAN)

            val voltage = buffer.readShort() / 100f
            val current = buffer.readShort() / 100f
            val capacity = buffer.readShort() / 100f
            val totalCapacity = buffer.readShort() / 100f
            val cycles = buffer.readShort()

            buffer.readShort() // Production date - skip

            val balanceStatus = buffer.readInt()
            val protectionStatus = buffer.readShort()
            val version = buffer.readByte()
            val soc = buffer.readByte()

            val fetStatus = buffer.readInt()
            val cells = buffer.readByte()
            val ntcCount = buffer.readByte()

            // Read every reported NTC probe with a remaining-bytes guard so
            // multi-probe packs (Komaki class) decode instead of throwing.
            // BasicInfo carries a single temperature; the first probe is kept
            // for compatibility while full arrays live in ExtendedBmsTelemetry.
            var temp1 = 0f
            repeat(ntcCount.coerceIn(0, MAX_NTC_PROBES)) { index ->
                if (buffer.remaining() >= 2) {
                    val celsius = (buffer.readShort() - 2731) / 10f
                    if (index == 0) temp1 = celsius
                }
            }

            return BasicInfo(
                voltage = voltage,
                current = current,
                capacity = capacity,
                totalCapacity = totalCapacity,
                cycles = cycles,
                soc = soc,
                temperature = temp1,
                cellCount = cells,
                protectionStatus = protectionStatus,
                fetStatus = fetStatus,
                balanceStatus = balanceStatus.toLong(),
                version = version
            )
        } catch (e: Exception) {
            return null
        }
    }

    override fun parseCellVoltages(data: ByteArray): List<Float>? {
        if (data.size < 8 || data[0] != PACKET_START || data[data.size - 1] != PACKET_END) {
            return null
        }

        try {
            val cellCount = (data[2].toInt() and 0xFF)
            if (cellCount == 0) return emptyList()

            val buffer = PacketUtils.createByteBuffer(data, 3, cellCount * 2, ByteOrder.BIG_ENDIAN)
            val voltages = mutableListOf<Float>()
            for (i in 0 until cellCount) {
                val voltage = buffer.readShort() / 1000f
                voltages.add(voltage)
            }
            return voltages
        } catch (e: Exception) {
            return null
        }
    }

    override fun parseDeviceName(data: ByteArray): String? {
        if (data.size < 8 || data[0] != PACKET_START || data[data.size - 1] != PACKET_END) {
            return null
        }

        try {
            val nameLength = (data[2].toInt() and 0xFF)
            val nameBytes = data.copyOfRange(3, 3 + nameLength)
            return String(nameBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            return null
        }
    }

    override fun validatePacket(data: ByteArray): Boolean {
        return data.size >= 8 &&
            data[0] == PACKET_START &&
            data[data.size - 1] == PACKET_END &&
            verifyChecksum(data)
    }

    private fun verifyChecksum(data: ByteArray): Boolean {
        if (data.size < 7) return false
        val checksum = ((data[1].toInt() and 0xFF) + (data[2].toInt() and 0xFF)) and 0xFFFF
        val receivedChecksum = ((data[3].toInt() shl 8) + (data[4].toInt() and 0xFF)) and 0xFFFF
        return checksum == receivedChecksum
    }

    override fun getSupportedCommands(): Set<Byte> = setOf(
        ProtocolType.XIAOXIANG.basicInfoCommand,
        ProtocolType.XIAOXIANG.cellVoltageCommand,
        ProtocolType.XIAOXIANG.deviceNameCommand
    )
}