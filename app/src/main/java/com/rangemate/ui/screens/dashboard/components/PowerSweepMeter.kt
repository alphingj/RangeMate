package com.rangemate.ui.screens.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rangemate.ui.theme.PowerZoneGreen
import com.rangemate.ui.theme.PowerZoneYellow
import com.rangemate.ui.theme.PowerZoneRed

@Composable
fun PowerSweepMeter(
    power: Float,
    maxPower: Float,
    modifier: Modifier = Modifier,
    greenZoneEnd: Float = 1000f,
    yellowZoneEnd: Float = 1500f
) {
    // Animate power position
    val animatedPower by animateFloatAsState(
        targetValue = power.coerceIn(0f, maxPower),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "power"
    )

    Column(modifier = modifier) {
        // Power value display
        Text(
            text = String.format("%.0f W", power),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sweep meter
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val width = size.width
            val height = size.height
            val strokeWidth = height * 0.8f

            // Calculate zone positions
            val greenEnd = (greenZoneEnd / maxPower) * width
            val yellowEnd = (yellowZoneEnd / maxPower) * width

            // Draw green zone
            drawLine(
                color = PowerZoneGreen,
                start = Offset(0f, height / 2),
                end = Offset(greenEnd, height / 2),
                strokeWidth = strokeWidth
            )

            // Draw yellow zone
            drawLine(
                color = PowerZoneYellow,
                start = Offset(greenEnd, height / 2),
                end = Offset(yellowEnd, height / 2),
                strokeWidth = strokeWidth
            )

            // Draw red zone
            drawLine(
                color = PowerZoneRed,
                start = Offset(yellowEnd, height / 2),
                end = Offset(width, height / 2),
                strokeWidth = strokeWidth
            )

            // Draw power indicator
            val indicatorPos = (animatedPower / maxPower) * width
            drawCircle(
                color = Color.White,
                radius = height * 0.6f,
                center = Offset(indicatorPos, height / 2),
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = when {
                    animatedPower <= greenZoneEnd -> PowerZoneGreen
                    animatedPower <= yellowZoneEnd -> PowerZoneYellow
                    else -> PowerZoneRed
                },
                radius = height * 0.5f,
                center = Offset(indicatorPos, height / 2)
            )
        }

        // Zone labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Text(
                text = String.format("%.0f", greenZoneEnd),
                fontSize = 12.sp,
                color = PowerZoneGreen
            )
            Text(
                text = String.format("%.0f", yellowZoneEnd),
                fontSize = 12.sp,
                color = PowerZoneYellow
            )
            Text(
                text = String.format("%.0f", maxPower),
                fontSize = 12.sp,
                color = PowerZoneRed
            )
        }
    }
}
