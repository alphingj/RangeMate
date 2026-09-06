package com.rangemate.data.protocol.jbd

import com.rangemate.data.protocol.common.PacketUtils

/**
 * Shared pure parsers for the two JBD 0x03 payload dialects found in the wild.
 *
 * - LAYOUT_B_DOC: open_battery spec — voltage/10, current/10 signed, remaining
 *   /100, count-prefixed int8 temps (offset −40), RSOC, MOS byte, u16 protection.
 *   Self-describing; preferred when it validates.
 * - LAYOUT_A_LIB: rakhmaevao/JbdBms UART layout — voltage/100, current/100
 *   signed, cycles at +8, protection at +16, SOC at +19, Kelvin×10 temps from
 *   +23 with the count derived from frame length (the lib hardcodes 2 probes;
 *   Komaki-class packs carry more).
 *
 * Current scaling for layout A is signed/100 (classic Xiaoxiang 0.01 A units).
 * The lib's `* 10` current line is treated as a lib-side quirk, not wire truth.
 * Both layouts are gated by scooter-class sanity windows (pack assumed 20–150 V,
 * e.g. Komaki X-One 72 V nominal); the field log arbitrates any remainder.
 */
object JbdPayloads {

    enum class Layout { LAYOUT_B_DOC, LAYOUT_A_LIB }

    data class BasicData(
        val voltage: Float,
        val current: Float,
        val remainingAh: Float,
        val temperatures: List<Float>,
        val soc: Int,
        val mosCharge: Boolean,
        val mosDischarge: Boolean,
        val protection: Int,
        val cycles: Int,
        val nominalCapacityAh: Float,
        val layout: Layout
    )

    /** Tries the self-describing doc layout first, then the lib layout. */
    fun parseBasic(payload: ByteArray): BasicData? =
        parseLayoutB(payload) ?: parseLayoutA(payload)

    fun parseLayoutB(payload: ByteArray): BasicData? {
        if (payload.size < 11) return null
        return try {
            val voltage = PacketUtils.readU16BE(payload, 0) / 10f
            val current = PacketUtils.readI16BE(payload, 2) / 10f
            val remainingAh = PacketUtils.readU16BE(payload, 4) / 100f
            val count = payload[6].toInt() and 0xFF
            if (count !in 1..MAX_PROBES) return null
            if (payload.size < 11 + count) return null
            // int8 temps: Byte.toInt() sign-extends, then apply −40 offset.
            val temps = (0 until count).map { (payload[7 + it].toInt()) - 40f }
            val rsoc = payload[7 + count].toInt() and 0xFF
            val mos = payload[8 + count].toInt() and 0xFF
            val protection = PacketUtils.readU16BE(payload, 9 + count)

            if (voltage !in PACK_VOLTAGE_RANGE) return null
            if (rsoc !in 0..100) return null
            if (!temps.all { it in PACK_TEMP_RANGE }) return null

            BasicData(
                voltage = voltage,
                current = current,
                remainingAh = remainingAh,
                temperatures = temps,
                soc = rsoc,
                mosCharge = mos and 0x01 != 0,
                mosDischarge = mos and 0x02 != 0,
                protection = protection,
                cycles = 0,
                nominalCapacityAh = 0f,
                layout = Layout.LAYOUT_B_DOC
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseLayoutA(payload: ByteArray): BasicData? {
        // Absolute lib offsets minus the 4-byte DD CMD STAT LEN header.
if (payload.size < MIN_A_SIZE) return null
        return try {
            val voltage = PacketUtils.readU16BE(payload, 0) / 100f
            val current = PacketUtils.readI16BE(payload, 2) / 100f
            val cycles = PacketUtils.readU16BE(payload, 8)
            val nominalCapacityAh = PacketUtils.readU16BE(payload, 4) / 100f
            val protection = PacketUtils.readU16BE(payload, 16)
            val soc = payload[19].toInt() and 0xFF
            val tempBytes = payload.size - 23
            if (tempBytes < 2 || tempBytes % 2 != 0 || tempBytes / 2 !in 1..MAX_PROBES) return null
            val temps = (0 until tempBytes / 2).map { i ->
                (PacketUtils.readU16BE(payload, 23 + i * 2) - KELVIN_OFFSET) / 10f
            }

            if (voltage !in PACK_VOLTAGE_RANGE) return null
            if (soc !in 0..100) return null
            if (!temps.all { it in PACK_TEMP_RANGE }) return null

BasicData(
                voltage = voltage,
                current = current,
                remainingAh = 0f,
                temperatures = temps,
                soc = soc,
                mosCharge = false,
                mosDischarge = false,
                protection = protection,
                cycles = cycles,
                nominalCapacityAh = nominalCapacityAh,
                layout = Layout.LAYOUT_A_LIB
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cell voltages in volts.
     * Layout B carries a count byte; layout A encodes count as length/2 (lib).
     */
    fun parseCells(payload: ByteArray): List<Float>? {
        if (payload.size >= 3) {
            val count = payload[0].toInt() and 0xFF
            if (count in 1..MAX_CELLS && payload.size == 1 + 2 * count) {
                val volts = (0 until count).map { PacketUtils.readU16BE(payload, 1 + it * 2) / 1000f }
                if (volts.all { it in CELL_VOLTAGE_RANGE }) return volts else return null
            }
        }
        if (payload.size >= 2 && payload.size % 2 == 0) {
            val count = payload.size / 2
            if (count in 1..MAX_CELLS) {
                val volts = (0 until count).map { PacketUtils.readU16BE(payload, it * 2) / 1000f }
                if (volts.all { it in CELL_VOLTAGE_RANGE }) return volts
            }
        }
        return null
    }

    private const val MAX_PROBES = 8
    private const val MAX_CELLS = 32
    private const val KELVIN_OFFSET = 2731
    private const val MIN_A_SIZE = 25

    /** Scooter-class pack window (Komaki X-One is 72 V nominal). */
    private val PACK_VOLTAGE_RANGE = 20f..150f
    private val PACK_TEMP_RANGE = -20f..80f
    private val CELL_VOLTAGE_RANGE = 2.0f..4.5f
}
