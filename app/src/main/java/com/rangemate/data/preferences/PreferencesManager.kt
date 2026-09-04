package com.rangemate.data.preferences

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class DevicePreferences(
    val macAddress: String,
    val maxPower: Float = 2000f,
    val fullRangeKm: Float = 70f,
    val distanceUnit: String = "km",
    val greenZoneEnd: Float = 1000f,
    val yellowZoneEnd: Float = 1500f,
    val showPowerGraph: Boolean = false,
    val warningTempC: Float = 45f,
    val criticalTempC: Float = 55f,
    val lastConnected: Long = 0L,
    val rideCount: Int = 0,
    val totalDistanceKm: Float = 0f,
    val recentWhPerKm: Float? = null,
    val whPerKmHistory: List<Float> = emptyList(),
    val adaptiveRangeEnabled: Boolean = true,
    val detectedProtocol: String = "",
    val protocolCachedAt: Long = 0L
)

data class GlobalPreferences(
    val lastConnectedDeviceMac: String? = null,
    val themeMode: String = "DARK",
    val accentColor: Int = 0x00E676.toInt(),  // Bright green, fits in Int
    val autoReconnectEnabled: Boolean = true,
    val dashboardLayout: String = "FULL",
    val leftPanelContent: String = "BATTERY_RANGE",
    val rightPanelContent: String = "POWER_SWEEP",
    val showMetricsRow: Boolean = true,
    val showSpeedTop: Boolean = true
)

@Singleton
class PreferencesManager @Inject constructor() {
    // In-memory storage (DataStore persistence can be added later)
    private val devicePreferencesMap = mutableMapOf<String, DevicePreferences>()
    private val _globalPreferences = MutableStateFlow(GlobalPreferences())
    val globalPreferences: Flow<GlobalPreferences> = _globalPreferences.asStateFlow()

    private val devicePreferencesFlows = mutableMapOf<String, MutableStateFlow<DevicePreferences>>()

    // =========================================================================
    // GLOBAL PREFERENCES
    // =========================================================================

    suspend fun updateGlobalPreferences(block: GlobalPreferences.() -> GlobalPreferences) {
        val current = _globalPreferences.value
        val updated = current.block()
        _globalPreferences.value = updated
    }

    // =========================================================================
    // PER-DEVICE PREFERENCES
    // =========================================================================

    private fun getOrCreateDeviceFlow(macAddress: String): MutableStateFlow<DevicePreferences> {
        return devicePreferencesFlows.getOrPut(macAddress) {
            MutableStateFlow(DevicePreferences(macAddress = macAddress))
        }
    }

    suspend fun getDevicePreferences(macAddress: String): DevicePreferences {
        return devicePreferencesMap.getOrPut(macAddress) { DevicePreferences(macAddress = macAddress) }
    }

    // Get device preferences as a Flow (reactive)
    fun getDevicePreferencesFlow(macAddress: String): StateFlow<DevicePreferences> {
        return getOrCreateDeviceFlow(macAddress).asStateFlow()
    }

    suspend fun updateDevicePreferences(macAddress: String, block: DevicePreferences.() -> DevicePreferences) {
        val current = devicePreferencesMap.getOrPut(macAddress) { DevicePreferences(macAddress = macAddress) }
        val updated = current.block()
        devicePreferencesMap[macAddress] = updated
        getOrCreateDeviceFlow(macAddress).value = updated
    }

    // =========================================================================
    // CONVENIENCE METHODS
    // =========================================================================

    suspend fun updateMaxPower(macAddress: String, maxPower: Float) {
        updateDevicePreferences(macAddress) { copy(maxPower = maxPower) }
    }

    suspend fun updateFullRange(macAddress: String, rangeKm: Float) {
        updateDevicePreferences(macAddress) { copy(fullRangeKm = rangeKm) }
    }

    suspend fun updateDistanceUnit(macAddress: String, unit: String) {
        updateDevicePreferences(macAddress) { copy(distanceUnit = unit) }
    }

    suspend fun updatePowerZones(macAddress: String, greenEnd: Float, yellowEnd: Float) {
        updateDevicePreferences(macAddress) { copy(greenZoneEnd = greenEnd, yellowZoneEnd = yellowEnd) }
    }

    suspend fun updateShowPowerGraph(macAddress: String, show: Boolean) {
        updateDevicePreferences(macAddress) { copy(showPowerGraph = show) }
    }

    suspend fun updateTempThresholds(macAddress: String, warning: Float, critical: Float) {
        updateDevicePreferences(macAddress) { copy(warningTempC = warning, criticalTempC = critical) }
    }

    suspend fun updateThemeMode(mode: String) {
        _globalPreferences.value = _globalPreferences.value.copy(themeMode = mode)
    }

    suspend fun updateAccentColor(color: Int) {
        _globalPreferences.value = _globalPreferences.value.copy(accentColor = color)
    }

    suspend fun updateAutoReconnect(enabled: Boolean) {
        _globalPreferences.value = _globalPreferences.value.copy(autoReconnectEnabled = enabled)
    }

    suspend fun updateDashboardLayout(layout: String) {
        _globalPreferences.value = _globalPreferences.value.copy(dashboardLayout = layout)
    }

    suspend fun updatePanelContent(left: String, right: String) {
        _globalPreferences.value = _globalPreferences.value.copy(leftPanelContent = left, rightPanelContent = right)
    }

    suspend fun updateShowMetricsRow(show: Boolean) {
        _globalPreferences.value = _globalPreferences.value.copy(showMetricsRow = show)
    }

    suspend fun updateShowSpeedTop(show: Boolean) {
        _globalPreferences.value = _globalPreferences.value.copy(showSpeedTop = show)
    }

    suspend fun setLastConnectedDevice(macAddress: String?) {
        _globalPreferences.value = _globalPreferences.value.copy(lastConnectedDeviceMac = macAddress)
    }

    suspend fun recordConnection(macAddress: String) {
        updateDevicePreferences(macAddress) { copy(lastConnected = System.currentTimeMillis()) }
        _globalPreferences.value = _globalPreferences.value.copy(lastConnectedDeviceMac = macAddress)
    }

    suspend fun recordRide(macAddress: String, distanceKm: Float, whPerKm: Float) {
        updateDevicePreferences(macAddress) {
            val newHistory = (whPerKmHistory + whPerKm).takeLast(50)
            val weightedAvg = calculateWeightedWhPerKm(newHistory)
            copy(
                rideCount = rideCount + 1,
                totalDistanceKm = totalDistanceKm + distanceKm,
                recentWhPerKm = weightedAvg,
                whPerKmHistory = newHistory
            )
        }
    }

    private fun calculateWeightedWhPerKm(history: List<Float>): Float {
        if (history.isEmpty()) return 0f
        val weights = history.mapIndexed { i, _ -> (i + 1).toFloat() }
        val totalWeight = weights.sum()
        var sum = 0f
        for (i in history.indices) {
            sum += history[i] * weights[i]
        }
        return sum / totalWeight
    }

    suspend fun updateAdaptiveRange(macAddress: String, enabled: Boolean) {
        updateDevicePreferences(macAddress) { copy(adaptiveRangeEnabled = enabled) }
    }

    suspend fun cacheProtocol(macAddress: String, protocolName: String) {
        updateDevicePreferences(macAddress) { 
            copy(detectedProtocol = protocolName, protocolCachedAt = System.currentTimeMillis()) 
        }
    }

    suspend fun getCachedProtocol(macAddress: String): String? {
        val prefs = devicePreferencesMap[macAddress] ?: DevicePreferences(macAddress = macAddress)
        val ageHours = (System.currentTimeMillis() - prefs.protocolCachedAt) / (1000 * 60 * 60)
        return if (ageHours < 168 && prefs.detectedProtocol.isNotBlank()) prefs.detectedProtocol else null
    }
}