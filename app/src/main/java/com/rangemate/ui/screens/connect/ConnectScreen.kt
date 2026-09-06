package com.rangemate.ui.screens.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rangemate.data.ble.BleManager
import com.rangemate.data.protocol.ProtocolType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onDeviceConnected: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedMac by viewModel.connectedMac.collectAsStateWithLifecycle()
    val detectedProtocol by viewModel.detectedProtocol.collectAsStateWithLifecycle()
    val protocolDetectionState by viewModel.protocolDetectionState.collectAsStateWithLifecycle()
    val compatibleMap by viewModel.compatibleMacs.collectAsStateWithLifecycle()
    val filterOnly by viewModel.filterCompatibleOnly.collectAsStateWithLifecycle()

    var hasPermissions by remember { mutableStateOf(false) }
    var hasRecordedConnection by remember { mutableStateOf(false) }
    var selectedProtocol by remember { mutableStateOf<ProtocolType?>(null) }
    var showProtocolSelector by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            viewModel.startScan()
        }
    }

    LaunchedEffect(Unit) {
        val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            hasPermissions = true
            viewModel.startScan()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(connectionState, connectedMac) {
        val mac = connectedMac
        if (connectionState == BleManager.ConnectionState.CONNECTED && mac != null && !hasRecordedConnection) {
            hasRecordedConnection = true
            viewModel.onDeviceConnected(mac)
            onDeviceConnected()
        } else if (connectionState != BleManager.ConnectionState.CONNECTED) {
            hasRecordedConnection = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RangeMate") },
                actions = {
                    if (!showProtocolSelector) {
                        IconButton(onClick = { showProtocolSelector = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Protocol Settings")
                        }
                    }
                    IconButton(onClick = { if (hasPermissions) viewModel.startScan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProtocolSelector) {
                ProtocolSelector(
                    selectedProtocol = selectedProtocol,
                    onProtocolSelected = { protocol ->
                        selectedProtocol = protocol
                        showProtocolSelector = false
                        if (hasPermissions) {
                            viewModel.startScan()
                        }
                    },
                    onAutoSelected = {
                        selectedProtocol = null
                        showProtocolSelector = false
                        if (hasPermissions) {
                            viewModel.startScan()
                        }
                    }
                )
                Divider(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            when {
                !hasPermissions -> {
                    Text(
                        text = "Bluetooth permissions required",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                isScanning -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        Text("Scanning for BMS devices...")
                        if (protocolDetectionState != BleManager.DetectionState.IDLE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detecting protocol: ${protocolDetectionState.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                connectionState == BleManager.ConnectionState.CONNECTING -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        Text("Connecting...")
                        if (protocolDetectionState != BleManager.DetectionState.IDLE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Protocol: ${protocolDetectionState.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                scanResults.isEmpty() -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No devices found",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = { viewModel.startScan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Again")
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (detectedProtocol != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Detected Protocol",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = detectedProtocol?.displayName ?: "Unknown",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Found ${scanResults.size} device(s)",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Compatible BMS only",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = filterOnly,
                                onCheckedChange = { viewModel.toggleCompatibilityFilter() }
                            )
                        }
                        LazyColumn {
                            items(scanResults) { result ->
                                val isMatch = compatibleMap.containsKey(result.device.address)
                                if (!filterOnly || isMatch) {
                                    DeviceItem(
                                        scanResult = result,
                                        matchDetails = compatibleMap[result.device.address],
                                        onClick = { viewModel.connect(result.device, selectedProtocol) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolSelector(
    selectedProtocol: ProtocolType?,
    onProtocolSelected: (ProtocolType) -> Unit,
    onAutoSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BMS Protocol", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { /* already open */ }) {
                    Text("Select Protocol")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAutoSelected() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto Detect", style = MaterialTheme.typography.bodyLarge)
                    if (selectedProtocol == null) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Divider()

                ProtocolType.autoDetectionOrder.forEach { protocol ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProtocolSelected(protocol) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(protocol.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Service: ${protocol.serviceUuid.takeLast(4)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (selectedProtocol == protocol) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (protocol != ProtocolType.autoDetectionOrder.last()) Divider()
                }
            }
        }
    }
}

@Composable
@SuppressLint("MissingPermission")
private fun DeviceItem(
    scanResult: ScanResult,
    matchDetails: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = scanResult.device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = scanResult.device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (matchDetails != null) {
                    SuggestionChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = "Likely BMS · $matchDetails",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}