package com.netcoremessenger.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.netcoremessenger.ui.theme.CosmicLightBackground

@Composable
fun DynamicBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background == CosmicLightBackground
    val baseColor = if (isLight) MaterialTheme.colorScheme.background else Color(0xFF030508)
    val doodleColor = if (isLight) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.115f)
    }
    val secondaryDoodleColor = if (isLight) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.030f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
    }
    val washColor = if (isLight) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    } else {
        Color(0xFF05070C).copy(alpha = 0.96f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            drawRect(color = washColor, topLeft = Offset.Zero, size = Size(width, height))

            val cell = 116f
            val stroke = Stroke(width = 2.2f)
            var y = -cell
            var row = 0
            while (y < height + cell) {
                var x = if (row % 2 == 0) -cell * 0.25f else cell * 0.25f
                var col = 0
                while (x < width + cell) {
                    val cx = x + cell * 0.5f
                    val cy = y + cell * 0.5f
                    val ink = if ((row + col) % 3 == 0) doodleColor else secondaryDoodleColor
                    when ((row + col) % 5) {
                        0 -> {
                            drawRoundRect(
                                color = ink,
                                topLeft = Offset(cx - 18f, cy - 11f),
                                size = Size(36f, 22f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                                style = stroke
                            )
                            drawLine(ink, Offset(cx - 8f, cy + 12f), Offset(cx - 15f, cy + 20f), 2.2f)
                        }
                        1 -> {
                            drawCircle(ink, radius = 15f, center = Offset(cx, cy), style = stroke)
                            drawLine(ink, Offset(cx - 9f, cy), Offset(cx + 9f, cy), 2.2f)
                            drawLine(ink, Offset(cx, cy - 9f), Offset(cx, cy + 9f), 2.2f)
                        }
                        2 -> {
                            val path = Path().apply {
                                moveTo(cx - 18f, cy + 8f)
                                quadraticBezierTo(cx - 4f, cy - 16f, cx + 18f, cy - 8f)
                                quadraticBezierTo(cx + 8f, cy + 14f, cx - 18f, cy + 8f)
                            }
                            drawPath(path, ink, style = stroke)
                            drawCircle(ink, radius = 2.2f, center = Offset(cx + 8f, cy - 5f))
                        }
                        3 -> {
                            drawArc(
                                color = ink,
                                startAngle = 210f,
                                sweepAngle = 240f,
                                useCenter = false,
                                topLeft = Offset(cx - 16f, cy - 16f),
                                size = Size(32f, 32f),
                                style = stroke
                            )
                            drawLine(ink, Offset(cx + 10f, cy + 11f), Offset(cx + 18f, cy + 19f), 2.2f)
                        }
                        else -> {
                            drawLine(ink, Offset(cx, cy - 18f), Offset(cx, cy + 18f), 2.2f)
                            drawLine(ink, Offset(cx - 18f, cy), Offset(cx + 18f, cy), 2.2f)
                            drawLine(ink, Offset(cx - 12f, cy - 12f), Offset(cx + 12f, cy + 12f), 2.2f)
                            drawLine(ink, Offset(cx - 12f, cy + 12f), Offset(cx + 12f, cy - 12f), 2.2f)
                        }
                    }
                    x += cell
                    col++
                }
                y += cell
                row++
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
