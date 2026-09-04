package com.rangemate.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.rangemate.data.protocol.XiaoxiangProtocol
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
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var pollingJob: Job? = null
    private var readDeviceNameJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _bmsData = MutableStateFlow<XiaoxiangProtocol.BasicInfo?>(null)
    val bmsData: StateFlow<XiaoxiangProtocol.BasicInfo?> = _bmsData.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _connectedDeviceMac = MutableStateFlow<String?>(null)
    val connectedDeviceMac: StateFlow<String?> = _connectedDeviceMac.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val current = _scanResults.value.toMutableList()
            if (!current.any { it.device.address == result.device.address }) {
                current.add(result)
                _scanResults.value = current
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    pollingJob?.cancel()
                    readDeviceNameJob?.cancel()
                    _deviceName.value = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(BleConstants.BMS_SERVICE_UUID))
                val characteristic = service?.getCharacteristic(UUID.fromString(BleConstants.BMS_CHARACTERISTIC_UUID))

                characteristic?.let {
                    gatt.setCharacteristicNotification(it, true)
                    readDeviceName()
                    startPolling()
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            if (data != null) {
                val parsed = XiaoxiangProtocol.parseBasicInfo(data)
                if (parsed != null) {
                    _bmsData.value = parsed
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val parsedBasic = XiaoxiangProtocol.parseBasicInfo(value)
            if (parsedBasic != null) {
                _bmsData.value = parsedBasic
            } else {
                val parsedName = XiaoxiangProtocol.parseDeviceName(value)
                if (parsedName != null) {
                    _deviceName.value = parsedName
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Could track write success for device name command
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return
        }

        _scanResults.value = emptyList()
        _isScanning.value = true

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString(BleConstants.BMS_SERVICE_UUID)))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(filters, settings, scanCallback)

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
    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        _deviceName.value = device.name
        _connectedDeviceMac.value = device.address
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        pollingJob?.cancel()
        readDeviceNameJob?.cancel()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _bmsData.value = null
        _deviceName.value = null
        _connectedDeviceMac.value = null
    }

    @SuppressLint("MissingPermission")
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                val service = bluetoothGatt?.getService(UUID.fromString(BleConstants.BMS_SERVICE_UUID))
                val characteristic = service?.getCharacteristic(UUID.fromString(BleConstants.BMS_CHARACTERISTIC_UUID))

                characteristic?.let {
                    val command = XiaoxiangProtocol.buildReadCommand(BleConstants.CMD_READ_BASIC_INFO)
                    it.value = command
                    bluetoothGatt?.writeCharacteristic(it)
                }

                delay(BleConstants.BMS_UPDATE_INTERVAL_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readDeviceName() {
        readDeviceNameJob?.cancel()
        readDeviceNameJob = scope.launch {
            val service = bluetoothGatt?.getService(UUID.fromString(BleConstants.BMS_SERVICE_UUID))
            val characteristic = service?.getCharacteristic(UUID.fromString(BleConstants.BMS_CHARACTERISTIC_UUID))

            characteristic?.let {
                val command = XiaoxiangProtocol.buildReadCommand(BleConstants.CMD_READ_DEVICE_NAME)
                it.value = command
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}