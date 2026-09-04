package com.rangemate.data.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Xiaoxiang/JBD BMS protocol parser
 * Protocol: 0xA5 CMD LEN DATA CHECKSUM 0x77
 */
object XiaoxiangProtocol {

    fun buildReadCommand(command: Byte): ByteArray {
        val buffer = ByteArray(7)
        buffer[0] = 0xA5.toByte()
        buffer[1] = command
        buffer[2] = 0x00  // Length (always 0 for read commands)

        // Calculate checksum: sum of all bytes except start/end markers
        val checksum = ((buffer[1].toInt() and 0xFF) +
                       (buffer[2].toInt() and 0xFF)) and 0xFFFF
        buffer[3] = ((checksum shr 8) and 0xFF).toByte()
        buffer[4] = (checksum and 0xFF).toByte()
        buffer[5] = 0x77

        return buffer
    }

    fun parseBasicInfo(data: ByteArray): BasicInfo? {
        if (data.size < 34 || data[0] != 0xA5.toByte() || data[data.size - 1] != 0x77.toByte()) {
            return null
        }

        try {
            val buffer = ByteBuffer.wrap(data, 4, data.size - 7).order(ByteOrder.BIG_ENDIAN)

            val voltage = buffer.short.toInt() / 100f  // centivolts to volts
            val current = buffer.short.toInt() / 100f  // centiamps to amps
            val capacity = buffer.short.toInt() / 100f  // centi-Ah to Ah
            val totalCapacity = buffer.short.toInt() / 100f
            val cycles = buffer.short.toInt()

            buffer.short  // Production date - skip

            val balanceStatus = buffer.int
            val protectionStatus = buffer.short.toInt()
            val version = buffer.get().toInt() and 0xFF
            val soc = buffer.get().toInt() and 0xFF

            val fetStatus = buffer.int
            val cells = buffer.get().toInt() and 0xFF
            val ntcCount = buffer.get().toInt() and 0xFF

            // Temperature sensors - read first one
            val temp1 = if (ntcCount > 0) {
                ((buffer.short.toInt() - 2731) / 10f)  // Kelvin * 10 to Celsius
            } else 0f

            return BasicInfo(
                voltage = voltage,
                current = current,
                capacity = capacity,
                totalCapacity = totalCapacity,
                cycles = cycles,
                soc = soc,
                temperature = temp1,
                cellCount = cells
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun parseDeviceName(data: ByteArray): String? {
        if (data.size < 8 || data[0] != 0xA5.toByte() || data[data.size - 1] != 0x77.toByte()) {
            return null
        }

        try {
            // Device name response: 0xA5 0x05 [LEN] [NAME_BYTES...] [CHECKSUM] 0x77
            val nameLength = (data[2].toInt() and 0xFF)
            val nameBytes = data.copyOfRange(3, 3 + nameLength)
            return String(nameBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            return null
        }
    }

    data class BasicInfo(
        val voltage: Float,
        val current: Float,
        val capacity: Float,
        val totalCapacity: Float,
        val cycles: Int,
        val soc: Int,
        val temperature: Float,
        val cellCount: Int
    )
}