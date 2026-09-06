package com.rangemate.data.protocol.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteBuffer.readShort(): Int = this.short.toInt() and 0xFFFF
fun ByteBuffer.readUShort(): Int = this.short.toInt() and 0xFFFF
fun ByteBuffer.readInt(): Int = this.int
fun ByteBuffer.readUInt(): Long = this.int.toLong() and 0xFFFFFFFFL
fun ByteBuffer.readLong(): Long = this.long
fun ByteBuffer.readByte(): Int = this.get().toInt() and 0xFF

object PacketUtils {
    fun createByteBuffer(data: ByteArray, offset: Int, length: Int, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteBuffer {
        return ByteBuffer.wrap(data, offset, length).order(order)
    }

    fun bytesToHex(data: ByteArray): String {
        return data.joinToString(" ") { String.format("%02X", it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        return hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    }

    fun calculateChecksumSum(data: ByteArray, start: Int, end: Int): Int {
        var sum = 0
        for (i in start until end) {
            sum += data[i].toInt() and 0xFF
        }
        return sum and 0xFFFF
    }

    fun calculateChecksumXor(data: ByteArray, start: Int, end: Int): Int {
        var xor = 0
        for (i in start until end) {
            xor = xor xor (data[i].toInt() and 0xFF)
        }
        return xor and 0xFF
    }

    /**
     * Validates a DD..77 JBD-family response frame and returns its payload.
     *
     * Both references agree on one rule (their formulas are algebraically
     * identical): checksum = 0x10000 - (length + sum(data bytes)).
     * Frame: DD CMD STAT LEN D[0..LEN) CHK_H CHK_L 77, STAT must be 0x00.
     */
    fun extractDdPayload(frame: ByteArray): ByteArray? {
        if (frame.size < 7) return null
        if (frame[0] != 0xDD.toByte() || frame[frame.size - 1] != 0x77.toByte()) return null
        if ((frame[2].toInt() and 0xFF) != 0x00) return null
        val len = frame[3].toInt() and 0xFF
        if (frame.size != len + 7) return null
        val received = ((frame[4 + len].toInt() and 0xFF) shl 8) or (frame[5 + len].toInt() and 0xFF)
        val computed = (0x10000 - (len + calculateChecksumSum(frame, 4, 4 + len))) and 0xFFFF
        if (received != computed) return null
        return frame.copyOfRange(4, 4 + len)
    }

    fun readU16BE(data: ByteArray, index: Int): Int =
        ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)

    fun readI16BE(data: ByteArray, index: Int): Int {
        val raw = readU16BE(data, index)
        return if (raw and 0x8000 != 0) raw - 0x10000 else raw
    }

    fun calculateCrc16Modbus(data: ByteArray, start: Int, end: Int): Int {
        var crc = 0xFFFF
        for (i in start until end) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0..7) {
                if ((crc and 0x0001) != 0) {
                    crc = (crc shr 1) xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        return crc and 0xFFFF
    }
}