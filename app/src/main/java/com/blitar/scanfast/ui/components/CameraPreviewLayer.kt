package com.blitar.scanfast.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blitar.scanfast.camera.CameraXController

/**
 * Preview kamera layar penuh + guide-frame + tap-to-focus (lingkaran putih muncul di titik tap,
 * membesar sedikit lalu memudar ±500 ms). Dipakai CaptureScreen dan RetakeScreen. Tombol/overlay
 * lain harus digambar SESUDAH layer ini agar tap pada tombol tidak memicu fokus.
 */
@Composable
fun CameraPreviewLayer(controller: CameraXController, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var tapPos by remember { mutableStateOf(Offset.Zero) }
    var tapCount by remember { mutableIntStateOf(0) }
    val ring = remember { Animatable(1f) }

    // Flash shutter: putih ±150 ms lalu memudar. Hanya digambar (tanpa pointer input) sehingga tidak
    // memblokir tap/capture berikutnya; capture beruntun cukup memulai ulang animasinya.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(controller.shutterCount) {
        if (controller.shutterCount > 0) {
            flash.snapTo(FLASH_PEAK)
            flash.animateTo(0f, tween(FLASH_MS, easing = LinearEasing))
        }
    }

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(RING_MS, easing = LinearEasing))
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                    doOnLayout { controller.start(lifecycleOwner, this) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        GuideFrame()
        Box(
            Modifier.fillMaxSize().pointerInput(controller) {
                detectTapGestures { offset ->
                    previewView?.let { controller.focusAt(it, offset.x, offset.y) }
                    tapPos = offset
                    tapCount++
                }
            }
        )
        Canvas(Modifier.fillMaxSize()) {
            val p = ring.value
            if (tapCount > 0 && p < 1f) {
                val radius = 26.dp.toPx() * (1f + 0.45f * p)
                drawCircle(
                    Color.White.copy(alpha = 1f - p), radius, tapPos, style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(Color.White.copy(alpha = (1f - p) * 0.9f), 3.dp.toPx(), tapPos)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (flash.value > 0f) drawRect(Color.White.copy(alpha = flash.value))
        }
    }
}

private const val RING_MS = 500
private const val FLASH_MS = 150
private const val FLASH_PEAK = 0.7f
