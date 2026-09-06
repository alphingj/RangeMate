package com.rangemate.ui.screens.debug
import android.annotation.SuppressLint

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import com.rangemate.BuildConfig
import com.rangemate.data.ble.BleManager
import com.rangemate.data.log.RawPacketLogger
import com.rangemate.data.log.TelegramLogUploader
import com.rangemate.data.protocol.BasicInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val logger: RawPacketLogger,
    private val uploader: TelegramLogUploader
) : ViewModel() {

    val connectionState = bleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleManager.ConnectionState.DISCONNECTED)

    val scanResults = bleManager.scanResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bmsData = bleManager.bmsData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deviceName = bleManager.deviceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Live BLE inspection flows (observable — mutations recompose).
    val rawPackets: StateFlow<List<String>> = bleManager.recentPackets
    val servicesList: StateFlow<List<String>> = bleManager.gattServices
    val characteristicsList: StateFlow<List<String>> = bleManager.gattCharacteristics

    private val _parsedPackets = MutableStateFlow<List<String>>(emptyList())
    val parsedPackets: StateFlow<List<String>> = _parsedPackets.asStateFlow()

    val cellVoltages: StateFlow<List<Float>> = bleManager.cellVoltages

    // Packet-logging session state (field capture for remote diagnosis).
    val isLogging: StateFlow<Boolean> = logger.isLogging
    val logEntries: StateFlow<Long> = logger.entryCount
    val logFileSize: StateFlow<Long> = logger.fileSizeBytes

    private val _logElapsedMs = MutableStateFlow(0L)
    val logElapsedMs: StateFlow<Long> = _logElapsedMs.asStateFlow()

    private val _lastLogFile = MutableStateFlow<File?>(null)
    val lastLogFile: StateFlow<File?> = _lastLogFile.asStateFlow()

    sealed interface TelegramState {
        data object Idle : TelegramState
        data object Sending : TelegramState
        data object Sent : TelegramState
        data class Failed(val message: String) : TelegramState
    }

    private val _telegramState = MutableStateFlow<TelegramState>(TelegramState.Idle)
    val telegramState: StateFlow<TelegramState> = _telegramState.asStateFlow()

    val telegramAvailable: Boolean = uploader.isConfigured

    val authEnabled: StateFlow<Boolean> = bleManager.authHandshakeEnabled

    fun setAuthEnabled(enabled: Boolean) = bleManager.setAuthHandshakeEnabled(enabled)

    fun startLogging() {
        logger.startLogging()
        _logElapsedMs.value = 0L
        _telegramState.value = TelegramState.Idle
        viewModelScope.launch {
            val start = logger.sessionStartMs()
            while (logger.isLogging.value) {
                _logElapsedMs.value = System.currentTimeMillis() - start
                delay(1000)
            }
        }
    }

    /** Stops the session, then auto-uploads to Telegram when configured. */
    fun stopLogging() {
        viewModelScope.launch {
            val file = logger.stopLogging()
            _lastLogFile.value = file
            if (file != null) {
                sendViaTelegram(file)
            }
        }
    }

    fun resendLastLog() {
        _lastLogFile.value?.let { file ->
            viewModelScope.launch { sendViaTelegram(file) }
        }
    }

    fun shareLastFile(context: android.content.Context) {
        _lastLogFile.value?.let { file ->
            logger.shareLogFile(context, file)
        }
    }

    private suspend fun sendViaTelegram(file: File) {
        if (!uploader.isConfigured) return
        _telegramState.value = TelegramState.Sending
        val caption = buildCaption(file)
        _telegramState.value = when (val result = uploader.uploadLog(file, caption)) {
            is TelegramLogUploader.UploadResult.Sent -> TelegramState.Sent
            is TelegramLogUploader.UploadResult.Failed -> TelegramState.Failed(result.message)
        }
    }

    private fun buildCaption(file: File): String {
        val secs = (logElapsedMs.value / 1000).toInt()
        val elapsed = String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60)
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val start = fmt.format(Date(logger.sessionStartMs()))
        val end = fmt.format(Date())
        return "🧪 RangeMate log\n" +
            "📱 ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
            "📦 v${BuildConfig.APP_VERSION_NAME} · session $elapsed · ${logEntries.value} lines · ${logFileSize.value / 1024} KB\n" +
            "⏱ $start → $end"
    }

    fun addParsedPacket(info: BasicInfo) {
        val str = "V: ${info.voltage}V, I: ${info.current}A, SOC: ${info.soc}%, Temp: ${info.temperature}°C, Cells: ${info.cellCount}, Cycles: ${info.cycles}, Cap: ${info.capacity}/${info.totalCapacity}Ah"
        _parsedPackets.value = (_parsedPackets.value + str).takeLast(20)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBackClick: () -> Unit = {},
    viewModel: DebugViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val bmsData by viewModel.bmsData.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val isLogging by viewModel.isLogging.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
    val logFileSize by viewModel.logFileSize.collectAsStateWithLifecycle()
    val logElapsedMs by viewModel.logElapsedMs.collectAsStateWithLifecycle()
    val telegramState by viewModel.telegramState.collectAsStateWithLifecycle()
    val hasLastLog by viewModel.lastLogFile.collectAsStateWithLifecycle()
    val authEnabled by viewModel.authEnabled.collectAsStateWithLifecycle()
    val servicesList by viewModel.servicesList.collectAsStateWithLifecycle()
    val characteristicsList by viewModel.characteristicsList.collectAsStateWithLifecycle()
    val rawPackets by viewModel.rawPackets.collectAsStateWithLifecycle()
    val parsedPackets by viewModel.parsedPackets.collectAsStateWithLifecycle()
    val cellVoltages by viewModel.cellVoltages.collectAsStateWithLifecycle()

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
                    IconButton(onClick = { bmsData?.let { viewModel.addParsedPacket(it) } }) {
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
            // Packet Logging (field capture for remote diagnosis)
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Packet Logging", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isLogging) {
                        val seconds = (logElapsedMs / 1000).toInt()
                        val elapsed = String.format(java.util.Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                        Text(
                            text = "● Recording  $elapsed  ·  $logEntries lines  ·  ${logFileSize / 1024} KB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.stopLogging() }) {
                            Text("Stop")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keep logging through scan → connect → key cycle, then stop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Button(onClick = { viewModel.startLogging() }) {
                            Text("Start Log")
                        }
                    }
                    if (!isLogging && viewModel.telegramAvailable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        when (val state = telegramState) {
                            is DebugViewModel.TelegramState.Sending -> {
                                Text(
                                    text = "⇪ Sending log to Telegram…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is DebugViewModel.TelegramState.Sent -> {
                                Text(
                                    text = "✓ Log delivered to Telegram",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            is DebugViewModel.TelegramState.Failed -> {
                                Text(
                                    text = "✕ Upload failed: ${state.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { viewModel.resendLastLog() }) {
                                    Text("Tap to resend")
                                }
                            }
                            is DebugViewModel.TelegramState.Idle -> {}
                        }
                        if (hasLastLog != null) {
                            TextButton(onClick = { viewModel.shareLastFile(context) }) {
                                Text("Share file manually…")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "On Stop, logs upload automatically to the developer's Telegram (BT names/MACs included).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // JBD auth handshake (opt-in, off by default)
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("JBD Auth Handshake (0xFF)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attempt if probes are silent",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = authEnabled,
                            onCheckedChange = { viewModel.setAuthEnabled(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sends default-key challenge-response frames after failed detection. Every step is logged; wrong credentials simply fail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

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
                        if (cellVoltages.isNotEmpty()) {
                            DebugRow(
                                "Cells",
                                cellVoltages.joinToString(" ") {
                                    String.format(java.util.Locale.US, "%.3f", it)
                                }
                            )
                        }
                        val mosStates = listOfNotNull(
                            "charge".takeIf { info.fetStatus and 0x01 != 0 },
                            "discharge".takeIf { info.fetStatus and 0x02 != 0 }
                        )
                        DebugRow("MOS", mosStates.ifEmpty { listOf("off") }.joinToString("+"))
                        if (info.protectionStatus != 0) {
                            val faults = com.rangemate.data.protocol.common.ProtectionFlags.labels(info.protectionStatus)
                            DebugRow(
                                "Protection",
                                "0x${Integer.toHexString(info.protectionStatus)}: " +
                                    (faults.ifEmpty { listOf("unknown") }.joinToString(", "))
                            )
                            if (com.rangemate.data.protocol.common.ProtectionFlags.isPowerOffImminent(info.protectionStatus)) {
                                Text(
                                    text = "⚠ Shutdown-imminent fault active",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            DebugRow("Protection", "OK")
                        }
                    } ?: Text("No data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Services
            if (servicesList.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BLE Services", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(servicesList) { service ->
                                Text(text = service, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Characteristics
            if (characteristicsList.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Characteristics", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(characteristicsList) { char ->
                                Text(text = char, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Raw Packets
            if (rawPackets.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Raw Packets (last 50)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            itemsIndexed(rawPackets) { index, packet ->
                                Text(text = "$index: $packet", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Parsed Packets
            if (parsedPackets.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Parsed Packets (last 20)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            itemsIndexed(parsedPackets) { index, packet ->
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
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            @SuppressLint("MissingPermission")
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