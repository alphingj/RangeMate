package com.rangemate.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
        ) {
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
                        value = settings.maxPower,
                        onValueChange = { viewModel.updateMaxPower(it) },
                        valueRange = 100f..5000f,
                        steps = 49
                    )
                    Text(
                        text = String.format("%.0f W", settings.maxPower),
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
                        value = settings.fullRangeKm,
                        onValueChange = { viewModel.updateFullRange(it) },
                        valueRange = 10f..200f,
                        steps = 19
                    )
                    Text(
                        text = String.format("%.0f km", settings.fullRangeKm),
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
                            value = settings.greenZoneEnd.toString(),
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
                            value = settings.greenZoneEnd.toString(),
                            onValueChange = { },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            enabled = false
                        )
                        Text(" - ")
                        TextField(
                            value = settings.yellowZoneEnd.toString(),
                            onValueChange = { viewModel.updateYellowZone(it.toFloatOrNull() ?: 1500f) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text(" W)")
                    }
                }
            }

            // Distance Unit
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Distance Unit", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Kilometers")
                        Switch(
                            checked = settings.distanceUnit == "km",
                            onCheckedChange = { if (it) viewModel.updateDistanceUnit("km") }
                        )
                    }
                    Row {
                        Text("Miles")
                        Switch(
                            checked = settings.distanceUnit == "mi",
                            onCheckedChange = { if (it) viewModel.updateDistanceUnit("mi") }
                        )
                    }
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
                            value = settings.warningTempC.toString(),
                            onValueChange = { viewModel.updateWarningTemp(it.toFloatOrNull() ?: 45f) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text("°C")
                    }
                    Row {
                        Text("Critical: ")
                        TextField(
                            value = settings.criticalTempC.toString(),
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
                        checked = settings.showPowerGraph,
                        onCheckedChange = { viewModel.updateShowPowerGraph(it) }
                    )
                }
            }
        }
    }
}