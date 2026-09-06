package com.rangemate.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rangemate.data.range.AdaptiveRangeEngine
import com.rangemate.data.range.DrivingPattern
import com.rangemate.data.range.RangePrediction

@Composable
fun AdaptiveRangeDisplay(
    prediction: RangePrediction?,
    modifier: Modifier = Modifier,
    unit: String = "km"
) {
    val imperial = unit == "mi"
    val unitLabel = if (imperial) "mi" else "km"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        prediction?.let { p ->
            // Main range with confidence
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "%.1f %s",
                        convertKm(p.estimatedRangeKm, imperial),
                        unitLabel
                    ),
                    fontSize = 48.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                ConfidenceBadge(confidence = p.confidence)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Driving pattern
            Text(
                text = "Pattern: ${formatPattern(p.pattern)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Scenario ranges
            ScenarioRow(scenarios = p.rangeScenarios, imperial = imperial, unitLabel = unitLabel)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sample info
            Text(
                text = "Based on ${p.basedOnSamples} segments • ${p.formatConfidence()} confidence",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } ?: Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ride to learn range",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = "Drive 100m+ segments to build prediction",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val color = when {
        confidence >= 0.8f -> MaterialTheme.colorScheme.primary
        confidence >= 0.5f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    
    Box(
        modifier = Modifier
            .padding(8.dp)
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${(confidence * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ScenarioRow(scenarios: Map<String, Float>, imperial: Boolean, unitLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        scenarios.forEach { (label, rangeKm) ->
            if (rangeKm > 0f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label.replace("_", " ").uppercase(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = String.format(
                            java.util.Locale.US,
                            "%.1f %s",
                            convertKm(rangeKm, imperial),
                            unitLabel
                        ),
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun convertKm(km: Float, imperial: Boolean): Float =
    if (imperial) km * 0.621371f else km

private fun formatPattern(pattern: DrivingPattern): String {
    return when (pattern) {
        DrivingPattern.CITY_CALM -> "City Calm"
        DrivingPattern.CITY_NORMAL -> "City Normal"
        DrivingPattern.CITY_AGGRESSIVE -> "City Aggressive"
        DrivingPattern.HIGHWAY -> "Highway"
        DrivingPattern.HILLY -> "Hilly"
        DrivingPattern.MIXED -> "Mixed"
    }
}