package com.rangemate.ui.screens.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rangemate.ui.theme.PowerZoneGreen
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PowerGraph(
    powerHistory: StateFlow<List<Pair<Long, Float>>>,
    modifier: Modifier = Modifier
) {
    val history by powerHistory.collectAsState()

    Canvas(modifier = modifier.fillMaxSize()) {
        if (history.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height

        // Find min/max for scaling
        val maxPower = history.maxOfOrNull { it.second } ?: 1f
        val minTime = history.firstOrNull()?.first ?: 0L
        val maxTime = history.lastOrNull()?.first ?: 1L
        val timeRange = (maxTime - minTime).coerceAtLeast(1L)

        // Draw grid lines
        val gridColor = Color.Gray.copy(alpha = 0.2f)
        for (i in 0..4) {
            val y = height * i / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw power line
        val path = Path()
        history.forEachIndexed { index, (timestamp, power) ->
            val x = ((timestamp - minTime).toFloat() / timeRange) * width
            val y = height - ((power / maxPower) * height)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = PowerZoneGreen,
            style = Stroke(width = 3f)
        )
    }
}
