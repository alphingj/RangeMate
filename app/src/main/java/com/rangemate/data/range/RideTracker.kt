package com.rangemate.data.range

import com.rangemate.data.model.BmsData
import com.rangemate.data.repository.BmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Tracks ride data and feeds AdaptiveRangeEngine
 */
@Singleton
class RideTracker @Inject constructor(
    private val bmsRepository: BmsRepository,
    private val adaptiveRangeEngine: AdaptiveRangeEngine
) {
    private var lastBmsData: BmsData? = null
    private var segmentStartSoc: Int = -1
    private var segmentStartCapacityAh: Float = 0f
    private var segmentDistanceM: Float = 0f
    private var segmentEnergyWh: Float = 0f
    private var segmentPowerSum: Float = 0f
    private var segmentPowerCount: Int = 0
    private var segmentPowerSqSum: Float = 0f
    private var segmentMaxAcceleration: Float = 0f
    private var segmentStartTime: Long = 0
    private var lastSpeedKmh: Float = 0f
    private var lastTimestamp: Long = 0
    private var isTracking = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private var collectJob: Job? = null

    // Ride detection thresholds
    private val MIN_RIDE_SPEED_KMH = 3f
    private val SEGMENT_DISTANCE_M = 100f
    private val MIN_RIDE_DURATION_MS = 60_000 // 1 minute minimum

    fun startTracking() {
        if (isTracking) return
        isTracking = true
        resetSegment()
        startCollecting()
    }

    fun stopTracking(): RangePrediction? {
        isTracking = false
        collectJob?.cancel()
        collectJob = null
        finalizeSegment()
        return adaptiveRangeEngine.rangePrediction.value
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = scope.launch {
            bmsRepository.bmsData
                .filter { bms -> bms.connected }
                .distinctUntilChanged { prev, next -> prev.timestamp == next.timestamp }
                .collect { bmsData ->
                    processBmsData(bmsData)
                }
        }
    }

    private fun processBmsData(bmsData: BmsData) {
        if (!isTracking) return
        
        // Get speed from GPS (would need to be passed in or accessed separately)
        // For now, we'll use a placeholder - in real implementation, get from SpeedProvider
        val speedKmh = 0f // TODO: Get from SpeedProvider
        
        val now = System.currentTimeMillis()
        
        // Detect ride start
        if (!isRiding && speedKmh > 3f) {
            startRide()
        }
        
        if (isRiding) {
            // Accumulate segment data
            accumulateSegment(bmsData, speedKmh, now)
            
            // Check if segment is complete
            if (segmentDistanceM >= 100f) {
                finalizeSegment()
                resetSegment()
            }
            
            // Detect ride end
            if (speedKmh < 1f && (now - lastTimestamp) > 30_000) {
                stopTracking()
            }
        }
        
        lastBmsData = bmsData
        lastTimestamp = now
    }

    private var isRiding = false
    
    private fun startRide() {
        isRiding = true
        segmentStartTime = System.currentTimeMillis()
        lastBmsData?.let { bms ->
            segmentStartSoc = bms.soc
            segmentStartCapacityAh = bms.capacity
        }
        resetSegment()
    }

    private fun accumulateSegment(bmsData: BmsData, speedKmh: Float, timestamp: Long) {
        lastBmsData?.let { prev ->
            // Calculate distance from speed and time
            val dtHours = (timestamp - lastTimestamp) / 3_600_000f // hours
            val distanceKm = speedKmh * dtHours
            segmentDistanceM += distanceKm * 1000
            
            // Calculate energy from capacity change
            val capacityChangeAh = prev.capacity - bmsData.capacity
            if (capacityChangeAh > 0) {
                val avgVoltage = (prev.voltage + bmsData.voltage) / 2f
                val energyWh = capacityChangeAh * avgVoltage
                segmentEnergyWh += energyWh
            }
            
            // Accumulate power for variance
            segmentPowerSum += bmsData.power
            segmentPowerSqSum += bmsData.power * bmsData.power
            segmentPowerCount++
            
            // Estimate acceleration from speed change
            val acceleration = (speedKmh - lastSpeedKmh) / 3.6f / (dtHours * 3600f) // m/s²
            segmentMaxAcceleration = maxOf(segmentMaxAcceleration, abs(acceleration))
        }
        
        lastSpeedKmh = speedKmh
    }

    private fun finalizeSegment() {
        if (segmentDistanceM < 50f || segmentPowerCount == 0) {
            resetSegment()
            return
        }
        
        val distanceKm = segmentDistanceM / 1000f
        val avgPower = segmentPowerSum / segmentPowerCount
        val avgPowerSq = segmentPowerSqSum / segmentPowerCount
        val powerVariance = avgPowerSq - avgPower * avgPower
        val avgSpeed = lastSpeedKmh
        
        val whPerKm = if (distanceKm > 0f) segmentEnergyWh / distanceKm else 0f
        
        // Feed to adaptive range engine
        adaptiveRangeEngine.processRideSegment(
            distanceTraveledM = segmentDistanceM,
            energyConsumedWh = segmentEnergyWh,
            avgSpeedKmh = avgSpeed,
            avgPowerW = avgPower,
            powerVariance = powerVariance,
            accelerationRate = segmentMaxAcceleration
        )
        
        resetSegment()
    }

    private fun resetSegment() {
        segmentStartSoc = -1
        segmentStartCapacityAh = 0f
        segmentDistanceM = 0f
        segmentEnergyWh = 0f
        segmentPowerSum = 0f
        segmentPowerCount = 0
        segmentPowerSqSum = 0f
        segmentMaxAcceleration = 0f
        lastSpeedKmh = 0f
    }
}

@HiltViewModel
class RangeViewModel @Inject constructor(
    private val adaptiveRangeEngine: AdaptiveRangeEngine,
    private val rideTracker: RideTracker,
    private val bmsRepository: BmsRepository
) : ViewModel() {

    val rangePrediction = adaptiveRangeEngine.rangePrediction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val drivingPattern = adaptiveRangeEngine.drivingPattern
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Expose calculated range based on current battery
    val calculatedRange = combine(
        bmsRepository.bmsData,
        adaptiveRangeEngine.rangePrediction
    ) { bmsData, prediction ->
        prediction?.let { p ->
            adaptiveRangeEngine.calculateRange(
                packVoltageV = bmsData.voltage,
                remainingCapacityAh = bmsData.capacity,
                totalCapacityAh = bmsData.totalCapacity,
                socPercent = bmsData.soc
            )
        } ?: null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startRide() {
        rideTracker.startTracking()
    }

    fun stopRide(): RangePrediction? {
        return rideTracker.stopTracking()
    }
}