package com.rangemate.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val deviceSettings by viewModel.deviceSettings.collectAsStateWithLifecycle()
    val globalSettings by viewModel.globalSettings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // =====================================================================
            // DEVICE SETTINGS (per-scooter)
            // =====================================================================
            Text("Scooter Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // BMS Protocol Selection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BMS Protocol", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Detected: ${deviceSettings.detectedProtocol?.ifBlank { "Auto" } ?: "Auto"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Protocol is always auto-detected at connect time; a manual
                    // override can be picked on the Connect screen per scan.
                    Text(
                        text = "Selection: Automatic",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Max Power setting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Max Power", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = deviceSettings.maxPower,
                        onValueChange = { viewModel.updateMaxPower(it) },
                        valueRange = 100f..5000f,
                        steps = 49
                    )
                    Text(
                        text = String.format(java.util.Locale.US,"%.0f W", deviceSettings.maxPower),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Full Range setting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Full Range", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = deviceSettings.fullRangeKm,
                        onValueChange = { viewModel.updateFullRange(it) },
                        valueRange = 10f..200f,
                        steps = 19
                    )
                    Text(
                        text = String.format(java.util.Locale.US,"%.0f km", deviceSettings.fullRangeKm),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Power Zone settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Power Zones", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        Text("Green Zone (0 - ")
                        TextField(
                            value = deviceSettings.greenZoneEnd.toString(),
                            onValueChange = { viewModel.updateGreenZone(it.toFloatOrNull() ?: 1000f) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text(" W)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Yellow Zone (")
                        TextField(
                            value = deviceSettings.greenZoneEnd.toString(),
                            onValueChange = { },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            enabled = false
                        )
                        Text(" - ")
                        TextField(
                            value = deviceSettings.yellowZoneEnd.toString(),
                            onValueChange = { viewModel.updateYellowZone(it.toFloatOrNull() ?: 1500f) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text(" W)")
                    }
                }
            }

            // Distance Unit (single exclusive switch)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Imperial units (miles)", style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = deviceSettings.distanceUnit == "mi",
                        onCheckedChange = { viewModel.updateDistanceUnit(if (it) "mi" else "km") }
                    )
                }
            }

            // Temperature Thresholds
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Temperature Thresholds", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Warning: ")
                        TextField(
                            value = deviceSettings.warningTempC.toString(),
                            onValueChange = { viewModel.updateWarningTemp(it.toFloatOrNull() ?: 45f) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text("°C")
                    }
                    Row {
                        Text("Critical: ")
                        TextField(
                            value = deviceSettings.criticalTempC.toString(),
                            onValueChange = { viewModel.updateCriticalTemp(it.toFloatOrNull() ?: 55f) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text("°C")
                    }
                }
            }

            // Show Power Graph toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Show Power Graph", style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = deviceSettings.showPowerGraph,
                        onCheckedChange = { viewModel.updateShowPowerGraph(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =====================================================================
            // GLOBAL SETTINGS (app-wide)
            // =====================================================================
            Text("App Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Theme
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Dark")
                        RadioButton(
                            selected = globalSettings.themeMode == "DARK",
                            onClick = { viewModel.updateThemeMode("DARK") }
                        )
                    }
                    Row {
                        Text("Light")
                        RadioButton(
                            selected = globalSettings.themeMode == "LIGHT",
                            onClick = { viewModel.updateThemeMode("LIGHT") }
                        )
                    }
                    Row {
                        Text("Auto (System)")
                        RadioButton(
                            selected = globalSettings.themeMode == "AUTO",
                            onClick = { viewModel.updateThemeMode("AUTO") }
                        )
                    }
                }
            }

            // Auto Reconnect
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Auto Reconnect", style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = globalSettings.autoReconnectEnabled,
                        onCheckedChange = { viewModel.updateAutoReconnect(it) }
                    )
                }
            }

            // Show Metrics Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Show Metrics Row", style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = globalSettings.showMetricsRow,
                        onCheckedChange = { viewModel.updateShowMetricsRow(it) }
                    )
                }
            }

            // Show Speed Top
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Show Speed at Top", style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = globalSettings.showSpeedTop,
                        onCheckedChange = { viewModel.updateShowSpeedTop(it) }
                    )
                }
            }
        }
    }
}