package com.rangemate.data.protocol.auth

import com.rangemate.data.log.RawPacketLogger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opt-in JBD 0xFF challenge-response handshake (open_battery §1.1).
 *
 * Attempt ONLY when DD probes earn zero response: some firmware gates standard
 * commands behind auth. Wrong credentials simply fail — but this stays behind
 * an explicit Debug toggle (default OFF) regardless.
 *
 * Framing note: the spec fixes the 8-bit auth checksum (sum of CMD+LEN+DATA)
 * but leaves the packet envelope implicit; FF AA CMD LEN DATA CHK 77 is used
 * here and every step is logged, so the field capture shows exactly what the
 * BMS accepts. TODO(field log): confirm the envelope + password derivation.
 */
class JbdAuthSequence(
    private val logger: RawPacketLogger? = null
) {
    /**
     * Runs the full handshake. Returns true only if every step is acknowledged.
     * Response routing mirrors ProtocolAutoDetector (listener injection, no DI
     * cycle): one in-flight signal at a time, completed by the live RX path.
     */
    suspend fun runAuth(
        registerListener: (((ByteArray) -> Unit)?) -> Unit,
        writeChannel: (ByteArray) -> Boolean,
        deviceMac: String
    ): Boolean {
        logger?.logEvent("BLE_AUTH", "Starting 0xFF handshake (opt-in)")
        val inflight = AtomicReference<CompletableDeferred<ByteArray>?>(null)

        registerListener { rx ->
            val pending = inflight.get()
            if (rx.isNotEmpty() && pending != null && !pending.isCompleted) {
                logger?.logPacket("RX-AUTH", rx, "auth")
                pending.complete(rx)
            }
        }

        try {
            // Step 1: App Key "000000" -> expect 0x00 OK / password-required.
            if (awaitStep(writeChannel, inflight, "AppKey", authFrame(CMD_APP_KEY, DEFAULT_APP_KEY)) == null) return false
            // Step 2: request random challenge.
            val challenge = awaitStep(writeChannel, inflight, "Random", authFrame(CMD_RANDOM, ByteArray(0)))
                ?: return false
            val random = challenge.lastOrNull() ?: 0x00.toByte()
            // Step 3: password = XOR(pwd, mac, random), best-effort derivation.
            // TODO(field log): confirm derivation against a working capture.
            if (awaitStep(writeChannel, inflight, "Password", authFrame(CMD_PASSWORD, derivePassword(deviceMac, random))) == null) return false
            // Step 4: root password.
            if (awaitStep(writeChannel, inflight, "Root", authFrame(CMD_ROOT, DEFAULT_APP_KEY)) == null) return false

            logger?.logEvent("BLE_AUTH", "Handshake complete")
            return true
        } catch (e: Exception) {
            logger?.logEvent("APP_ERR", "Auth sequence failed: ${e.message}")
            return false
        } finally {
            registerListener(null)
        }
    }

    private suspend fun awaitStep(
        writeChannel: (ByteArray) -> Boolean,
        inflight: AtomicReference<CompletableDeferred<ByteArray>?>,
        name: String,
        frame: ByteArray
    ): ByteArray? {
        val signal = CompletableDeferred<ByteArray>()
        inflight.set(signal)
        logger?.logEvent("BLE_AUTH", "TX $name: ${frame.joinToString(" ") { String.format("%02X", it) }}")
        writeChannel(frame)
        return withTimeoutOrNull(STEP_TIMEOUT_MS) { signal.await() }.also {
            if (it == null) logger?.logEvent("BLE_AUTH", "$name timed out")
        }
    }

    /**
     * Best-effort password derivation per spec text (XOR of password, MAC and
     * challenge), 6 bytes. TODO(field log): confirm against working capture.
     */
    private fun derivePassword(deviceMac: String, random: Byte): ByteArray {
        val pwd = DEFAULT_APP_KEY
        val macBytes = deviceMac.split(":", "-")
            .mapNotNull { it.toIntOrNull(16)?.toByte() }
            .toByteArray()
        return ByteArray(pwd.size) { i ->
            val mac = if (macBytes.isNotEmpty()) macBytes[i % macBytes.size] else 0x00.toByte()
            (pwd[i].toInt() xor mac.toInt() xor random.toInt()).toByte()
        }
    }

    private fun authFrame(cmd: Byte, data: ByteArray): ByteArray {
        val out = ByteArray(6 + data.size)
        out[0] = 0xFF.toByte()
        out[1] = 0xAA.toByte()
        out[2] = cmd
        out[3] = data.size.toByte()
        data.copyInto(out, 4)
        var sum = (cmd.toInt() and 0xFF) + data.size
        data.forEach { sum += it.toInt() and 0xFF }
        out[4 + data.size] = (sum and 0xFF).toByte()
        out[5 + data.size] = 0x77
        return out
    }

    companion object {
        private const val CMD_APP_KEY: Byte = 0x15
        private const val CMD_RANDOM: Byte = 0x17
        private const val CMD_PASSWORD: Byte = 0x18
        private const val CMD_ROOT: Byte = 0x1D
        private val DEFAULT_APP_KEY = "000000".toByteArray(Charsets.US_ASCII)
        private const val STEP_TIMEOUT_MS = 2000L
    }
}
