package com.rangemate.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rangemate.data.model.BmsData
import com.rangemate.ui.screens.dashboard.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onDisconnect: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDebugClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val bmsData by viewModel.bmsData.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val speedKmh by viewModel.speedKmh.collectAsStateWithLifecycle()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ConnectionStatusBar(
                connected = bmsData.connected,
                deviceName = bmsData.deviceName,
                onDisconnect = onDisconnect,
                onSettingsClick = onSettingsClick,
                onDebugClick = onDebugClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Speed display at top center
            SpeedDisplay(
                speedKmh = speedKmh,
                isGpsEnabled = isGpsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            )

            // Main dashboard row: SOC/Range on left, power meter on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Left column: Battery SOC and Range
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    BatteryGauge(
                        soc = bmsData.soc.toFloat(),
                        voltage = bmsData.voltage
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    RangeDisplay(
                        range = bmsData.range,
                        unit = settings.distanceUnit
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Right: Power sweep meter
                PowerSweepMeter(
                    power = bmsData.power,
                    maxPower = settings.maxPower,
                    greenZoneEnd = settings.greenZoneEnd,
                    yellowZoneEnd = settings.yellowZoneEnd,
                    modifier = Modifier.weight(1.5f)
                )
            }

            // Bottom metrics row
            MetricsRow(
                current = bmsData.current,
                voltage = bmsData.voltage,
                temperature = bmsData.temperature,
                capacity = bmsData.capacity,
                totalCapacity = bmsData.totalCapacity
            )

            // Optional: Power graph (when enabled)
            if (settings.showPowerGraph) {
                PowerGraph(
                    powerHistory = viewModel.powerHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}