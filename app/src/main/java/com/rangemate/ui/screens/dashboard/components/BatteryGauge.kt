package com.rangemate.ui.screens.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rangemate.ui.theme.*

@Composable
fun BatteryGauge(
    soc: Float,
    voltage: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val size = minOf(size.width, size.height)
            val strokeWidth = size * 0.08f

            // Background arc
            drawArc(
                color = Color.Gray.copy(alpha = 0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(size, size),
                topLeft = Offset(
                    (this.size.width - size) / 2,
                    (this.size.height - size) / 2
                )
            )

            // SOC arc with color gradient based on level
            val socColor = when {
                soc >= 50f -> PowerZoneGreen
                soc >= 20f -> PowerZoneYellow
                else -> PowerZoneRed
            }

            val sweepAngle = (soc / 100f) * 270f
            drawArc(
                color = socColor,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(size, size),
                topLeft = Offset(
                    (this.size.width - size) / 2,
                    (this.size.height - size) / 2
                )
            )
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${soc.toInt()}%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = String.format("%.1f V", voltage),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}
