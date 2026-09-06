package com.rangemate.ui.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusBar(
    connected: Boolean,
    deviceName: String,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onDebugClick: () -> Unit = {}
) {
    // Ticking clock (30 s cadence avoids recomposing the bar every second).
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Time
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs)),
                    style = MaterialTheme.typography.titleMedium
                )

                // Connection status
                Icon(
                    imageVector = if (connected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (connected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        },
        actions = {
            if (connected) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = onDebugClick) {
                Icon(Icons.Default.Build, contentDescription = "Debug")
            }
        }
    )
}