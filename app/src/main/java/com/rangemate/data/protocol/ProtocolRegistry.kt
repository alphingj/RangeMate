package com.rangemate.data.protocol

import android.bluetooth.BluetoothGatt
import com.rangemate.data.log.RawPacketLogger
import com.rangemate.data.protocol.common.PacketUtils
import com.rangemate.data.protocol.daly.DalyProtocol
import com.rangemate.data.protocol.jbd.JbdDdProtocol
import com.rangemate.data.protocol.xiaoxiang.XiaoxiangProtocol
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class ProtocolRegistry {
    private val protocols: Map<ProtocolType, BmsProtocol> = mapOf(
        ProtocolType.XIAOXIANG to XiaoxiangProtocol(),
        ProtocolType.JBD_DD to JbdDdProtocol(),
        ProtocolType.JBD to XiaoxiangProtocol(),
        ProtocolType.OVERKILL to XiaoxiangProtocol(),
        ProtocolType.DALY to DalyProtocol()
    )

    fun getProtocol(type: ProtocolType): BmsProtocol? = protocols[type]

    fun getAllProtocols(): List<BmsProtocol> = protocols.values.toList()

    fun getAutoDetectionProtocols(): List<BmsProtocol> = protocols.values
        .filter { it.type != ProtocolType.GENERIC }
        .distinctBy { it.type }
        .sortedBy { it.type.priority }

    fun getProtocolByServiceUuid(uuid: String): BmsProtocol? = protocols.values
        .firstOrNull { it.serviceUuid.equals(uuid, ignoreCase = true) }
}

class ProtocolAutoDetector(
    private val registry: ProtocolRegistry = ProtocolRegistry(),
    private val logger: RawPacketLogger? = null
) {
    data class DetectionResult(
        val protocol: BmsProtocol,
        /** Wire format that earned a response; null when matched by UUID or fallback. */
        val frameFormat: JbdFrameBuilder.FrameFormat?,
        /** True once variant probing actually ran (vs instant UUID match). */
        val probed: Boolean
    )

    /**
     * Multi-variant interrogation.
     *
     * Responses are NOT captured here — BLE forbids ad-hoc callbacks. The caller
     * routes live notification payloads via [registerListener] (see
     * BleManager.setDetectionListener), which breaks the DI cycle that a
     * constructor-held BleManager reference would create.
     */
    suspend fun detectProtocol(
        gatt: BluetoothGatt,
        registerListener: (((ByteArray) -> Unit)?) -> Unit,
        writeChannel: (ByteArray) -> Boolean
    ): DetectionResult {
        // Step 1: match by service UUID across ALL discovered services.
        for (service in gatt.services) {
            val uuid = service.uuid.toString()
            val byUuid = registry.getProtocolByServiceUuid(uuid)
            if (byUuid != null) {
                logger?.logEvent("BLE_PROTO", "UUID match: $uuid -> ${byUuid.type}")
                return DetectionResult(byUuid, null, probed = false)
            }
        }
        logger?.logEvent("BLE_PROTO", "No service UUID match; starting variant probing")

        // Step 2: sequential variant probes, verified by real parsing —
        // never by magic header bytes.
        val verified = AtomicBoolean(false)
        // Written by the probing loop, read on the BLE callback thread.
        val inflightFormat = AtomicReference<JbdFrameBuilder.FrameFormat?>(null)
        val signal = CompletableDeferred<DetectionResult>()
        var probingRan = false

        registerListener { rx ->
            if (rx.isNotEmpty() && !verified.get()) {
                val head = rx.take(8).joinToString(" ") { String.format("%02X", it) }
                logger?.logEvent("BLE_PROTO", "Probe response (${rx.size}B): $head…")
                val classified = classifyResponse(rx)
                if (classified != null) {
                    logger?.logEvent("BLE_PROTO", "Verified ${classified.type} via ${inflightFormat.get()}")
                    verified.set(true)
                    signal.complete(DetectionResult(classified, inflightFormat.get(), probed = true))
                } else {
                    logger?.logEvent("BLE_PROTO", "Response did not validate against known protocols")
                }
            }
        }

        try {
            for ((format, probe) in JbdFrameBuilder.buildBasicInfoProbes()) {
                if (verified.get()) break
                probingRan = true
                inflightFormat.set(format)
                logger?.logEvent("BLE_PROTO", "Probe $format: ${probe.joinToString(" ") { String.format("%02X", it) }}")
                writeChannel(probe)
                val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) { signal.await() }
                if (result != null) return result
                logger?.logEvent("BLE_PROTO", "Probe $format timed out")
            }
        } catch (e: Exception) {
            logger?.logEvent("APP_ERR", "Detection loop failed: ${e.message}")
        } finally {
            registerListener(null)
        }

        logger?.logEvent("BLE_PROTO", "No variant responded; falling back to XIAOXIANG default")
        val fallback = registry.getProtocol(ProtocolType.XIAOXIANG)!!
        return DetectionResult(fallback, null, probed = probingRan)
    }

    /** First registered protocol whose checksum + parse both accept [bytes]. */
    private fun classifyResponse(bytes: ByteArray): BmsProtocol? =
        registry.getAutoDetectionProtocols().firstOrNull { protocol ->
            runCatching { protocol.validatePacket(bytes) && protocol.parseBasicInfo(bytes) != null }
                .getOrDefault(false)
        }

    companion object {
        private const val PROBE_TIMEOUT_MS = 2500L
    }
}