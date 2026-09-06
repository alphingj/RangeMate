package com.rangemate.data.protocol.common

/**
 * JBD protection-status bit assignments, adopted from the rakhmaevao/JbdBms
 * reference implementation (tested against real JBD hardware).
 *
 * Replaces all provisional bit guesses previously used in this codebase.
 */
object ProtectionFlags {
    const val CELL_OVP = 1
    const val CELL_UVP = 2
    const val PACK_OVP = 4
    const val PACK_UVP = 8
    const val CHG_OTP = 16
    const val CHG_UTP = 32
    const val DSG_OTP = 64
    const val DSG_UTP = 128
    const val CHG_OCP = 256
    const val DSG_OCP = 512
    const val SHORT_CIRCUIT = 1024
    const val AFE_ERROR = 2048
    const val SOFT_LOCK = 4096
    const val CHG_OVERTIME = 8192
    const val DSG_OVERTIME = 16384

    /** Bits that cut output power (lib: 0x46CA). */
    const val POWER_OFF_ERRORS = 0x46CA

    data class Fault(
        val mask: Int,
        val label: String,
        val cutsPower: Boolean
    )

    private val TABLE = listOf(
        Fault(CELL_OVP, "Cell over-voltage", false),
        Fault(CELL_UVP, "Cell under-voltage", true),
        Fault(PACK_OVP, "Pack over-voltage", false),
        Fault(PACK_UVP, "Pack under-voltage", true),
        Fault(CHG_OTP, "Charge over-temp", false),
        Fault(CHG_UTP, "Charge under-temp", false),
        Fault(DSG_OTP, "Discharge over-temp", true),
        Fault(DSG_UTP, "Discharge under-temp", true),
        Fault(CHG_OCP, "Charge over-current", false),
        Fault(DSG_OCP, "Discharge over-current", true),
        Fault(SHORT_CIRCUIT, "Short circuit", true),
        Fault(AFE_ERROR, "AFE error", false),
        Fault(SOFT_LOCK, "Soft lock", false),
        Fault(CHG_OVERTIME, "Charge overtime", false),
        Fault(DSG_OVERTIME, "Discharge overtime", true)
    )

    fun decode(mask: Int): List<Fault> = TABLE.filter { mask and it.mask != 0 }

    fun labels(mask: Int): List<String> = decode(mask).map { it.label }

    fun isPowerOffImminent(mask: Int): Boolean = mask and POWER_OFF_ERRORS != 0
}
