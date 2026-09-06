package com.rangemate.ui.screens.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Rolling 60-second power history chart in EV-cluster style.
 *
 * - [isVisible] drives an [AnimatedVisibility] wrapper so hiding the graph
 *   collapses the space instead of leaving dead real estate.
 * - Y scale uses a dynamic ceiling: max(configured max, 110% of window peak).
 * - Area under the curve is filled with a vertical green gradient.
 */
@Composable
fun PowerGraph(
    isVisible: Boolean,
    powerHistory: List<Pair<Long, Float>>,
    maxPowerSetting: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(0xFF0D0D0D))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POWER DYNAMICS (W)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )

                val currentPower = powerHistory.lastOrNull()?.second ?: 0f
                Text(
                    text = "${currentPower.toInt()} W",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        currentPower < maxPowerSetting * 0.45f -> Color(0xFF2E7D32)
                        currentPower < maxPowerSetting * 0.75f -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    val rawPeak = powerHistory.maxOfOrNull { it.second } ?: 0f
                    val dynamicCeiling = max(maxPowerSetting, rawPeak * 1.1f).coerceAtLeast(1f)

                    // Reference grid at 0% / 50% / 100%.
                    listOf(0.0f, 0.5f, 1.0f).forEach { scalar ->
                        val yOffset = height * (1f - scalar)
                        drawLine(
                            color = Color(0xFF262626),
                            start = Offset(0f, yOffset),
                            end = Offset(width, yOffset),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (powerHistory.size >= 2) {
                        val strokePath = Path()
                        val fillPath = Path()

                        val timeMin = powerHistory.first().first
                        val timeMax = powerHistory.last().first
                        val timeDelta = max(1L, timeMax - timeMin).toFloat()

                        powerHistory.forEachIndexed { idx, point ->
                            val x = ((point.first - timeMin) / timeDelta) * width
                            val yFraction = (point.second / dynamicCeiling).coerceIn(0f, 1f)
                            val y = height * (1f - yFraction)

                            if (idx == 0) {
                                strokePath.moveTo(x, y)
                                fillPath.moveTo(x, height)
                                fillPath.lineTo(x, y)
                            } else {
                                strokePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }

                            if (idx == powerHistory.lastIndex) {
                                fillPath.lineTo(x, height)
                                fillPath.close()
                            }
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1B5E20).copy(alpha = 0.45f),
                                    Color(0xFF1B5E20).copy(alpha = 0.00f)
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )

                        drawPath(
                            path = strokePath,
                            color = Color(0xFF4CAF50),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
