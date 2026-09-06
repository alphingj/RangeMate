package com.rangemate.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.rangemate.data.log.RawPacketLogger
import com.rangemate.data.protocol.BmsProtocol
import com.rangemate.data.protocol.BasicInfo
import com.rangemate.data.protocol.JbdFrameBuilder
import com.rangemate.data.protocol.ProtocolRegistry
import com.rangemate.data.preferences.PreferencesManager
import com.rangemate.data.protocol.ProtocolAutoDetector
import com.rangemate.data.protocol.ProtocolType
import com.rangemate.data.protocol.auth.JbdAuthSequence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val protocolRegistry: ProtocolRegistry = ProtocolRegistry(),
    private val protocolAutoDetector: ProtocolAutoDetector = ProtocolAutoDetector(),
    private val logger: RawPacketLogger? = null,
    scanFilter: BmsScanFilter? = null,
    private val prefs: PreferencesManager? = null
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val bmsScanFilter: BmsScanFilter = scanFilter ?: BmsScanFilter(logger)

    private var bluetoothGatt: BluetoothGatt? = null
    private var pollingJob: Job? = null
    private var readDeviceNameJob: Job? = null
    private var detectionJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastDevice: BluetoothDevice? = null
    private var userInitiatedDisconnect = true
    private var reconnectAttempts = 0
    private var manualProtocol: ProtocolType? = null
    private var currentProtocol: BmsProtocol? = null
    private var preferredFrameFormat: JbdFrameBuilder.FrameFormat? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _compatibleMacs = MutableStateFlow<Map<String, String>>(emptyMap())
    val compatibleMacs: StateFlow<Map<String, String>> = _compatibleMacs.asStateFlow()

    private val _gattServices = MutableStateFlow<List<String>>(emptyList())
    val gattServices: StateFlow<List<String>> = _gattServices.asStateFlow()

    private val _gattCharacteristics = MutableStateFlow<List<String>>(emptyList())
    val gattCharacteristics: StateFlow<List<String>> = _gattCharacteristics.asStateFlow()

    private val _recentPackets = MutableStateFlow<List<String>>(emptyList())
    val recentPackets: StateFlow<List<String>> = _recentPackets.asStateFlow()

    private fun pushPacket(label: String, bytes: ByteArray) {
        val hex = bytes.joinToString(" ") { String.format("%02X", it) }
        _recentPackets.value = (_recentPackets.value + "$label: $hex").takeLast(MAX_RECENT_PACKETS)
    }

    /** One-shot hook letting ProtocolAutoDetector observe live RX frames (no DI cycle). */
    private var detectionFrameListener: ((ByteArray) -> Unit)? = null

    fun setDetectionListener(listener: ((ByteArray) -> Unit)?) {
        detectionFrameListener = listener
    }

    private val _authEnabled = MutableStateFlow(false)
    val authHandshakeEnabled: StateFlow<Boolean> = _authEnabled.asStateFlow()

    fun setAuthHandshakeEnabled(enabled: Boolean) {
        _authEnabled.value = enabled
        logger?.logEvent("BLE_AUTH", "Handshake opt-in ${if (enabled) "ENABLED" else "disabled"}")
    }

    // RX reassembly for DD..77 frames split across BLE notifications.
    private val rxStash = ArrayDeque<Byte>()
    private var rxStashStartMs = 0L

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _bmsData = MutableStateFlow<BasicInfo?>(null)
    val bmsData: StateFlow<BasicInfo?> = _bmsData.asStateFlow()

    private val _cellVoltages = MutableStateFlow<List<Float>>(emptyList())
    val cellVoltages: StateFlow<List<Float>> = _cellVoltages.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _connectedDeviceMac = MutableStateFlow<String?>(null)
    val connectedDeviceMac: StateFlow<String?> = _connectedDeviceMac.asStateFlow()

    private val _detectedProtocol = MutableStateFlow<ProtocolType?>(null)
    val detectedProtocol: StateFlow<ProtocolType?> = _detectedProtocol.asStateFlow()

    private val _protocolDetectionState = MutableStateFlow<DetectionState>(DetectionState.IDLE)
    val protocolDetectionState: StateFlow<DetectionState> = _protocolDetectionState.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val recordHex = result.scanRecord?.bytes
                ?.joinToString("") { String.format("%02X", it) } ?: "NO_BYTES"

            // Log every sighting in full — this is what diagnoses "scan finds nothing".
            logger?.logEvent(
                "BLE_SCAN",
                "Sighting: MAC=${device.address} Name=${device.name ?: "Unknown"} " +
                    "RSSI=${result.rssi}dBm Record=$recordHex"
            )

            // Tag-everything: evaluate but never hide.
            val match = bmsScanFilter.evaluate(result)
            if (match.isCompatible) {
                val display = match.details ?: match.reason.name
                if (_compatibleMacs.value[device.address] != display) {
                    _compatibleMacs.value = _compatibleMacs.value + (device.address to display)
                }
            }

            // Dedupe by MAC, refresh the entry so RSSI stays live.
            val current = _scanResults.value.toMutableList()
            current.removeAll { it.device.address == device.address }
            current.add(result)
            _scanResults.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            logger?.logEvent("BLE_SCAN", "Scan failed, error=$errorCode")
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            logger?.logEvent("BLE_CONN", "State change: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectionTimeoutJob?.cancel()
                    reconnectAttempts = 0
                    _connectionState.value = ConnectionState.CONNECTED
                    // Negotiate past the 23-byte default ATT MTU: 0x03 responses
                    // run ~34-40 bytes and arrive truncated otherwise. Non-fatal.
                    runCatching { gatt.requestMtu(RX_MTU) }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    pollingJob?.cancel()
                    readDeviceNameJob?.cancel()
                    detectionJob?.cancel()
                    setDetectionListener(null)
                    _deviceName.value = null
                    currentProtocol = null
                    preferredFrameFormat = null
                    _detectedProtocol.value = null
                    _protocolDetectionState.value = DetectionState.IDLE
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _gattServices.value = gatt.services.map { it.uuid.toString() }
                _gattCharacteristics.value = gatt.services.flatMap { service ->
                    service.characteristics.map { char ->
                        "${char.uuid} props=${char.properties}"
                    }
                }
                gatt.services.forEach { service ->
                    val chars = service.characteristics.joinToString(", ") { it.uuid.toString().take(8) }
                    logger?.logEvent("BLE_GATT", "Service ${service.uuid} -> [$chars]")
                }
                _protocolDetectionState.value = DetectionState.DETECTING
                detectionJob?.cancel()
                detectionJob = scope.launch {
                    val manual = manualProtocol?.let { protocolRegistry.getProtocol(it) }
                    if (manual != null) {
                        logger?.logEvent("BLE_PROTO", "Manual override: ${manual.type}, skipping auto-detect")
                        currentProtocol = manual
                        preferredFrameFormat = null
                        _detectedProtocol.value = manual.type
                        _protocolDetectionState.value = DetectionState.SUCCESS
                        onProtocolDetected(gatt, manual, null)
                        return@launch
                    }
                    val characteristic = getCharacteristic(gatt)
                    if (characteristic != null) {
                        var result = protocolAutoDetector.detectProtocol(
                            gatt,
                            registerListener = ::setDetectionListener,
                            writeChannel = { bytes -> writeCommand(gatt, characteristic, bytes, "detect") }
                        )
                        // Silent probes + explicit opt-in -> try the 0xFF handshake once,
                        // then interrogate again.
                        if (result.probed &&
                            result.frameFormat == null &&
                            _authEnabled.value
                        ) {
                            logger?.logEvent("BLE_PROTO", "Probes silent + auth opt-in -> attempting 0xFF handshake")
                            val authed = JbdAuthSequence(logger).runAuth(
                                registerListener = ::setDetectionListener,
                                writeChannel = { bytes -> writeCommand(gatt, characteristic, bytes, "auth") },
                                deviceMac = gatt.device.address
                            )
                            if (authed) {
                                result = protocolAutoDetector.detectProtocol(
                                    gatt,
                                    registerListener = ::setDetectionListener,
                                    writeChannel = { bytes -> writeCommand(gatt, characteristic, bytes, "detect") }
                                )
                            }
                        }
                        currentProtocol = result.protocol
                        preferredFrameFormat = result.frameFormat
                        _detectedProtocol.value = result.protocol.type
                        _protocolDetectionState.value = DetectionState.SUCCESS
                        logger?.logEvent(
                            "BLE_PROTO",
                            "Active protocol: ${result.protocol.type} format=${result.frameFormat ?: "default"}"
                        )
                        onProtocolDetected(gatt, result.protocol, result.frameFormat)
                    } else {
                        _protocolDetectionState.value = DetectionState.FAILED
                        // Fallback to Xiaoxiang
                        currentProtocol = protocolRegistry.getProtocol(ProtocolType.XIAOXIANG)
                        _detectedProtocol.value = ProtocolType.XIAOXIANG
                        _protocolDetectionState.value = DetectionState.FALLBACK
                        onProtocolDetected(gatt, currentProtocol!!, null)
                    }
                }
            } else {
                logger?.logEvent("BLE_GATT", "Service discovery failed, status=$status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            onNotificationChunk(data, characteristic.uuid.toString().take(8))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onNotificationChunk(value, characteristic.uuid.toString().take(8))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            logger?.logEvent(
                "BLE_GATT",
                "Write status=$status char=${characteristic.uuid.toString().take(8)}"
            )
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            logger?.logEvent("BLE_GATT", "MTU result: mtu=$mtu status=$status")
        }

        private fun onNotificationChunk(chunk: ByteArray, tag: String) {
            logger?.logPacket("RX", chunk, tag)
            pushPacket("RX", chunk)
            for (frame in extractFrames(chunk)) {
                detectionFrameListener?.invoke(frame)
                currentProtocol?.let { protocol ->
                    val parsedBasic = protocol.parseBasicInfo(frame)
                    if (parsedBasic != null) {
                        _bmsData.value = parsedBasic
                    } else {
                        val cells = protocol.parseCellVoltages(frame)
                        if (cells != null) {
                            _cellVoltages.value = cells
                        } else {
                            protocol.parseDeviceName(frame)?.let { _deviceName.value = it }
                        }
                    }
                }
            }
        }
    }

    /**
     * Splits the notification stream into complete frames.
     *
     * DD..77 frames assemble across notifications using the length byte;
     * anything else passes through untouched so the legacy A5 path behaves
     * exactly as before (zero regression risk there).
     */
    private fun extractFrames(chunk: ByteArray): List<ByteArray> {
        val now = System.currentTimeMillis()
        if (rxStash.isEmpty()) {
            if (chunk.isNotEmpty() && chunk[0] != 0xDD.toByte()) return listOf(chunk)
            rxStashStartMs = now
        } else if (now - rxStashStartMs > RX_ASSEMBLY_TIMEOUT_MS) {
            logger?.logEvent("BLE_GATT", "RX assembly timeout, dropping ${rxStash.size}B")
            rxStash.clear()
            rxStashStartMs = now
            if (chunk.isNotEmpty() && chunk[0] != 0xDD.toByte()) return listOf(chunk)
        }
        chunk.forEach { rxStash.add(it) }
        if (rxStash.size > RX_STASH_CAP) {
            logger?.logEvent("BLE_GATT", "RX stash overrun, dropping ${rxStash.size}B")
            rxStash.clear()
            return emptyList()
        }
        val frames = mutableListOf<ByteArray>()
        while (true) {
            while (rxStash.isNotEmpty() && rxStash.first() != 0xDD.toByte()) {
                rxStash.removeFirst()
            }
            if (rxStash.size < DD_MIN_FRAME) break
            val len = rxStash[3].toInt() and 0xFF
            val total = len + 7
            if (rxStash.size < total) break
            if (rxStash[total - 1] != 0x77.toByte()) {
                logger?.logEvent("BLE_GATT", "RX framing error, resyncing")
                rxStash.removeFirst()
                continue
            }
            frames.add(ByteArray(total) { rxStash[it] })
            repeat(total) { rxStash.removeFirst() }
        }
        if (rxStash.isEmpty()) rxStashStartMs = 0L
        if (frames.size > 1 || (frames.size == 1 && chunk.size < frames[0].size)) {
            logger?.logEvent("BLE_GATT", "Assembled ${frames.size} frame(s) from stream")
        }
        return frames
    }

    /** Single write path so every TX frame is logged with its purpose tag. */
    @SuppressLint("MissingPermission")
    private fun writeCommand(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        bytes: ByteArray,
        tag: String
    ): Boolean {
        logger?.logPacket("TX", bytes, tag)
        pushPacket("TX[$tag]", bytes)
        characteristic.value = bytes
        return gatt.writeCharacteristic(characteristic)
    }

    /**
     * Resolves the notify/write characteristic across all known service pairs.
     * UUID and payload layout can cross (e.g. FFE0 service speaking DD frames),
     * so the detected protocol's pair is tried first, then every known pair.
     */
    private fun getCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val preferred = currentProtocol ?: protocolRegistry.getProtocol(ProtocolType.XIAOXIANG)!!
        val ordered = linkedMapOf<String, String>()
        ordered[preferred.serviceUuid] = preferred.characteristicUuid
        protocolRegistry.getAllProtocols().forEach { ordered[it.serviceUuid] = it.characteristicUuid }
        for ((svc, chr) in ordered) {
            try {
                val service = gatt.getService(UUID.fromString(svc)) ?: continue
                val characteristic = service.getCharacteristic(UUID.fromString(chr)) ?: continue
                if (svc != preferred.serviceUuid) {
                    logger?.logEvent("BLE_GATT", "Characteristic fallback in use: $svc / $chr")
                }
                return characteristic
            } catch (e: Exception) {
                logger?.logEvent("BLE_GATT", "Characteristic lookup failed for $svc: ${e.message}")
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun onProtocolDetected(
        gatt: BluetoothGatt,
        protocol: BmsProtocol,
        frameFormat: JbdFrameBuilder.FrameFormat?
    ) {
        val characteristic = getCharacteristic(gatt)
        characteristic?.let {
            // Enable notifications on the characteristic
            gatt.setCharacteristicNotification(it, true)
            
            // Also write to CCCD descriptor (0x2902) - required for some BMS
            val cccd = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            cccd?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
            
            readDeviceName(protocol)
            startPolling(protocol)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return
        }

        _scanResults.value = emptyList()
        _compatibleMacs.value = emptyMap()
        _isScanning.value = true
        logger?.logEvent("BLE_SCAN", "Starting filterless low-latency scan (software matching)")

        // Single wide-open scan: JBD-family boards often advertise no service UUID,
        // so hardware filters are useless. BmsScanFilter evaluates every sighting.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(emptyList(), settings, scanCallback)

        scope.launch {
            delay(BleConstants.SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        bleScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, protocolType: ProtocolType? = null) {
        stopScan()
        lastDevice = device
        userInitiatedDisconnect = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        setManualProtocol(protocolType)
        _connectionState.value = ConnectionState.CONNECTING
        _deviceName.value = device.name
        _connectedDeviceMac.value = device.address
        logger?.logEvent("BLE_CONN", "Connecting to ${device.address} (protocol: ${protocolType?.name ?: "auto"})")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        armConnectionTimeout()
    }

    fun setManualProtocol(type: ProtocolType?) {
        manualProtocol = type
    }

    private fun armConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(BleConstants.CONNECTION_TIMEOUT_MS)
            if (_connectionState.value == ConnectionState.CONNECTING) {
                logger?.logEvent("BLE_CONN", "Connect timed out after ${BleConstants.CONNECTION_TIMEOUT_MS}ms")
                internalDisconnect(userInitiated = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        internalDisconnect(userInitiated = true)
    }

    @SuppressLint("MissingPermission")
    private fun internalDisconnect(userInitiated: Boolean) {
        userInitiatedDisconnect = userInitiated
        pollingJob?.cancel()
        readDeviceNameJob?.cancel()
        detectionJob?.cancel()
        connectionTimeoutJob?.cancel()
        reconnectJob?.cancel()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _bmsData.value = null
        _cellVoltages.value = emptyList()
        _recentPackets.value = emptyList()
        _deviceName.value = null
        _connectedDeviceMac.value = null
        currentProtocol = null
        _detectedProtocol.value = null
        _protocolDetectionState.value = DetectionState.IDLE
        if (!userInitiated) scheduleReconnect()
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        val device = lastDevice ?: return
        val snapshot = prefs?.globalSnapshot() ?: return
        if (!snapshot.autoReconnectEnabled) return
        if (snapshot.lastConnectedDeviceMac != null && snapshot.lastConnectedDeviceMac != device.address) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            logger?.logEvent("BLE_CONN", "Auto-reconnect exhausted ($MAX_RECONNECT_ATTEMPTS attempts)")
            return
        }
        reconnectAttempts++
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_connectionState.value == ConnectionState.DISCONNECTED && !userInitiatedDisconnect) {
                logger?.logEvent("BLE_CONN", "Auto-reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS")
                _connectionState.value = ConnectionState.CONNECTING
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                armConnectionTimeout()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPolling(protocol: BmsProtocol) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var pollCells = false
            while (true) {
                val gatt = bluetoothGatt ?: return@launch
                val characteristic = getCharacteristic(gatt)

                characteristic?.let { characteristic ->
                    // Alternate basic-info and cell-voltage polls so both stay live.
                    // Uses the wire format that earned a detection response, when known.
                    val format = preferredFrameFormat
                    if (!pollCells) {
                        val command = format
                            ?.let { JbdFrameBuilder.buildBasicInfoCommand(it) }
                            ?: protocol.buildBasicInfoCommand()
                        writeCommand(gatt, characteristic, command, "poll_basic")
                    } else {
                        val command = format
                            ?.let { JbdFrameBuilder.buildReadCommand(protocol.type.cellVoltageCommand, it) }
                            ?: protocol.buildCellVoltageCommand()
                        writeCommand(gatt, characteristic, command, "poll_cells")
                    }
                    pollCells = !pollCells
                }

                delay(BleConstants.BMS_UPDATE_INTERVAL_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readDeviceName(protocol: BmsProtocol) {
        readDeviceNameJob?.cancel()
        readDeviceNameJob = scope.launch {
            val gatt = bluetoothGatt ?: return@launch
            val characteristic = getCharacteristic(gatt)

            characteristic?.let {
                writeCommand(gatt, it, protocol.buildDeviceNameCommand(), "device_name")
            }
        }
    }

    companion object {
        private const val RX_MTU = 247
        private const val DD_MIN_FRAME = 7
        private const val RX_ASSEMBLY_TIMEOUT_MS = 2000L
        private const val RX_STASH_CAP = 512
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECENT_PACKETS = 50
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    enum class DetectionState {
        IDLE,
        DETECTING,
        SUCCESS,
        FALLBACK,
        FAILED
    }
}