package com.rangemate.data.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import com.rangemate.data.log.RawPacketLogger
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Software signature matcher for smart-BMS advertisements.
 *
 * JBD/Xiaoxiang boards frequently do NOT advertise their service UUID, so hardware
 * scan filters miss them. This evaluates every sighting in software instead.
 *
 * Correction vs naive implementations: service UUIDs are matched by FULL equality
 * against known 128-bit profiles — never by substring (e.g. "FFE1" is a
 * *characteristic* UUID and can never legitimately appear in an advertisement's
 * service list).
 */
@Singleton
class BmsScanFilter @Inject constructor(
    private val logger: RawPacketLogger? = null
) {
    enum class MatchReason {
        NAME_PREFIX,
        MANUFACTURER_ID,
        SERVICE_UUID,
        NONE
    }

    data class MatchResult(
        val isCompatible: Boolean,
        val reason: MatchReason,
        val details: String? = null
    )

    private val targetNamePrefixes = listOf("JBD_", "XIAOXIANG", "KOMAKI", "X1", "BMS", "BT_BMS", "ANT_")

    // PROVISIONAL — unverified against field captures. The logger records every
    // manufacturer ID seen in the wild; correct this list from real logs.
    // These IDs must NEVER be the sole gate for showing a device (tag, don't gate).
    private val targetManufacturerIds = listOf(0x0001, 0x55AA, 0x0002)

    private val targetServiceUuids = listOf(
        "0000FFE0-0000-1000-8000-00805F9B34FB", // Xiaoxiang / JBD / Overkill
        "0000FF00-0000-1000-8000-00805F9B34FB", // JBD DD-BLE (open_battery spec)
        "0000FFF0-0000-1000-8000-00805F9B34FB" // Daly
    )

    @SuppressLint("MissingPermission")
    fun evaluate(result: ScanResult): MatchResult {
        val device = result.device
        val scanRecord = result.scanRecord
        val deviceName = (device.name ?: scanRecord?.deviceName)?.uppercase(Locale.US)

        // 1. Name prefix signatures (no permission needed for scanRecord.deviceName;
        //    device.name requires BLUETOOTH_CONNECT, granted before scanning starts).
        if (deviceName != null) {
            for (prefix in targetNamePrefixes) {
                if (deviceName.startsWith(prefix)) {
                    logger?.logEvent("BLE_SCAN", "Filter MATCH [Name]: $deviceName (${device.address}) via prefix $prefix")
                    return MatchResult(true, MatchReason.NAME_PREFIX, "Name prefix: $prefix")
                }
            }
        }

        // 2. Manufacturer-specific data blocks.
        val manufacturerData = scanRecord?.manufacturerSpecificData
        if (manufacturerData != null && manufacturerData.size() > 0) {
            for (i in 0 until manufacturerData.size()) {
                val id = manufacturerData.keyAt(i)
                if (id in targetManufacturerIds) {
                    logger?.logEvent("BLE_SCAN", "Filter MATCH [MfgId]: 0x${Integer.toHexString(id).uppercase(Locale.US)} from ${device.address}")
                    return MatchResult(true, MatchReason.MANUFACTURER_ID, "Mfg ID: 0x${Integer.toHexString(id).uppercase(Locale.US)}")
                }
            }
        }

        // 3. Full 128-bit service UUID equality.
        val serviceUuids = scanRecord?.serviceUuids
        if (serviceUuids != null) {
            for (parcelUuid in serviceUuids) {
                val uuidStr = parcelUuid.uuid.toString()
                if (targetServiceUuids.any { it.equals(uuidStr, ignoreCase = true) }) {
                    logger?.logEvent("BLE_SCAN", "Filter MATCH [ServiceUUID]: $uuidStr from ${device.address}")
                    return MatchResult(true, MatchReason.SERVICE_UUID, "Service ${uuidStr.take(8)}…")
                }
            }
        }

        return MatchResult(false, MatchReason.NONE)
    }
}
