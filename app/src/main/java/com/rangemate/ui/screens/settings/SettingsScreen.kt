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
import com.rangemate.data.preferences.DevicePreferences
import com.rangemate.data.preferences.GlobalPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val deviceSettings by viewModel.globalSettings.collectAsStateWithLifecycle()
        .let { it } // placeholder - we'll use device settings from navigation args
    val globalSettings by viewModel.globalSettings.collectAsStateWithLifecycle()

    // Get device settings from navigation args if available
    // For now, we'll use a combined approach
    val isDeviceConnected = true // This would come from navigation

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
            // =====================================================================
            // DEVICE SETTINGS (per-scooter)
            // =====================================================================
            if (isDeviceConnected) {
                Text("Scooter Settings", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                // Max Power setting
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Max Power", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        // We'd need to pass device settings - for now show global as fallback
                        DeviceSettingsSection(viewModel = viewModel)
                    }
                }
            }

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

            // Accent Color
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Color picker would go here
                    Text("Custom color picker TBD", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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

            // Dashboard Layout
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dashboard Layout", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Full")
                        RadioButton(
                            selected = globalSettings.dashboardLayout == "FULL",
                            onClick = { viewModel.updateDashboardLayout("FULL") }
                        )
                    }
                    Row {
                        Text("Compact")
                        RadioButton(
                            selected = globalSettings.dashboardLayout == "COMPACT",
                            onClick = { viewModel.updateDashboardLayout("COMPACT") }
                        )
                    }
                }
            }

            // Panel Content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Left Panel", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Battery & Range")
                        RadioButton(
                            selected = globalSettings.leftPanelContent == "BATTERY_RANGE",
                            onClick = { viewModel.updatePanelContent("BATTERY_RANGE", globalSettings.rightPanelContent) }
                        )
                    }
                    Row {
                        Text("Power Sweep")
                        RadioButton(
                            selected = globalSettings.leftPanelContent == "POWER_SWEEP",
                            onClick = { viewModel.updatePanelContent("POWER_SWEEP", globalSettings.rightPanelContent) }
                        )
                    }
                    Row {
                        Text("Power Graph")
                        RadioButton(
                            selected = globalSettings.leftPanelContent == "POWER_GRAPH",
                            onClick = { viewModel.updatePanelContent("POWER_GRAPH", globalSettings.rightPanelContent) }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Right Panel", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Battery & Range")
                        RadioButton(
                            selected = globalSettings.rightPanelContent == "BATTERY_RANGE",
                            onClick = { viewModel.updatePanelContent(globalSettings.leftPanelContent, "BATTERY_RANGE") }
                        )
                    }
                    Row {
                        Text("Power Sweep")
                        RadioButton(
                            selected = globalSettings.rightPanelContent == "POWER_SWEEP",
                            onClick = { viewModel.updatePanelContent(globalSettings.leftPanelContent, "POWER_SWEEP") }
                        )
                    }
                    Row {
                        Text("Power Graph")
                        RadioButton(
                            selected = globalSettings.rightPanelContent == "POWER_GRAPH",
                            onClick = { viewModel.updatePanelContent(globalSettings.leftPanelContent, "POWER_GRAPH") }
                        )
                    }
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

@Composable
private fun DeviceSettingsSection(viewModel: SettingsViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Device settings require a connected scooter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Connect to a BMS to configure:", style = MaterialTheme.typography.bodySmall)
        Text("• Max Power", style = MaterialTheme.typography.bodySmall)
        Text("• Full Range", style = MaterialTheme.typography.bodySmall)
        Text("• Power Zones", style = MaterialTheme.typography.bodySmall)
        Text("• Distance Unit", style = MaterialTheme.typography.bodySmall)
        Text("• Temperature Thresholds", style = MaterialTheme.typography.bodySmall)
        Text("• Show Power Graph", style = MaterialTheme.typography.bodySmall)
    }
}