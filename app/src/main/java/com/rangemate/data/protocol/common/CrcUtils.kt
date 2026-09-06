package com.rangemate.data.protocol.common

object CrcUtils {
    // CRC-16 Modbus
    fun crc16Modbus(data: ByteArray, start: Int, end: Int): Int {
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