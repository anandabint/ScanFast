package com.blitar.scanfast.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blitar.scanfast.camera.CameraXController
import com.blitar.scanfast.camera.VolumeShutterHandler
import com.blitar.scanfast.ui.components.CameraPreviewLayer
import com.blitar.scanfast.ui.components.TorchToggleButton
import com.blitar.scanfast.ui.components.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Layar capture satu foto (dipakai Foto ulang dan Tambah foto): preview + guide-frame + tap-to-focus +
 * torch + shutter (tombol atau volume) + Batal, tanpa counter/skip/Run. Setelah capture, [process]
 * dijalankan (melempar exception kalau gagal → toast, kamera siap lagi); sukses → [onDone].
 */
@Composable
fun SingleShotScreen(
    title: String,
    errorPrefix: String,
    nextFileName: () -> String,
    process: suspend (path: String) -> Unit,
    volumeShutter: VolumeShutterHandler,
    onClose: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember { CameraXController(context) }
    DisposableEffect(controller) { onDispose { controller.release() } }

    var busy by remember { mutableStateOf(false) }
    val doCapture by rememberUpdatedState {
        if (hasPermission && !busy) {
            busy = true
            controller.capture(
                fileName = nextFileName(),
                onSuccess = { path ->
                    scope.launch {
                        val error: String? = try {
                            process(path)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IOException) {
                            Log.e("SingleShotScreen", "$errorPrefix gagal (I/O)", e)
                            e.message ?: "kesalahan I/O"
                        } catch (e: RuntimeException) {
                            Log.e("SingleShotScreen", "$errorPrefix gagal (runtime)", e)
                            e.message ?: "kesalahan pemrosesan"
                        } catch (e: OutOfMemoryError) {
                            Log.e("SingleShotScreen", "$errorPrefix gagal (memori habis)", e)
                            "memori habis"
                        }
                        if (error == null) {
                            haptics.confirm()
                            onDone()
                        } else {
                            haptics.reject()
                            Toast.makeText(context, "$errorPrefix gagal: $error", Toast.LENGTH_LONG).show()
                            busy = false
                        }
                    }
                },
                onError = {
                    busy = false
                    Toast.makeText(context, "Capture gagal: ${it.message}", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
    BackHandler(enabled = busy) { /* tunggu proses selesai */ }
    DisposableEffect(volumeShutter) {
        val listener: () -> Unit = { doCapture() }
        volumeShutter.register(listener)
        onDispose { volumeShutter.unregister(listener) }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreviewLayer(controller)
            TorchToggleButton(
                torchOn = controller.torchOn,
                onToggle = {
                    haptics.tick()
                    controller.toggleTorch()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(top = 24.dp, end = 16.dp),
            )
        } else {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Izin kamera diperlukan", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Beri izin")
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 24.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    onClose()
                },
                enabled = !busy,
            ) { Text("Batal") }
            Button(
                onClick = { doCapture() },
                enabled = hasPermission && !busy,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {}
            Spacer(Modifier.width(72.dp)) // penyeimbang agar shutter tepat di tengah
        }

        if (busy) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        "Memproses…",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}
