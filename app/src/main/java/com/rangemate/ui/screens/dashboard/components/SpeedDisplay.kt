package com.rangemate.ui.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpeedDisplay(
    speedKmh: Float,
    isGpsEnabled: Boolean,
    modifier: Modifier = Modifier,
    unit: String = "km"
) {
    val imperial = unit == "mi"
    val speed = if (imperial) speedKmh * KMH_TO_MPH else speedKmh
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = String.format(java.util.Locale.US, "%.0f", speed),
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (imperial) "mph" else "km/h",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        if (!isGpsEnabled) {
            Text(
                text = "GPS off",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private const val KMH_TO_MPH = 0.621371f