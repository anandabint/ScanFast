package com.blitar.scanfast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import com.blitar.scanfast.model.MARGIN_PERCENT
import com.blitar.scanfast.model.guideRect

/**
 * Guide-frame berrasio tetap (GUIDE_ASPECT_RATIO): lebar mengikuti margin kiri-kanan (kini 0 = menempel
 * ke tepi layar), tinggi mengikuti rasio dan dipusatkan vertikal. Area DI LUAR kotak digelapkan
 * ([dimColor]); area di dalam dibiarkan transparan penuh. TIDAK ada garis tepi: batas kotak ditandai
 * oleh transisi gelap-ke-jernih itu sendiri. Hanya visual + dasar fallback crop; tidak membatasi
 * deteksi otomatis dan tidak menangani sentuhan.
 */
@Composable
fun GuideFrame(
    modifier: Modifier = Modifier,
    marginPercent: Float = MARGIN_PERCENT,
    dimColor: Color = Color.Black.copy(alpha = 0.55f),
) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val g = guideRect(size.width / size.height, marginPercent)
            // Lapisan gelap seluruh layar, dikurangi (Difference) area kotak.
            clipRect(
                g.left * size.width, g.top * size.height, g.right * size.width, g.bottom * size.height,
                clipOp = ClipOp.Difference,
            ) {
                drawRect(dimColor)
            }
        }
    }
}
