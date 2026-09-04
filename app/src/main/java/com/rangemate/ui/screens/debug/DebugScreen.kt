package com.rangemate.ui.screens.debug

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rangemate.data.ble.BleManager
import com.rangemate.data.protocol.XiaoxiangProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel() {

    val connectionState = bleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleManager.ConnectionState.DISCONNECTED)

    val scanResults = bleManager.scanResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bmsData = bleManager.bmsData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deviceName = bleManager.deviceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var rawPackets = mutableListOf<String>()
    var parsedPackets = mutableListOf<String>()
    var servicesList = mutableListOf<String>()
    var characteristicsList = mutableListOf<String>()

    fun addRawPacket(data: ByteArray) {
        val hex = data.joinToString(" ") { String.format("%02X", it) }
        rawPackets.add(hex)
        if (rawPackets.size > 50) rawPackets.removeAt(0)
    }

    fun addParsedPacket(info: XiaoxiangProtocol.BasicInfo) {
        val str = "V: ${info.voltage}V, I: ${info.current}A, SOC: ${info.soc}%, Temp: ${info.temperature}°C, Cells: ${info.cellCount}, Cycles: ${info.cycles}, Cap: ${info.capacity}/${info.totalCapacity}Ah"
        parsedPackets.add(str)
        if (parsedPackets.size > 20) parsedPackets.removeAt(0)
    }

    fun updateServices(gatt: BluetoothGatt?) {
        servicesList.clear()
        characteristicsList.clear()
        gatt?.services?.forEach { service ->
            servicesList.add("Service: ${service.uuid}")
            service.characteristics.forEach { char ->
                characteristicsList.add("  Char: ${char.uuid} props=${char.properties} perms=${char.permissions}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBackClick: () -> Unit = {},
    viewModel: DebugViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val bmsData by viewModel.bmsData.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.addParsedPacket(bmsData!!) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Connection Status
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("State: ")
                        Text(
                            text = connectionState.name,
                            color = when (connectionState) {
                                BleManager.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                BleManager.ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            },
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Row {
                        Text("Device: ")
                        Text(text = deviceName ?: "N/A")
                    }
                }
            }

            // BMS Data
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BMS Data (Parsed)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    bmsData?.let { info ->
                        DebugRow("Voltage", "${info.voltage} V")
                        DebugRow("Current", "${info.current} A")
                        DebugRow("Power", "${info.voltage * info.current} W")
                        DebugRow("SOC", "${info.soc}%")
                        DebugRow("Capacity", "${info.capacity} / ${info.totalCapacity} Ah")
                        DebugRow("Cycles", "${info.cycles}")
                        DebugRow("Temperature", "${info.temperature} °C")
                        DebugRow("Cell Count", "${info.cellCount}")
                    } ?: Text("No data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Services
            if (viewModel.servicesList.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BLE Services", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(viewModel.servicesList) { service ->
                                Text(text = service, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Characteristics
            if (viewModel.characteristicsList.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Characteristics", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(viewModel.characteristicsList) { char ->
                                Text(text = char, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Raw Packets
            if (viewModel.rawPackets.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Raw Packets (last 50)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            itemsIndexed(viewModel.rawPackets) { index, packet ->
                                Text(text = "$index: $packet", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Parsed Packets
            if (viewModel.parsedPackets.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Parsed Packets (last 20)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            itemsIndexed(viewModel.parsedPackets) { index, packet ->
                                Text(text = "$index: $packet", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Scan Results
            if (scanResults.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Scan Results", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(scanResults) { result ->
                                Text(text = "${result.device.name ?: "Unknown"} - ${result.device.address} (RSSI: ${result.rssi})", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = value, fontFamily = FontFamily.Monospace)
    }
}