package com.rangemate.ui.screens.connect

import android.Manifest
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rangemate.data.ble.BleManager

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

    var hasPermissions by remember { mutableStateOf(false) }
    var hasRecordedConnection by remember { mutableStateOf(false) }

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
            when {
                !hasPermissions -> {
                    Text(
                        text = "Bluetooth permissions required",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                isScanning -> {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text("Scanning for BMS devices...")
                }
                connectionState == BleManager.ConnectionState.CONNECTING -> {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text("Connecting...")
                }
                scanResults.isEmpty() -> {
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
                else -> {
                    Text(
                        text = "Found ${scanResults.size} device(s)",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                    LazyColumn {
                        items(scanResults) { result ->
                            DeviceItem(
                                scanResult = result,
                                onClick = { viewModel.connect(result.device) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    scanResult: ScanResult,
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
            }
        }
    }
}
