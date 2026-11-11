package com.blitar.scanfast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blitar.scanfast.model.MARGIN_PERCENT

/** Kotak garis tipis semi-transparan; margin = persentase ukuran layar (sama dengan crop). */
@Composable
fun GuideFrame(
    modifier: Modifier = Modifier,
    marginPercent: Float = MARGIN_PERCENT,
    strokeWidth: Dp = 1.5.dp,
    color: Color = Color.White.copy(alpha = 0.6f),
) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val mx = size.width * marginPercent
            val my = size.height * marginPercent
            drawRect(
                color = color,
                topLeft = Offset(mx, my),
                size = Size(size.width - 2 * mx, size.height - 2 * my),
                style = Stroke(width = strokeWidth.toPx())
            )
        }
    }
}
