package com.blitar.scanfast.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blitar.scanfast.model.BatchViewModel
import com.blitar.scanfast.model.CapturedPhoto
import com.blitar.scanfast.model.CaptureViewModel
import com.blitar.scanfast.model.NormPoint
import com.blitar.scanfast.model.QuadPoints
import com.blitar.scanfast.processing.ImageCropper
import com.blitar.scanfast.ui.components.ScreenScaffold
import com.blitar.scanfast.ui.components.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

private const val DISPLAY_LONG_SIDE = 1600
private const val MIN_SIDE_DP = 40

private class EditorData(val image: ImageBitmap, val initial: QuadPoints, val auto: QuadPoints)

private sealed interface EditorState {
    data object Loading : EditorState
    data object Failed : EditorState
    class Ready(val data: EditorData) : EditorState
}

@Composable
fun CropEditorScreen(
    number: Int,
    captureViewModel: CaptureViewModel,
    batchViewModel: BatchViewModel,
    onClose: () -> Unit,
    onRetake: (number: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = captureViewModel.currentSession()
    val photo = remember(session, number) { session?.photos?.firstOrNull { it.number == number } }
    if (session == null || photo == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Aksi "Foto ulang" dan "Hapus" SELALU ada di app bar, di semua state (memuat, rusak, siap).
    val topActions: @Composable RowScope.(Boolean) -> Unit = { busy ->
        TextButton(
            onClick = {
                haptics.tick()
                onRetake(photo.number)
            },
            enabled = !busy && !deleting,
        ) { Text("Foto ulang") }
        TextButton(
            onClick = {
                haptics.tick()
                showDeleteDialog = true
            },
            enabled = !busy && !deleting,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Hapus") }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus halaman ini?") },
            text = { Text("Nomor ${photo.number} akan dikosongkan.") },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleting = true
                        scope.launch {
                            try {
                                batchViewModel.deletePhoto(session, photo)
                                haptics.confirm()
                                onClose()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: RuntimeException) {
                                Log.e("CropEditor", "Hapus gagal", e)
                                haptics.reject()
                                Toast.makeText(context, "Gagal menghapus: ${e.message}", Toast.LENGTH_LONG).show()
                                deleting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Hapus") }
            },
        )
    }

    val state by produceState<EditorState>(EditorState.Loading, photo) {
        value = withContext(Dispatchers.IO) {
            loadEditorData(photo)?.let { EditorState.Ready(it) } ?: EditorState.Failed
        }
    }
    when (val s = state) {
        EditorState.Loading -> ScreenScaffold(
            title = "Edit Crop", modifier = modifier.fillMaxSize(), onBack = onClose,
            actions = { topActions(false) },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        EditorState.Failed -> ScreenScaffold(
            title = "Edit Crop", modifier = modifier.fillMaxSize(), onBack = onClose,
            actions = { topActions(false) },
        ) { innerPadding ->
            Column(
                Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "File rusak, tidak bisa dibuka",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Foto halaman ${photo.number} tidak bisa dibaca sehingga tidak bisa diatur crop-nya. " +
                        "Gunakan Foto ulang untuk mengambilnya lagi, atau Hapus untuk mengosongkan nomor ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onClose,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Kembali") }
            }
        }
        is EditorState.Ready -> EditorBody(number, session, photo, s.data, batchViewModel, onClose, topActions, modifier)
    }
}

@Composable
private fun EditorBody(
    number: Int,
    session: com.blitar.scanfast.model.CaptureSession,
    photo: CapturedPhoto,
    data: EditorData,
    batchViewModel: BatchViewModel,
    onClose: () -> Unit,
    topActions: @Composable RowScope.(Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val minSidePx = with(LocalDensity.current) { MIN_SIDE_DP.dp.toPx() }
    val colors = MaterialTheme.colorScheme
    val pts = remember(data) { mutableStateListOf<NormPoint>().apply { addAll(data.initial.toList()) } }
    var activeIdx by remember { mutableIntStateOf(-1) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }

    val fit by rememberUpdatedState(fitRect(boxSize, data.image.width, data.image.height))
    BackHandler(enabled = saving) { /* tunggu simpan selesai */ }

    ScreenScaffold(
        title = "Edit Crop",
        modifier = modifier.fillMaxSize(),
        onBack = if (saving) null else ({ haptics.tick(); onClose() }),
        actions = { topActions(saving) },
    ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding)) {
        Text(
            "Halaman $number — seret sudut untuk mengatur crop",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { boxSize = it }
                .pointerInput(data) {
                    val slop = 56.dp.toPx()
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (!saving) {
                                activeIdx = nearestHandle(pts, fit, pos, slop)
                                if (activeIdx >= 0) haptics.gestureStart()
                            }
                        },
                        onDrag = { change, drag ->
                            val i = activeIdx
                            if (i >= 0 && fit.width > 0f && fit.height > 0f) {
                                change.consume()
                                val p = pts[i]
                                pts[i] = NormPoint(
                                    (p.x + drag.x / fit.width).coerceIn(0f, 1f),
                                    (p.y + drag.y / fit.height).coerceIn(0f, 1f),
                                )
                            }
                        },
                        onDragEnd = {
                            if (activeIdx >= 0) haptics.gestureEnd()
                            activeIdx = -1
                        },
                        onDragCancel = { activeIdx = -1 },
                    )
                },
        ) {
            Image(
                bitmap = data.image,
                contentDescription = "Foto original halaman $number",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) { drawQuad(pts, fit, activeIdx, colors.primary, colors.tertiary, colors.scrim) }
        }
        // Dua baris: aksi sekunder (Batal, Reset) berdampingan, aksi utama (Simpan) full-width di bawah.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            OutlinedButton(
                onClick = { haptics.tick(); onClose() },
                enabled = !saving,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Batal", maxLines = 1) }
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    pts.clear()
                    pts.addAll(data.auto.toList())
                },
                enabled = !saving,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1.6f).height(52.dp),
            ) { Text("Reset ke otomatis", maxLines = 1) }
            }
            Button(
                enabled = !saving,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    if (!isConvex(pts, data.image.width, data.image.height)) {
                        haptics.reject()
                        Toast.makeText(context, "Titik tidak membentuk segi empat", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (hasShortSide(pts, fit, minSidePx)) {
                        haptics.reject()
                        Toast.makeText(
                            context, "Area terlalu kecil, geser titik lebih lebar", Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    // Sama persis dengan quad otomatis → simpan sebagai otomatis (bendera tetap).
                    val quad = if (pts.toList() == data.auto.toList()) data.auto
                    else QuadPoints(pts[0], pts[1], pts[2], pts[3], autoDetected = false, manuallyEdited = true)
                    saving = true
                    scope.launch {
                        val error: String? = try {
                            batchViewModel.reprocess(session, photo, quad)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IOException) {
                            Log.e("CropEditor", "Simpan gagal (I/O)", e)
                            e.message ?: "kesalahan I/O"
                        } catch (e: RuntimeException) {
                            Log.e("CropEditor", "Simpan gagal (runtime)", e)
                            e.message ?: "kesalahan pemrosesan"
                        } catch (e: OutOfMemoryError) {
                            Log.e("CropEditor", "Simpan gagal (memori habis)", e)
                            "memori habis"
                        }
                        if (error == null) {
                            haptics.confirm()
                            onClose()
                        } else {
                            haptics.reject()
                            Toast.makeText(context, "Gagal menyimpan: $error", Toast.LENGTH_LONG).show()
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Menyimpan…" else "Simpan", style = MaterialTheme.typography.titleMedium, maxLines = 1) }
        }
    }
    }
}

private fun QuadPoints.toList() = listOf(topLeft, topRight, bottomRight, bottomLeft)

/** Area gambar di dalam container untuk ContentScale.Fit (rata tengah). */
private fun fitRect(box: IntSize, imgW: Int, imgH: Int): Rect {
    if (box.width == 0 || box.height == 0 || imgW == 0 || imgH == 0) return Rect.Zero
    val scale = min(box.width.toFloat() / imgW, box.height.toFloat() / imgH)
    val w = imgW * scale
    val h = imgH * scale
    return Rect((box.width - w) / 2f, (box.height - h) / 2f, (box.width + w) / 2f, (box.height + h) / 2f)
}

private fun toScreen(p: NormPoint, fit: Rect) = Offset(fit.left + p.x * fit.width, fit.top + p.y * fit.height)

private fun nearestHandle(pts: List<NormPoint>, fit: Rect, pos: Offset, slop: Float): Int {
    var best = -1
    var bestDist = slop
    pts.forEachIndexed { i, p ->
        val d = (toScreen(p, fit) - pos).getDistance()
        if (d <= bestDist) { best = i; bestDist = d }
    }
    return best
}

private fun DrawScope.drawQuad(
    pts: List<NormPoint>, fit: Rect, active: Int, primary: Color, activeColor: Color, scrim: Color,
) {
    if (fit.width <= 0f) return
    val s = pts.map { toScreen(it, fit) }
    val path = Path().apply {
        moveTo(s[0].x, s[0].y)
        for (i in 1 until s.size) lineTo(s[i].x, s[i].y)
        close()
    }
    drawPath(path, primary.copy(alpha = 0.18f))
    drawPath(path, primary, style = Stroke(width = 2.dp.toPx()))
    s.forEachIndexed { i, o ->
        drawCircle(scrim.copy(alpha = 0.5f), radius = 16.dp.toPx(), center = o)
        drawCircle(
            if (i == active) activeColor else primary,
            radius = if (i == active) 13.dp.toPx() else 10.dp.toPx(),
            center = o,
        )
    }
}

/** True kalau ada sisi quad (titik bersebelahan) lebih pendek dari [minPx] di koordinat layar tampilan. */
private fun hasShortSide(pts: List<NormPoint>, fit: Rect, minPx: Float): Boolean {
    if (fit.width <= 0f || fit.height <= 0f) return false
    return (0 until 4).any { i ->
        (toScreen(pts[i], fit) - toScreen(pts[(i + 1) % 4], fit)).getDistance() < minPx
    }
}

/** Segi empat konveks (tanda cross product semua sisi sama) pada koordinat piksel gambar. */
private fun isConvex(pts: List<NormPoint>, imgW: Int, imgH: Int): Boolean {
    val xy = pts.map { Offset(it.x * imgW, it.y * imgH) }
    var pos = 0
    var neg = 0
    for (i in 0 until 4) {
        val a = xy[i]
        val b = xy[(i + 1) % 4]
        val c = xy[(i + 2) % 4]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        if (cross > 1f) pos++ else if (cross < -1f) neg++ else return false
    }
    return pos == 4 || neg == 4
}

/** Decode original (downscale untuk tampilan) + quad awal/otomatis. Null kalau file rusak. Jalankan di IO. */
private fun loadEditorData(photo: CapturedPhoto): EditorData? {
    val bmp: Bitmap = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DISPLAY_LONG_SIDE) sample *= 2
        BitmapFactory.decodeFile(photo.filePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
    } catch (e: OutOfMemoryError) {
        Log.e("CropEditor", "Memori habis membuka foto ${photo.number}", e)
        return null
    } catch (e: IllegalArgumentException) {
        Log.e("CropEditor", "Foto ${photo.number} tidak valid", e)
        return null
    }
    val auto = photo.autoQuad ?: ImageCropper.autoQuad(bmp)
    val initial = photo.quad ?: auto
    return EditorData(bmp.asImageBitmap(), initial, auto)
}
