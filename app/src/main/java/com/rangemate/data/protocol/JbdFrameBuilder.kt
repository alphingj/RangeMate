package com.rangemate.data.protocol

/**
 * Builds JBD read frames.
 *
 * Both references (open_battery spec, rakhmaevao/JbdBms) agree on exactly one
 * request convention: [DD, A5, CMD, LEN, DATA, CHK_HI, CHK_LO, 77] with
 * checksum = 0x10000 - (CMD + LEN + DATA), e.g. 0x03 -> DD A5 03 00 FF FD 77.
 *
 * - DD_A5: the confirmed format — primary probe and polling format.
 * - LEGACY_A5_SUM: this repo's historical A5-first emission. No reference
 *   supports it; kept as a last-resort probe only, pending field confirmation.
 */
object JbdFrameBuilder {

    enum class FrameFormat {
        DD_A5,
        LEGACY_A5_SUM
    }

    fun buildReadCommand(commandByte: Byte, format: FrameFormat): ByteArray {
        return when (format) {
            FrameFormat.DD_A5 -> {
                val out = ByteArray(7)
                out[0] = 0xDD.toByte()
                out[1] = 0xA5.toByte()
                out[2] = commandByte
                out[3] = 0x00
                val chk = (0x10000 - ((commandByte.toInt() and 0xFF) + 0x00)) and 0xFFFF
                out[4] = ((chk shr 8) and 0xFF).toByte()
                out[5] = (chk and 0xFF).toByte()
                out[6] = 0x77
                out
            }
            FrameFormat.LEGACY_A5_SUM -> {
                val out = ByteArray(7)
                out[0] = 0xA5.toByte()
                out[1] = commandByte
                out[2] = 0x00
                val sum = (commandByte.toInt() and 0xFF) + 0x00
                out[3] = ((sum shr 8) and 0xFF).toByte()
                out[4] = (sum and 0xFF).toByte()
                out[5] = 0x77
                out[6] = 0x00
                out
            }
        }
    }

    fun buildBasicInfoCommand(format: FrameFormat): ByteArray =
        buildReadCommand(ProtocolType.XIAOXIANG.basicInfoCommand, format)

    /** Basic-info probes, confirmed format first. */
    fun buildBasicInfoProbes(): List<Pair<FrameFormat, ByteArray>> =
        FrameFormat.values().map { it to buildBasicInfoCommand(it) }
}
