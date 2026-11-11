package com.blitar.scanfast.ui.components

import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Warna identitas ikon (sama dengan ic_launcher_*), sengaja tidak mengikuti tema dinamis.
private val IntroBackground = Color(0xFF0A0A0A)
private val IntroForeground = Color.White

private val OvershootEasing = Easing { OvershootInterpolator().getInterpolation(it) }
private val AccelerateEasing = Easing { AccelerateInterpolator().getInterpolation(it) }

private const val CORNER_OFFSET = 14f
private const val CORNER_DURATION = 450
private const val CORNER_STAGGER = 40L
private const val PAGE_DELAY = 200L
private const val PAGE_DURATION = 350
private const val HOLD = 230L
private const val EXIT_DURATION = 200

/**
 * Intro animasi di atas konten app: empat sudut viewfinder "snap" masuk bergantian, halaman dokumen
 * membesar, lalu seluruh overlay memudar sambil sedikit membesar (total 1,0 detik: 570 ms perakitan, 230 ms
 * tahan, 200 ms fade-out). Sebelum [start] true, ikon sudah tergambar pada pose awal (tidak ada layar kosong)
 * sehingga bisa tampil di frame pertama sementara konten app dikomposisi. Gerakannya sama dengan
 * `avd_splash_icon`. Menelan sentuhan selama berjalan; [onFinished] dipanggil setelah selesai.
 */
@Composable
fun IntroAnimation(start: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finished by rememberUpdatedState(onFinished)
    val corners = remember { List(4) { Animatable(0f) } } // 0 = bergeser, 1 = posisi akhir (overshoot > 1)
    val pageScale = remember { Animatable(0.6f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(start) {
        if (!start) return@LaunchedEffect
        coroutineScope {
            corners.forEachIndexed { i, a ->
                launch {
                    delay(i * CORNER_STAGGER)
                    a.animateTo(1f, tween(CORNER_DURATION, easing = OvershootEasing))
                }
            }
            launch {
                delay(PAGE_DELAY)
                pageScale.animateTo(1f, tween(PAGE_DURATION, easing = OvershootEasing))
            }
        }
        delay(HOLD)
        exit.animateTo(1f, tween(EXIT_DURATION, easing = AccelerateEasing))
        finished()
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(IntroBackground)
            .pointerInput(Unit) { detectTapGestures { /* telan sentuhan selama intro */ } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(160.dp)
                .graphicsLayer {
                    val s = 1f + 0.15f * exit.value
                    scaleX = s
                    scaleY = s
                }
        ) {
            val unit = size.minDimension / 108f
            scale(unit, pivot = Offset.Zero) {
                drawBrackets(corners.map { it.value })
                drawPage(pageScale.value)
            }
        }
    }
}

/** Gambar dalam koordinat viewport 108x108 (sudah diskalakan oleh pemanggil). */
private fun DrawScope.drawBrackets(progress: List<Float>) {
    val stroke = Stroke(width = 5.48f, cap = StrokeCap.Round)
    // kiri-atas, kanan-atas, kiri-bawah, kanan-bawah (urutan sama dengan target AVD)
    val shapes = listOf(
        Triple(Path().apply { moveTo(22.78f, 37.55f); lineTo(22.78f, 22.78f); lineTo(37.55f, 22.78f) }, -1f, -1f),
        Triple(Path().apply { moveTo(70.45f, 22.78f); lineTo(85.22f, 22.78f); lineTo(85.22f, 37.55f) }, 1f, -1f),
        Triple(Path().apply { moveTo(22.78f, 70.45f); lineTo(22.78f, 85.22f); lineTo(37.55f, 85.22f) }, -1f, 1f),
        Triple(Path().apply { moveTo(85.22f, 70.45f); lineTo(85.22f, 85.22f); lineTo(70.45f, 85.22f) }, 1f, 1f),
    )
    shapes.forEachIndexed { i, (path, dx, dy) ->
        val away = (1f - progress[i]) * CORNER_OFFSET
        translate(left = dx * away, top = dy * away) {
            drawPath(path, IntroForeground, style = stroke)
        }
    }
}

private fun DrawScope.drawPage(scale: Float) {
    scale(scale, pivot = Offset(54f, 54f)) {
        drawRect(IntroForeground, topLeft = Offset(41.34f, 37.13f), size = Size(25.31f, 33.75f))
        val line = Stroke(width = 2.11f, cap = StrokeCap.Round)
        val ink = IntroBackground.copy(alpha = 0.85f)
        fun lineTo(x1: Float, y: Float, x2: Float) =
            drawLine(ink, Offset(x1, y), Offset(x2, y), strokeWidth = line.width, cap = StrokeCap.Round)
        lineTo(45.98f, 45.56f, 62.02f)
        lineTo(45.98f, 51.89f, 62.02f)
        lineTo(45.98f, 58.22f, 56.95f)
    }
}
