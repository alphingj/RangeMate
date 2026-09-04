package com.rangemate.data.range

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Adaptive Range Prediction Engine
 * 
 * Algorithm:
 * 1. Collect ride segments (100m windows) with energy consumption
 * 2. Calculate Wh/km per segment
 * 3. Filter outliers (stops, GPS drift, anomalies)
 * 4. Cluster by driving pattern (speed, acceleration, power variance)
 * 5. Weight recent rides exponentially (decay factor 7 days)
 * 6. Predict range = Remaining_Wh / Weighted_Wh_per_km
 * 
 * Data structures:
 * - RideSegment: 100m window with energy, speed, power, acceleration
 * - DrivingPattern: Clustered behavior profile
 * - RangePrediction: Estimated range with confidence intervals
 */
class AdaptiveRangeEngine {

    // Configuration
    private val SEGMENT_DISTANCE_M = 100  // 100m segments
    private val MIN_SEGMENT_SPEED_KMH = 2f  // Ignore near-stops
    private val MAX_WH_PER_KM = 100f  // Outlier threshold
    private val DECAY_HALF_LIFE_DAYS = 7.0  // Exponential decay half-life
    private val MIN_SAMPLES_FOR_PREDICTION = 5  // Need at least 5 segments
    private val CONFIDENCE_SAMPLES = 30  // Samples for high confidence

    // State
    private val _rangePrediction = MutableStateFlow<RangePrediction?>(null)
    val rangePrediction: StateFlow<RangePrediction?> = _rangePrediction

    private val _drivingPattern = MutableStateFlow<DrivingPattern?>(null)
    val drivingPattern: StateFlow<DrivingPattern?> = _drivingPattern

    /**
     * Process a new ride segment from live data
     * Call this periodically during a ride (e.g., every 100m or when sufficient data accumulated)
     */
    fun processRideSegment(
        distanceTraveledM: Float,
        energyConsumedWh: Float,
        avgSpeedKmh: Float,
        avgPowerW: Float,
        powerVariance: Float,
        accelerationRate: Float,  // m/s² per segment
        elevationGainM: Float = 0f,
        temperatureC: Float = 20f
    ): RangePrediction? {
        val distanceKm = distanceTraveledM / 1000f
        if (distanceKm < 0.05f) return _rangePrediction.value // Too short

        val whPerKm = if (distanceKm > 0f) energyConsumedWh / distanceKm else 0f
        if (whPerKm > MAX_WH_PER_KM || whPerKm < 1f) return _rangePrediction.value // Outlier

        // Create segment
        val segment = RideSegment(
            distanceKm = distanceKm,
            whPerKm = whPerKm,
            avgSpeedKmh = avgSpeedKmh,
            avgPowerW = avgPowerW,
            powerVariance = powerVariance,
            accelerationRate = accelerationRate,
            elevationGainM = elevationGainM,
            temperatureC = temperatureC,
            timestamp = System.currentTimeMillis()
        )

        // Update prediction with new segment
        return updatePrediction(segment)
    }

    /**
     * Update prediction with a new segment using exponential weighting
     */
    private fun updatePrediction(newSegment: RideSegment): RangePrediction? {
        // In a full implementation, we'd maintain a history of segments
        // For now, use exponential moving average
        val current = _rangePrediction.value
        
        val decayFactor = Math.exp(-1.0 / (DECAY_HALF_LIFE_DAYS * 24 * 60 * 60 * 1000 / 3600000)) // Per hour
        // Simplified: weight recent segments more
        
        val newWhPerKm = current?.let { pred ->
            val alpha = 0.3f // Smoothing factor
            alpha * newSegment.whPerKm + (1 - alpha) * pred.recentWhPerKm
        } ?: newSegment.whPerKm

        val samples = (current?.basedOnSamples ?: 0) + 1
        val confidence = (samples.toFloat() / CONFIDENCE_SAMPLES).coerceAtMost(1f)

        // Classify driving pattern
        val pattern = classifyPattern(newSegment)
        _drivingPattern.value = pattern

        // Scenario-based predictions
        val scenarios = calculateScenarios(newWhPerKm, pattern)

        val prediction = RangePrediction(
            estimatedRangeKm = 0f, // Will be set by caller with battery state
            recentWhPerKm = newWhPerKm,
            confidence = confidence,
            basedOnSamples = samples,
            pattern = pattern,
            whPerKmScenarios = scenarios,
            rangeScenarios = emptyMap(),
            timestamp = System.currentTimeMillis()
        )

        _rangePrediction.value = prediction
        return prediction
    }

    /**
     * Calculate estimated range given current battery state
     */
    fun calculateRange(
        packVoltageV: Float,
        remainingCapacityAh: Float,
        totalCapacityAh: Float,
        socPercent: Int
    ): RangePrediction? {
        val prediction = _rangePrediction.value ?: return null
        
        // Remaining energy in Wh
        val remainingWh = (socPercent / 100f) * totalCapacityAh * packVoltageV
        
        // Base range
        val baseRange = if (prediction.recentWhPerKm > 0f) {
            remainingWh / prediction.recentWhPerKm
        } else 0f

        // Apply pattern adjustments
        val adjustedRange = applyPatternAdjustment(baseRange, prediction.pattern)

        // Calculate scenario ranges
        val rangeScenarios: Map<String, Float> = prediction.whPerKmScenarios.mapValues { entry ->
            val whPerKm = entry.value
            if (whPerKm > 0f) remainingWh / whPerKm else 0f
        }

        return prediction.copy(
            estimatedRangeKm = adjustedRange,
            rangeScenarios = rangeScenarios
        )
    }

    private fun classifyPattern(segment: RideSegment): DrivingPattern {
        return when {
            segment.avgSpeedKmh > 40 && segment.powerVariance < 500 -> DrivingPattern.HIGHWAY
            segment.avgSpeedKmh < 20 && segment.accelerationRate > 1.5 -> DrivingPattern.CITY_AGGRESSIVE
            segment.avgSpeedKmh < 20 -> DrivingPattern.CITY_CALM
            segment.elevationGainM > 5 -> DrivingPattern.HILLY
            else -> DrivingPattern.MIXED
        }
    }

    private fun calculateScenarios(whPerKm: Float, pattern: DrivingPattern): Map<String, Float> {
        return mapOf(
            "city_calm" to whPerKm * 0.85f,
            "city_normal" to whPerKm * 1.0f,
            "city_aggressive" to whPerKm * 1.25f,
            "highway" to whPerKm * 1.15f,
            "hilly" to whPerKm * 1.35f,
            "mixed" to whPerKm
        )
    }

    private fun applyPatternAdjustment(baseRange: Float, pattern: DrivingPattern): Float {
        return when (pattern) {
            DrivingPattern.CITY_CALM -> baseRange * 1.1f
            DrivingPattern.CITY_NORMAL -> baseRange
            DrivingPattern.CITY_AGGRESSIVE -> baseRange * 0.85f
            DrivingPattern.HIGHWAY -> baseRange * 0.9f
            DrivingPattern.HILLY -> baseRange * 0.75f
            DrivingPattern.MIXED -> baseRange * 0.95f
        }
    }
}

data class RideSegment(
    val distanceKm: Float,
    val whPerKm: Float,
    val avgSpeedKmh: Float,
    val avgPowerW: Float,
    val powerVariance: Float,
    val accelerationRate: Float,
    val elevationGainM: Float,
    val temperatureC: Float,
    val timestamp: Long
)

data class RangePrediction(
    val estimatedRangeKm: Float,
    val recentWhPerKm: Float,
    val confidence: Float,  // 0.0 - 1.0
    val basedOnSamples: Int,
    val pattern: DrivingPattern,
    val whPerKmScenarios: Map<String, Float>,  // Scenario -> Wh/km
    val rangeScenarios: Map<String, Float> = emptyMap(),  // Scenario -> km
    val timestamp: Long
) {
    fun formatRange(): String {
        return String.format("%.1f km", estimatedRangeKm)
    }
    
    fun formatConfidence(): String {
        return String.format("%.0f%%", confidence * 100)
    }
}

enum class DrivingPattern {
    CITY_CALM,      // Low speed, low power variance, smooth
    CITY_NORMAL,    // Low-medium speed, moderate variance
    CITY_AGGRESSIVE, // Low speed, high acceleration, high variance
    HIGHWAY,        // High speed, low variance, steady power
    HILLY,          // Significant elevation changes
    MIXED           // Mixed conditions
}