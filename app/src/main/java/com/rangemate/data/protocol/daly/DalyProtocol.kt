package com.rangemate.data.protocol.daly

import com.rangemate.data.protocol.BasicInfo
import com.rangemate.data.protocol.BmsProtocol
import com.rangemate.data.protocol.ProtocolType
import com.rangemate.data.protocol.common.CrcUtils
import com.rangemate.data.protocol.common.PacketUtils
import com.rangemate.data.protocol.common.readShort
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class DalyProtocol : BmsProtocol {
    override val type: ProtocolType = ProtocolType.DALY
    override val serviceUuid: String = ProtocolType.DALY.serviceUuid
    override val characteristicUuid: String = ProtocolType.DALY.characteristicUuid
    override val scanFilterUuids: List<String> = ProtocolType.DALY.scanFilterUuids

    companion object {
        const val PACKET_START: Byte = 0xA5.toByte()
        const val PACKET_END: Byte = 0x77.toByte()
        const val CMD_SOC: Byte = 0x90.toByte()
        const val CMD_CELL_VOLTAGE: Byte = 0x95.toByte()
        const val CMD_DEVICE_NAME: Byte = 0x91.toByte()
    }

    override fun buildReadCommand(command: Byte): ByteArray {
        val buffer = ByteArray(13)
        buffer[0] = PACKET_START
        buffer[1] = command
        buffer[2] = 0x00
        buffer[3] = 0x00
        buffer[4] = 0x00
        buffer[5] = 0x00
        buffer[6] = 0x00
        buffer[7] = 0x00
        buffer[8] = 0x00
        buffer[9] = 0x00
        buffer[10] = 0x00

        val crc = CrcUtils.crc16Modbus(buffer, 0, 11)
        buffer[11] = ((crc shr 8) and 0xFF).toByte()
        buffer[12] = (crc and 0xFF).toByte()

        return buffer
    }

    override fun parseBasicInfo(data: ByteArray): BasicInfo? {
        if (data.size < 20) return null

        try {
            // Daly uses Modbus-style frames
            // Format: A5 [CMD] [LEN] [DATA...] [CRC16] [CRC16] 77
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            if (data[0] != PACKET_START || data[data.size - 1] != PACKET_END) {
                return null
            }

            val cmd = buffer.get(1).toInt() and 0xFF
            if (cmd != 0x90) return null // Not SOC data

            val len = buffer.get(2).toInt() and 0xFF
            if (data.size < 4 + len + 2) return null

            val dataStart = 3
            val socBuffer = ByteBuffer.wrap(data, dataStart, len).order(ByteOrder.BIG_ENDIAN)

            val voltage = socBuffer.readShort() / 10f // 0.1V
            // Daly encodes current with a 30000 offset: (raw - 30000) / 10 = A.
            val current = (socBuffer.readShort() - 30000) / 10f
            val soc = socBuffer.get().toInt() and 0xFF
            val capacity = socBuffer.readShort() / 100f // 0.01Ah
            val totalCapacity = socBuffer.readShort() / 100f
            val cycles = socBuffer.readShort()
            val temp1 = (socBuffer.readShort() - 2731) / 10f // Kelvin*10 to Celsius
            socBuffer.readShort() // NTC probe 2 (reserved; keeps buffer aligned)
            val cells = socBuffer.get().toInt() and 0xFF

            return BasicInfo(
                voltage = voltage,
                current = current,
                capacity = capacity,
                totalCapacity = totalCapacity,
                cycles = cycles,
                soc = soc,
                temperature = temp1,
                cellCount = cells,
                manufacturer = "Daly"
            )
        } catch (e: Exception) {
            return null
        }
    }

    override fun parseCellVoltages(data: ByteArray): List<Float>? {
        if (data.size < 8) return null

        try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            if (data[0] != PACKET_START || data[data.size - 1] != PACKET_END) {
                return null
            }

            val cmd = buffer.get(1).toInt() and 0xFF
            if (cmd != 0x95) return null // Not cell voltage data

            val len = buffer.get(2).toInt() and 0xFF
            if (data.size < 4 + len + 2) return null

            val cellCount = len / 2
            val voltages = mutableListOf<Float>()
            val cellBuffer = ByteBuffer.wrap(data, 3, len).order(ByteOrder.BIG_ENDIAN)

            for (i in 0 until cellCount) {
                val voltage = cellBuffer.readShort() / 1000f // mV to V
                voltages.add(voltage)
            }
            return voltages
        } catch (e: Exception) {
            return null
        }
    }

    override fun parseDeviceName(data: ByteArray): String? {
        if (data.size < 8) return null

        try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            if (data[0] != PACKET_START || data[data.size - 1] != PACKET_END) {
                return null
            }

            val cmd = buffer.get(1).toInt() and 0xFF
            if (cmd != 0x91) return null

            val len = buffer.get(2).toInt() and 0xFF
            val nameBytes = data.copyOfRange(3, 3 + len)
            return String(nameBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            return null
        }
    }

    override fun validatePacket(data: ByteArray): Boolean {
        if (data.size < 8) return false
        if (data[0] != PACKET_START || data[data.size - 1] != PACKET_END) return false

        val crc = CrcUtils.crc16Modbus(data, 0, data.size - 3)
        val receivedCrc = ((data[data.size - 3].toInt() shl 8) + (data[data.size - 2].toInt() and 0xFF)) and 0xFFFF
        return crc == receivedCrc
    }

    override fun getSupportedCommands(): Set<Byte> = setOf(
        CMD_SOC,
        CMD_CELL_VOLTAGE,
        CMD_DEVICE_NAME
    )
}